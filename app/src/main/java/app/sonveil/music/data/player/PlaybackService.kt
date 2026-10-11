package app.sonveil.music.data.player

import android.animation.ValueAnimator
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.sonveil.music.data.player.auto.AutoClientGate
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import app.sonveil.music.SonveilApp
import app.sonveil.music.MainActivity
import app.sonveil.music.R
import app.sonveil.music.data.player.auto.AutoLibraryCallback
import com.google.common.collect.ImmutableList
import kotlin.math.abs
import java.util.IdentityHashMap

/**
 * Playback service with a **sticky lifetime audio session** for Poweramp EQ DVC.
 *
 * Option A (maxmpz guidance): one [AudioManager.generateAudioSessionId] for the
 * whole service life; every ExoPlayer uses that id; OPEN once before audible
 * output; CLOSE only on [onDestroy].
 *
 * Option B: when [ExternalEqRisk] says dual-player is unsafe (Poweramp EQ
 * installed, etc.), **do not** run dual-ExoPlayer crossfade — single player owns
 * the sticky session (gapless / Media3 default transitions). Removes the second
 * AudioTrack / promote-teardown unbound window class.
 *
 * A decoded PCM gate holds silence through cold AudioTrack binding and recovery,
 * then ramps samples back in. A silent track holds the external EQ session
 * across normal pauses and item changes, avoiding repeated DVC rebinding.
 *
 * Media3 [DefaultAudioSink.flush] releases the AudioTrack on a seek.
 * [DvcGuardedPlayer] closes the gate before seeks if the anchor is unavailable.
 * A warm anchor keeps queued audio intact for natural resume and transitions.
 */
@UnstableApi
class PlaybackService : MediaLibraryService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var player: ExoPlayer? = null
    private var fadePlayer: ExoPlayer? = null
    private lateinit var playbackCache: PlaybackCache
    private val pcmGates = IdentityHashMap<ExoPlayer, DvcPcmGate>()
    private val pcmEqs = IdentityHashMap<ExoPlayer, GraphicEqProcessor>()
    private val pcmGains = IdentityHashMap<ExoPlayer, ReplayGainProcessor>()
    private var session: MediaLibraryService.MediaLibrarySession? = null
    private var libraryCallback: AutoLibraryCallback? = null
    private val eqMain = EqController()
    private val eqFade = EqController()
    private var rgLinear = 1f
    private lateinit var settings: PlayerSettings
    private val handler = Handler(Looper.getMainLooper())
    private var fadeAnim: ValueAnimator? = null
    private var volumeAnim: ValueAnimator? = null
    private var fading = false
    private var pendingReady = false
    private var pendingNextIndex = C.INDEX_UNSET
    private var pendingNextLinear = 1f
    private var lastNotifiedSessionId = 0

    /** Service-lifetime session; 0 only if [AudioManager.generateAudioSessionId] failed. */
    private var stickySessionId = 0
    private var dvcAnchor: DvcSessionAnchor? = null
    private val stopDvcAnchor = Runnable { onDvcAnchorGraceExpired() }

    /** True while muted waiting for READY + OPEN settle (any media transition). */
    private var sessionBindPending = false
    /**
     * True after user pause/stop under DVC guard: volume stays 0 and settle must
     * **not** unmute until a later play/resume. Distinct from [sessionBindPending]
     * so a READY-while-paused settle cannot blast on the next AudioTrack.
     */
    private var muteHeldForPause = false
    private var settleRunnable: Runnable? = null
    private var awaitAudioTrackInitialization = false
    private var guardedSeekAtElapsed = 0L
    private var pauseGeneration = 0L

    /** Auto-retry after transient IO / network [onPlayerError] (does not rebuild player / sticky id). */
    private var errorRetryAttempt = 0
    private var errorRetryRunnable: Runnable? = null
    private var resumeAfterError = false

    /** ElapsedRealtime when fade player became READY; 0 if not yet. */
    private var pendingReadyAtElapsed = 0L

    /**
     * Cached Option B gate. Re-evaluated periodically / when CF prefs change.
     * true ⇒ dual-ExoPlayer crossfade must not run.
     */
    private var dualPlayerCfBlocked = false
    private var dualPlayerCfCheckedAtElapsed = 0L

    private val sleepFire = Runnable { onSleepTimerFired() }
    private val sleepState = SleepTimerState()

    private val tick = object : Runnable {
        override fun run() {
            updateAnchoredSinkPolicy()
            maybeStartCrossfade()
            player?.let { updateSongPrefetch(it) }
            handler.postDelayed(this, 200)
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = PlayerSettings(this)
        settings.register(this)
        refreshDualPlayerCfGate(force = true)

        // 1) Allocate sticky session at service start — NOT deferred to first prepare.
        stickySessionId = generateStickySessionId()

        val client = (application as SonveilApp).container.client
        playbackCache = PlaybackCache(this, client, OkHttpDataSource.Factory(client.http)
            .setUserAgent("Sonveil/${app.sonveil.music.BuildConfig.VERSION_NAME}"))

        val exo = buildPlayer()
        player = exo
        applyGapless(exo)
        applyDisconnectPolicy(exo)

        // 2) Attach platform EQ/RG on sticky id, then OPEN before any audible output.
        // Cold start stays muted until settle completes (same guard as unexpected rebind).
        attachEq(exo, eqMain, openExternal = true, muteUntilBound = stickySessionId != 0)
        if (dualPlayerCfBlocked && stickySessionId != 0) {
            dvcAnchor = DvcSessionAnchor(stickySessionId).also { it.start() }
        }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val extras = Bundle().apply {
            putString("com.android.music.musicsource", "Sonveil")
            putString("app_name", "Sonveil")
        }
        val app = application as SonveilApp
        val callback = AutoLibraryCallback(app.container, app.container.player, packageName) { pkg -> AutoClientGate.lookup(this, pkg) }
        libraryCallback = callback
        val guarded = guardedPlayer(exo)
        session = MediaLibraryService.MediaLibrarySession.Builder(this, guarded, callback)
            .setId("app.sonveil.music.session")
            .setSessionActivity(openApp)
            .setExtras(extras)
            .setBitmapLoader(mediaArtworkBitmapLoader(this))
            .build()
        installPlayerListeners(exo)
        // applyReplayGain runs after settle when muteUntilBound; otherwise now.
        if (!sessionBindPending) {
            applyReplayGain(exo, exo.currentMediaItem, eqMain)
        }
        setMediaNotificationProvider(SonveilNotificationProvider(this))
        handler.post(tick)
        armSleepTimerFromSettings()
    }

    private fun generateStickySessionId(): Int {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val id = am.generateAudioSessionId()
        if (id == AudioManager.ERROR || id <= 0) {
            Log.w(TAG, "generateAudioSessionId failed: $id — falling back to ExoPlayer auto session")
            return 0
        }
        Log.i(TAG, "stickySessionId=$id (service lifetime)")
        return id
    }

    private fun installPlayerListeners(exo: ExoPlayer) {
        exo.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioTrackInitialized(
                eventTime: AnalyticsListener.EventTime,
                audioTrackConfig: AudioSink.AudioTrackConfig,
            ) {
                if (player !== exo) return
                if (pcmGates[exo]?.isClosed == true && !sessionBindPending) {
                    beginMuteUntilBound(exo, eqMain, reason = "pcm-gate-new-audiotrack")
                }
                if (!sessionBindPending) return
                if (eventTime.realtimeMs < guardedSeekAtElapsed) return
                awaitAudioTrackInitialization = false
                armSettleAfterReady(exo)
            }
        })
        exo.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (this@PlaybackService.player !== exo) return
                if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
                    updateSongPrefetch(exo)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (player !== exo) return
                // Intentional track change: drop pending network retries for the prior item.
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    resetErrorRetry(clearResume = true)
                }
                // Skip/album/auto: mute before any RG→volume=1f. CF promote uses
                // finishCrossfade (no transition on the promoted player).
                if (fading) return
                // Prime the new track before either the anchored path or a bind wait
                // can buffer PCM. The DVC path must not queue the previous track's gain.
                rgLinear = ReplayGainProcessor.fromExtras(mediaItem?.mediaMetadata?.extras, settings.replayGainMode, settings.peakLimiter)
                setPcmGain(exo, rgLinear)
                // Without an external EQ there is no session to rebind. Muting here
                // fades in every track and breaks gapless playback.
                if (!dualPlayerCfBlocked || canContinueThroughAnchoredTransition(exo)) {
                    applyReplayGain(exo, mediaItem, eqMain)
                    return
                }
                if (muteHeldForPause || !exo.playWhenReady) {
                    hardMuteNoSettle(exo, eqMain, reason = transitionMuteReason(reason))
                } else {
                    beginMuteUntilBound(exo, eqMain, reason = transitionMuteReason(reason))
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (player !== exo) return
                onPrimarySessionIdChanged(exo, audioSessionId)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (player !== exo) return
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    // Belt: seeks that bypass DvcGuardedPlayer (internal / retry).
                    // Only an external EQ needs the mute. A scrub must not clear the sleep timer.
                    if (dualPlayerCfBlocked && !sessionBindPending && !muteHeldForPause &&
                        !canContinueThroughAnchoredTransition(exo)
                    ) {
                        beginMuteUntilBound(exo, eqMain, reason = "discontinuity-seek")
                    }
                    if (!fading) cancelCrossfade(restoreVolume = false)
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (player !== exo) return
                if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
                // Upcoming stream-URL refreshes are playlist changes too. They must not
                // mute, abort a crossfade, or clear the sleep timer.
                if (fading || !dualPlayerCfBlocked) {
                    resetErrorRetry(clearResume = true)
                    return
                }
                if (canContinueThroughAnchoredTransition(exo)) {
                    applyReplayGain(exo, exo.currentMediaItem, eqMain)
                } else if (muteHeldForPause || !exo.playWhenReady) {
                    hardMuteNoSettle(exo, eqMain, reason = "playlist-changed")
                } else {
                    beginMuteUntilBound(exo, eqMain, reason = "playlist-changed")
                }
                cancelCrossfade(restoreVolume = false)
                resetErrorRetry(clearResume = true)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player !== exo) return
                if (playbackState == Player.STATE_READY && sessionBindPending) {
                    armSettleAfterReady(exo)
                }
                if (playbackState == Player.STATE_READY) {
                    resetErrorRetry(clearResume = true)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (player !== exo) return
                beginMuteUntilBound(exo, eqMain, reason = "player-error")
                cancelCrossfade(restoreVolume = false)
                handlePrimaryPlayerError(exo, error)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (player !== exo) return
                if (!playWhenReady) {
                    // Crossfade pauses the outgoing player at the item boundary on purpose.
                    // Treating that as a user pause releases the incoming player.
                    val crossfadeBoundary = fading &&
                        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
                    if (!crossfadeBoundary && !muteHeldForPause) {
                        onGuardedPause(exo, "listener-pause:$reason")
                    } else if (!crossfadeBoundary) {
                        cancelCrossfade(restoreVolume = false)
                    }
                    if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                        resetErrorRetry(clearResume = true)
                    }
                } else {
                    // Focus regain / play may bypass guard — arm settle before audible.
                    if (muteHeldForPause || sessionBindPending || exo.volume < 0.5f) {
                        onGuardedPlayOrResume(exo, "listener-resume:$reason")
                    } else if (stickySessionId != 0 &&
                        exo.audioSessionId != stickySessionId
                    ) {
                        beginMuteUntilBound(exo, eqMain, reason = "resume-mismatch")
                    }
                }
                val endOfTrackSleep = sleepState.endOfTrack
                when (sleepState.onPlayWhenReadyChanged(playWhenReady, reason)) {
                    SleepTimerState.Action.Fire -> onSleepTimerFired()
                    SleepTimerState.Action.Cancel -> cancelSleepFromUser()
                    SleepTimerState.Action.None -> Unit
                }
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                    !endOfTrackSleep && dualPlayerCfBlocked
                ) {
                    val generation = pauseGeneration
                    val endedIndex = exo.currentMediaItemIndex
                    handler.post {
                        if (player !== exo || pauseGeneration != generation || exo.playWhenReady ||
                            sleepState.endOfTrack
                        ) return@post
                        if (exo.currentMediaItemIndex == endedIndex) {
                            when {
                                exo.repeatMode == Player.REPEAT_MODE_ONE -> {
                                    onGuardedSeek(exo, "auto-repeat")
                                    exo.seekTo(endedIndex, 0L)
                                }
                                exo.hasNextMediaItem() -> {
                                    onGuardedSeek(exo, "auto-next")
                                    exo.seekToNextMediaItem()
                                }
                                else -> return@post
                            }
                        }
                        onGuardedPlayOrResume(exo, "auto-next")
                        exo.play()
                    }
                }
            }
        })
    }

    private fun updateSongPrefetch(exo: ExoPlayer) {
        if (!exo.playWhenReady || exo.playbackState != Player.STATE_READY || exo.mediaItemCount == 0) {
            playbackCache.prefetch(emptyList())
            return
        }
        val fullyBuffered = exo.duration != C.TIME_UNSET && exo.bufferedPosition >= exo.duration
        if (!fullyBuffered && exo.bufferedPosition - exo.currentPosition < 30_000L) {
            // Let the audible stream build a cushion before sharing mobile bandwidth.
            playbackCache.prefetch(emptyList())
            return
        }
        val timeline = exo.currentTimeline
        val uris = mutableListOf<android.net.Uri>()
        var index = exo.currentMediaItemIndex
        for (upcoming in 0 until 2) {
            index = timeline.getNextWindowIndex(index, exo.repeatMode, exo.shuffleModeEnabled)
            if (index == C.INDEX_UNSET || index == exo.currentMediaItemIndex) break
            exo.getMediaItemAt(index).localConfiguration?.uri?.let { uris.add(it) }
        }
        playbackCache.prefetch(uris)
    }

    private fun guardedPlayer(exo: ExoPlayer): DvcGuardedPlayer =
        DvcGuardedPlayer(exo, object : DvcGuardedPlayer.Guard {
            override fun beforeSeek(reason: String) = onGuardedSeek(exo, reason)
            override fun beforePause(reason: String) = onGuardedPause(exo, reason)
            override fun beforePlayOrResume(reason: String) = onGuardedPlayOrResume(exo, reason)
        })

    private fun transitionMuteReason(reason: Int): String = when (reason) {
        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "transition-seek"
        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "transition-auto"
        Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "transition-playlist"
        Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "transition-repeat"
        else -> "transition-$reason"
    }

    /**
     * Transient network / HTTP errors: prepare again with exponential backoff.
     * Does **not** rebuild ExoPlayer or touch sticky [audioSessionId] / EQ OPEN/CLOSE.
     */
    private fun handlePrimaryPlayerError(exo: ExoPlayer, error: PlaybackException) {
        val wantPlay = exo.playWhenReady || resumeAfterError
        resumeAfterError = wantPlay
        if (!PlaybackErrors.isTransient(error) || errorRetryAttempt >= MAX_ERROR_RETRIES) {
            Log.w(
                TAG,
                "playback error (no auto-retry attempt=$errorRetryAttempt): ${error.errorCodeName}",
                error,
            )
            return
        }
        val attempt = errorRetryAttempt
        errorRetryAttempt = attempt + 1
        val delayMs = ERROR_RETRY_BASE_MS * (1L shl attempt.coerceAtMost(3))
        Log.i(
            TAG,
            "transient playback error ${error.errorCodeName}; auto-retry ${attempt + 1}/$MAX_ERROR_RETRIES in ${delayMs}ms",
        )
        scheduleErrorRetry(exo, delayMs, wantPlay)
    }

    private fun scheduleErrorRetry(exo: ExoPlayer, delayMs: Long, play: Boolean) {
        cancelErrorRetryCallback()
        val r = Runnable {
            errorRetryRunnable = null
            if (player !== exo) return@Runnable
            if (exo.mediaItemCount == 0) return@Runnable
            val idx = exo.currentMediaItemIndex.coerceAtLeast(0)
            val pos = exo.currentPosition.coerceAtLeast(0L)
            Log.i(TAG, "auto-retry prepare idx=$idx pos=$pos play=$play")
            runCatching {
                // Each upstream open resolves fresh authentication. Replacing the item
                // here resets the retry budget through playlist/transition callbacks.
                exo.prepare()
                if (play) {
                    muteHeldForPause = false
                    if (!sessionBindPending) {
                        beginMuteUntilBound(exo, eqMain, reason = "error-retry-play")
                    }
                    exo.play()
                }
            }.onFailure { Log.w(TAG, "auto-retry prepare failed", it) }
        }
        errorRetryRunnable = r
        handler.postDelayed(r, delayMs)
    }

    private fun cancelErrorRetryCallback() {
        errorRetryRunnable?.let { handler.removeCallbacks(it) }
        errorRetryRunnable = null
    }

    private fun resetErrorRetry(clearResume: Boolean) {
        cancelErrorRetryCallback()
        errorRetryAttempt = 0
        if (clearResume) resumeAfterError = false
    }

    /**
     * Sticky path: session should never change. If ExoPlayer forces a new id
     * (format recreate / AudioTrack rebuild), mute → re-assert sticky → OPEN → settle → ramp.
     */
    private fun onPrimarySessionIdChanged(exo: ExoPlayer, audioSessionId: Int) {
        if (stickySessionId != 0 && audioSessionId == stickySessionId) {
            // Confirm sticky; keep EQ attached; do NOT CLOSE/OPEN.
            if (!dualPlayerCfBlocked && eqMain.audioSessionId != stickySessionId) {
                eqMain.attach(stickySessionId, settings)
            }
            if (lastNotifiedSessionId != stickySessionId) {
                notifyExternalEqSession(stickySessionId, open = true)
            }
            return
        }
        if (stickySessionId != 0 && audioSessionId != 0 && audioSessionId != stickySessionId) {
            Log.w(
                TAG,
                "unexpected session change: got=$audioSessionId sticky=$stickySessionId — mute/rebind safety net",
            )
            beginMuteUntilBound(exo, eqMain, reason = "session-changed")
            return
        }
        // No sticky (generation failed): legacy attach with OPEN-before-CLOSE.
        attachEq(exo, eqMain, openExternal = true, muteUntilBound = true)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibraryService.MediaLibrarySession? = session

    override fun onSharedPreferenceChanged(sharedPreferences: android.content.SharedPreferences?, key: String?) {
        val exo = player ?: return
        when (key) {
            PlayerSettings.EQ_ON, PlayerSettings.EQ_GAINS, PlayerSettings.EQ_PRESET,
            PlayerSettings.EQ_MODE, PlayerSettings.EQ_PREAMP_GRAPHIC, PlayerSettings.EQ_PREAMP_PARAMETRIC,
            PlayerSettings.EQ_FILTERS,
            -> {
                eqMain.apply(settings)
                eqFade.apply(settings)
                pushPcmEq()
            }
            PlayerSettings.RG_MODE, PlayerSettings.RG_LIMIT -> applyReplayGain(exo, exo.currentMediaItem, eqMain)
            PlayerSettings.GAPLESS, PlayerSettings.CROSSFADE -> {
                refreshDualPlayerCfGate(force = true)
                if (!settings.gapless || !dualPlayerCrossfadeAllowed()) cancelCrossfade()
                applyGapless(exo)
            }
            PlayerSettings.PAUSE_DISC -> applyDisconnectPolicy(exo)
            PlayerSettings.SLEEP_MINUTES,
            PlayerSettings.SLEEP_DEADLINE,
            PlayerSettings.SLEEP_DEADLINE_WALL,
            PlayerSettings.SLEEP_DEADLINE_BOOT_COUNT,
            -> armSleepTimerFromSettings()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(sleepFire)
        handler.removeCallbacks(stopDvcAnchor)
        dvcAnchor?.stop()
        dvcAnchor = null
        settleRunnable?.let { handler.removeCallbacks(it) }
        settleRunnable = null
        resetErrorRetry(clearResume = true)
        cancelCrossfade()
        volumeAnim?.cancel()
        volumeAnim = null
        settings.unregister(this)
        libraryCallback?.release()
        libraryCallback = null
        // CLOSE only on service destroy — never on skip / promote / track change.
        if (lastNotifiedSessionId != 0) {
            notifyExternalEqSession(lastNotifiedSessionId, open = false)
        } else if (stickySessionId != 0) {
            notifyExternalEqSession(stickySessionId, open = false)
        }
        session?.run {
            player.release()
            release()
        }
        fadePlayer?.release()
        if (::playbackCache.isInitialized) playbackCache.release()
        pcmGates.clear()
        pcmEqs.clear()
        pcmGains.clear()
        eqMain.release()
        eqFade.release()
        session = null
        player = null
        fadePlayer = null
        stickySessionId = 0
        super.onDestroy()
    }

    /**
     * Build ExoPlayer and **always** [ExoPlayer.setAudioSessionId] sticky id before any
     * prepare / setMediaItems that create an AudioTrack.
     */
    private fun buildPlayer(handleAudioFocus: Boolean = true): ExoPlayer {
        val gate = DvcPcmGate { flushed -> handler.post { onPcmGateFlushed(flushed) } }.apply {
            if (dualPlayerCfBlocked) enable() else disable()
        }
        val gain = ReplayGainProcessor()
        val tone = GraphicEqProcessor().apply { setProgram(EqProgram.from(settings)) }
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(gain, tone, gate))
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }
        val exo = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(playbackCache.dataSourceFactory),
            )
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(30_000, 120_000, 1_000, 3_000)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build(),
            )
            .setAudioAttributes(
                mediaAudioAttributes(),
                handleAudioFocus,
            )
            .setHandleAudioBecomingNoisy(settings.pauseOnDisconnect)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        pcmGates[exo] = gate
        pcmEqs[exo] = tone
        pcmGains[exo] = gain
        if (stickySessionId != 0) {
            exo.setAudioSessionId(stickySessionId)
            Log.d(TAG, "setAudioSessionId($stickySessionId) on new ExoPlayer")
        }
        return exo
    }

    private fun pushPcmEq() {
        val program = EqProgram.from(settings)
        pcmEqs.values.forEach { it.setProgram(program) }
    }

    private fun onPcmGateFlushed(gate: DvcPcmGate) {
        val exo = player ?: return
        if (pcmGates[exo] !== gate || !gate.isClosed || sessionBindPending) return
        // A sink flush can bypass controller and player callbacks. Keep the PCM
        // gate shut and re-arm settling even when no user seek was observed.
        beginMuteUntilBound(exo, eqMain, reason = "pcm-gate-flush")
    }

    private fun mediaAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

    private fun updateAnchoredSinkPolicy() {
        val exo = player ?: return
        val gate = pcmGates[exo] ?: return
        val keepOpen = dualPlayerCfBlocked && dvcAnchor?.isWarm == true &&
            !gate.isClosed && !sessionBindPending
        if (gate.keepOpenOnFlush == keepOpen) return
        gate.setKeepOpenOnFlush(keepOpen)
        applyGapless(exo)
    }

    private fun canContinueThroughAnchoredTransition(exo: ExoPlayer): Boolean =
        player === exo && exo.playWhenReady && !muteHeldForPause &&
            !sessionBindPending && pcmGates[exo]?.keepOpenOnFlush == true

    private fun applyGapless(exo: ExoPlayer) {
        // A warm anchor keeps DVC bound while Media3 advances the queue. If the
        // anchor is unavailable, pause at end and advance through the PCM gate.
        exo.pauseAtEndOfMediaItems = sleepState.endOfTrack ||
            (fading && player === exo) ||
            (dualPlayerCfBlocked && player === exo && pcmGates[exo]?.keepOpenOnFlush != true)
        exo.skipSilenceEnabled = false
    }

    private fun applyDisconnectPolicy(exo: ExoPlayer) {
        exo.setHandleAudioBecomingNoisy(settings.pauseOnDisconnect)
        fadePlayer?.setHandleAudioBecomingNoisy(settings.pauseOnDisconnect)
    }

    /**
     * Attach built-in EQ/RG (DynamicsProcessing) to the player's session.
     * With sticky id: never CLOSE here — only OPEN once via [notifyExternalEqSession].
     * [muteUntilBound]: keep volume at 0 until READY+settle (cold start / rebind).
     */
    private fun attachEq(
        exo: ExoPlayer,
        eq: EqController,
        openExternal: Boolean,
        muteUntilBound: Boolean,
    ) {
        val sid = when {
            stickySessionId != 0 -> {
                if (exo.audioSessionId != stickySessionId) {
                    runCatching { exo.setAudioSessionId(stickySessionId) }
                }
                stickySessionId
            }
            else -> exo.audioSessionId
        }
        if (sid == 0) {
            if (!dualPlayerCfBlocked) eq.attach(exo.audioSessionId, settings)
            return
        }

        if (dualPlayerCfBlocked) {
            eq.release()
            if (eq === eqMain && openExternal) notifyExternalEqSession(sid, open = true)
            if (muteUntilBound && eq === eqMain) {
                beginMuteUntilBound(exo, eq, reason = "cold-or-rebind")
            }
            return
        }

        val prev = eq.audioSessionId
        // Sticky path: do not CLOSE on attach / track change.
        if (stickySessionId == 0 && prev != 0 && prev != sid && eq === eqMain && openExternal) {
            // Legacy fallback only: OPEN before CLOSE (no unbound advertise window).
            eq.attach(sid, settings)
            if (openExternal) {
                notifyExternalEqSession(sid, open = true)
                notifyExternalEqSession(prev, open = false)
            }
            if (muteUntilBound) beginMuteUntilBound(exo, eq, reason = "legacy-attach")
            return
        }

        eq.attach(sid, settings)
        if (eq === eqMain && openExternal) {
            notifyExternalEqSession(sid, open = true)
        }
        if (muteUntilBound && eq === eqMain) {
            beginMuteUntilBound(exo, eq, reason = "cold-or-rebind")
        }
    }


    /**
     * Called from [DvcGuardedPlayer] before seek is forwarded to ExoPlayer.
     * Close the PCM gate before the playback thread flushes its AudioTrack.
     */
    private fun onGuardedSeek(exo: ExoPlayer, reason: String) {
        if (canContinueThroughAnchoredTransition(exo)) {
            cancelCrossfade(restoreVolume = false)
            Log.i(TAG, "anchor-held transition ($reason) sticky=$stickySessionId")
            return
        }
        if (!dualPlayerCfBlocked) {
            cancelCrossfade(restoreVolume = false)
            return
        }
        pcmGates[exo]?.close()
        awaitAudioTrackInitialization = true
        guardedSeekAtElapsed = SystemClock.elapsedRealtime()
        if (muteHeldForPause) {
            // Already hard-muted while paused; keep hold, refresh OPEN, no settle.
            hardMuteNoSettle(exo, eqMain, reason = "seek-while-paused:$reason")
            cancelCrossfade(restoreVolume = false)
            return
        }
        beginMuteUntilBound(exo, eqMain, reason = "guard-seek:$reason")
        cancelCrossfade(restoreVolume = false)
    }

    /** Keep a warm anchored pause seamless; otherwise hold mute for a guarded resume. */
    private fun onGuardedPause(exo: ExoPlayer, reason: String) {
        if (dualPlayerCfBlocked) {
            handler.removeCallbacks(stopDvcAnchor)
            handler.postDelayed(stopDvcAnchor, DVC_ANCHOR_PAUSE_GRACE_MS)
        }
        Log.i(TAG, "mute-hold-for-pause ($reason) sticky=$stickySessionId")
        pauseGeneration++
        volumeAnim?.cancel()
        volumeAnim = null
        settleRunnable?.let { handler.removeCallbacks(it) }
        settleRunnable = null
        muteHeldForPause = true
        if (dualPlayerCfBlocked && dvcAnchor?.isWarm == true && !sessionBindPending) {
            // Keep buffered samples intact so a quick resume is seamless. The
            // continuously playing silent track holds the DVC session active.
            cancelCrossfade(restoreVolume = false)
            return
        }
        pcmGates[exo]?.close()
        sessionBindPending = true
        if (!dualPlayerCfBlocked) {
            exo.volume = 0f
            fadePlayer?.volume = 0f
        }
        if (stickySessionId != 0 && exo.audioSessionId != stickySessionId) {
            runCatching { exo.setAudioSessionId(stickySessionId) }
        }
        val sid = if (stickySessionId != 0) stickySessionId else exo.audioSessionId
        if (sid != 0) {
            if (!dualPlayerCfBlocked) eqMain.attach(sid, settings)
            notifyExternalEqSession(sid, open = true)
        }
        cancelCrossfade(restoreVolume = false)
    }

    /** Resume directly from a warm anchored pause, or settle after a guarded pause. */
    private fun onGuardedPlayOrResume(exo: ExoPlayer, reason: String) {
        handler.removeCallbacks(stopDvcAnchor)
        if (muteHeldForPause && !sessionBindPending && dualPlayerCfBlocked &&
            dvcAnchor?.isWarm == true
        ) {
            muteHeldForPause = false
            Log.i(TAG, "anchor-held resume without AudioTrack reset ($reason)")
            return
        }
        dvcAnchor?.start()
        if (muteHeldForPause || sessionBindPending || exo.volume < 0.5f) {
            muteHeldForPause = false
            beginMuteUntilBound(exo, eqMain, reason = "guard-resume:$reason")
        }
    }

    private fun onDvcAnchorGraceExpired() {
        val exo = player
        if (exo != null && !exo.playWhenReady && muteHeldForPause && dualPlayerCfBlocked) {
            // After a long pause, close the gate and mute the dormant track while
            // the silent anchor still holds the DVC session. Resume takes the
            // normal bind path, so releasing this last active track is inaudible.
            pcmGates[exo]?.close()
            pcmGates[exo]?.setKeepOpenOnFlush(false)
            exo.volume = 0f
            sessionBindPending = true
        }
        dvcAnchor?.stop()
    }

    /** Mute + OPEN without arming settle (paused seek / hold paths). */
    private fun hardMuteNoSettle(exo: ExoPlayer, eq: EqController, reason: String) {
        pcmGates[exo]?.close()
        Log.i(TAG, "hard-mute-no-settle ($reason) sticky=$stickySessionId")
        volumeAnim?.cancel()
        volumeAnim = null
        settleRunnable?.let { handler.removeCallbacks(it) }
        settleRunnable = null
        sessionBindPending = true
        if (!dualPlayerCfBlocked) {
            exo.volume = 0f
            fadePlayer?.volume = 0f
        }
        if (stickySessionId != 0 && exo.audioSessionId != stickySessionId) {
            runCatching { exo.setAudioSessionId(stickySessionId) }
        }
        val sid = if (stickySessionId != 0) stickySessionId else exo.audioSessionId
        if (sid != 0) {
            if (!dualPlayerCfBlocked) eq.attach(sid, settings)
            notifyExternalEqSession(sid, open = true)
        }
    }

    private fun beginMuteUntilBound(exo: ExoPlayer, eq: EqController, reason: String) {
        pcmGates[exo]?.close()
        Log.i(TAG, "mute-until-bound ($reason) sticky=$stickySessionId exoSid=${exo.audioSessionId}")
        volumeAnim?.cancel()
        volumeAnim = null
        // Seeking/transition while we intend to become audible — drop pause hold.
        muteHeldForPause = false
        sessionBindPending = true
        // AudioTrack gain stays steady under DVC; decoded PCM supplies the mute.
        if (!dualPlayerCfBlocked) {
            exo.volume = 0f
            fadePlayer?.volume = 0f
        }

        if (stickySessionId != 0 && exo.audioSessionId != stickySessionId) {
            runCatching { exo.setAudioSessionId(stickySessionId) }
        }
        val sid = if (stickySessionId != 0) stickySessionId else exo.audioSessionId
        if (sid != 0) {
            if (!dualPlayerCfBlocked) eq.attach(sid, settings)
            notifyExternalEqSession(sid, open = true)
        }

        // Do not arm settle until STATE_READY (AudioTrack exists). If already READY
        // (in-track seek / skip), settle after sessionSettleMs().
        settleRunnable?.let { handler.removeCallbacks(it) }
        settleRunnable = null
        if (exo.playbackState == Player.STATE_READY && exo.playWhenReady &&
            !awaitAudioTrackInitialization
        ) {
            armSettleAfterReady(exo)
        }
    }

    /**
     * After mute: wait until the player is READY, then [SESSION_SETTLE_MS] for
     * Poweramp EQ to bind/settle on the sticky session before any audible ramp.
     */
    private fun armSettleAfterReady(exo: ExoPlayer) {
        if (!sessionBindPending || player !== exo) return
        if (muteHeldForPause) return // never unmute while pause-held
        if (awaitAudioTrackInitialization) return
        if (exo.playbackState != Player.STATE_READY) return
        if (!exo.playWhenReady) return // paused READY — wait for resume path
        settleRunnable?.let { handler.removeCallbacks(it) }
        val token = exo
        val settleMs = sessionSettleMs()
        val settle = Runnable {
            if (player !== token) return@Runnable
            if (!sessionBindPending) return@Runnable
            if (muteHeldForPause) {
                settleRunnable = null
                return@Runnable
            }
            // Still not READY (rare race) — wait again.
            if (token.playbackState != Player.STATE_READY) {
                settleRunnable = null
                return@Runnable
            }
            if (!token.playWhenReady) {
                settleRunnable = null
                return@Runnable
            }
            sessionBindPending = false
            settleRunnable = null
            Log.i(TAG, "settle complete (${settleMs}ms) — ramp to RG/DVC target")
            applyReplayGain(token, token.currentMediaItem, eqMain)
            pcmGates[token]?.open()
        }
        settleRunnable = settle
        handler.postDelayed(settle, settleMs)
    }

    /** Longer settle when external EQ / PA is installed — seek recreate needs more bind time. */
    private fun sessionSettleMs(): Long =
        if (dualPlayerCfBlocked || ExternalEqRisk.isDualPlayerCrossfadeUnsafe(this)) {
            SESSION_SETTLE_EXTERNAL_EQ_MS
        } else {
            SESSION_SETTLE_MS
        }

    /** Apply gain once in PCM; AudioTrack volume is reserved for mute/settle and fades. */
    private fun applyReplayGain(exo: ExoPlayer, item: MediaItem?, eq: EqController) {
        if (fading) return
        rgLinear = ReplayGainProcessor.fromExtras(item?.mediaMetadata?.extras, settings.replayGainMode, settings.peakLimiter)
        // Audio may be decoded and queued while the AudioTrack is muted. Apply gain
        // before that buffering begins, so unmuting cannot reveal a unity-gain burst.
        setPcmGain(exo, rgLinear)
        if (sessionBindPending) return
        eq.applyReplayGainLinear(1f, settings)
        if (abs(exo.volume - 1f) >= 0.01f) {
            if (exo.volume < 0.5f) setPlayerVolumeSmooth(exo, 1f) else exo.volume = 1f
        }
    }

    private fun setPcmGain(exo: ExoPlayer, linear: Float) {
        pcmGates[exo]?.setReplayGain(1f)
        pcmGains[exo]?.let {
            it.linearGain = linear
            it.limiter = settings.peakLimiter && settings.replayGainMode != ReplayGainMode.Off
        }
    }

    private fun setPlayerVolumeSmooth(exo: ExoPlayer, target: Float) {
        volumeAnim?.cancel()
        val from = exo.volume
        if (abs(from - target) < 0.01f) {
            if (from != target) exo.volume = target
            volumeAnim = null
            return
        }
        volumeAnim = ValueAnimator.ofFloat(from, target).apply {
            duration = VOLUME_RAMP_MS
            addUpdateListener { a ->
                if (!fading && !sessionBindPending) exo.volume = a.animatedValue as Float
            }
            start()
        }
    }

    private fun notifyExternalEqSession(sessionId: Int, open: Boolean) {
        if (sessionId == 0) return
        if (open && sessionId == lastNotifiedSessionId) return
        val action = if (open) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
        } else {
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
        }
        runCatching {
            sendBroadcast(
                Intent(action).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                },
            )
        }
        Log.i(TAG, "${if (open) "OPEN" else "CLOSE"} audio effect session=$sessionId")
        if (open) {
            lastNotifiedSessionId = sessionId
        } else if (sessionId == lastNotifiedSessionId) {
            // Only clear when closing the session we currently advertise.
            lastNotifiedSessionId = 0
        }
        // Closing a stale session while another is already open: keep lastNotified.
    }

    private fun maybeStartCrossfade() {
        if (fading || sleepState.endOfTrack) return
        if (!settings.gapless || !settings.crossfade || !dualPlayerCrossfadeAllowed()) {
            // Option B: external EQ risk → single-player only (no second AudioTrack).
            releasePendingFadePlayer()
            return
        }
        val exo = player ?: return
        if (!exo.isPlaying) return
        val dur = exo.duration
        if (dur <= 0) return
        val remain = dur - exo.currentPosition
        val fade = settings.crossfadeMs.toLong()
        if (!exo.hasNextMediaItem()) {
            releasePendingFadePlayer()
            return
        }
        val nextIndex = exo.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return

        // Warm the next player well before the fade window so prepare/buffer is not
        // on the critical path (owner: pause before fade / next not loaded).
        if (remain > fade + PREPARE_LEAD_MS) {
            releasePendingFadePlayer()
            return
        }
        if (remain > 0) {
            ensureNextPrepared(exo, nextIndex)
        }

        // Only start the volume ramp once we are inside the fade window AND the next
        // player is READY + settled (PA EQ bind window on shared sticky session).
        // Do not duck the outgoing track while the next is cold / unbound.
        if (remain <= 0 || remain > fade) return
        if (fadePlayer == null || !pendingReady) return
        if (pendingReadyAtElapsed == 0L) return
        if (SystemClock.elapsedRealtime() - pendingReadyAtElapsed < sessionSettleMs()) return
        beginCrossfadeRamp(exo, fadeMs = minOf(fade, remain).coerceAtLeast(200L))
    }

    /**
     * Build/prepare the secondary player without taking audio focus and without playing.
     * Critical: [handleAudioFocus]=false so [play] later does not pause the outgoing
     * player (owner: hard cut / early stop instead of true overlap).
     *
     * Both players share [stickySessionId] — no second session from [buildPlayer].
     */
    private fun ensureNextPrepared(from: ExoPlayer, nextIndex: Int) {
        if (!dualPlayerCrossfadeAllowed()) {
            releasePendingFadePlayer()
            return
        }
        val existing = fadePlayer
        if (existing != null && pendingNextIndex == nextIndex) return
        releasePendingFadePlayer()

        // The shared data source resolves fresh authentication for crossfade opens.
        val items = (0 until from.mediaItemCount).map(from::getMediaItemAt)
        val next = buildPlayer(handleAudioFocus = false)
        // buildPlayer already set stickySessionId. Re-assert before prepare; if sticky
        // generation failed, share primary session (Option A / #23 absorb).
        val sharedSession = when {
            stickySessionId != 0 -> stickySessionId
            from.audioSessionId != 0 -> from.audioSessionId
            else -> 0
        }
        if (sharedSession != 0 && next.audioSessionId != sharedSession) {
            next.setAudioSessionId(sharedSession)
        }
        fadePlayer = next
        pendingNextIndex = nextIndex
        pendingReady = false
        pendingReadyAtElapsed = 0L
        pendingNextLinear = ReplayGainProcessor.fromExtras(
            items[nextIndex].mediaMetadata.extras,
            settings.replayGainMode,
            settings.peakLimiter,
        )
        setPcmGain(next, pendingNextLinear)
        applyDisconnectPolicy(next)
        if (sharedSession != 0 && next.audioSessionId == sharedSession) {
            // eqMain already owns DynamicsProcessing + OPEN broadcast on this session.
            // Do not attach eqFade (#17 double-attach) or open a second external session.
            eqFade.release()
        } else {
            // Fallback if session share failed (session still 0).
            attachEq(next, eqFade, openExternal = false, muteUntilBound = false)
            eqFade.applyReplayGainLinear(pendingNextLinear, settings)
        }
        next.volume = 0f
        next.setMediaItems(items, nextIndex, 0L)
        next.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (fadePlayer !== next) return
                if (playbackState == Player.STATE_READY) {
                    markFadePlayerReady()
                }
            }
        })
        next.prepare()
        // prepare() may already be READY before the listener is observed.
        if (next.playbackState == Player.STATE_READY) {
            markFadePlayerReady()
        }
    }

    private fun markFadePlayerReady() {
        if (!pendingReady) {
            pendingReady = true
            pendingReadyAtElapsed = SystemClock.elapsedRealtime()
            Log.d(TAG, "fade player READY — settle ${sessionSettleMs()}ms before CF ramp")
        }
    }

    private fun beginCrossfadeRamp(from: ExoPlayer, fadeMs: Long) {
        val next = fadePlayer ?: return
        if (!pendingReady || fading) return
        fading = true
        volumeAnim?.cancel()
        volumeAnim = null

        val nextLinear = pendingNextLinear
        setPcmGain(from, rgLinear)
        setPcmGain(next, nextLinear)
        val fromGain = from.volume.coerceIn(0f, 1f)
        val nextGain = 1f

        from.pauseAtEndOfMediaItems = true
        // Secondary was built without audio focus — both players can be audible together.
        next.playWhenReady = true
        if (!next.isPlaying) next.play()

        fadeAnim?.cancel()
        fadeAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = fadeMs
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                from.volume = fromGain * (1f - t)
                next.volume = nextGain * t
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    finishCrossfade(from, next, nextLinear)
                }
            })
            start()
        }
    }

    private fun finishCrossfade(from: ExoPlayer, next: ExoPlayer, nextLinear: Float) {
        if (!fading || player !== from || fadePlayer !== next) return
        fadePlayer = null
        pendingReady = false
        pendingReadyAtElapsed = 0L
        pendingNextIndex = C.INDEX_UNSET
        rgLinear = nextLinear
        fading = false
        fadeAnim = null

        // Point internal primary at next first so outgoing listeners ignore focus loss.
        player = next

        // Take audio focus on next WHILE from still holds it. Releasing from first
        // abandoned focus, then setAudioAttributes re-requested it and Media3 briefly
        // suppressed playback (title already switched → owner-visible ~1s pause).
        next.setAudioAttributes(mediaAudioAttributes(), /* handleAudioFocus= */ true)

        // Shared sticky session: eqMain already owns the live effects — do not release.
        // Separate-session fallback: adopt eqFade without teardown+reattach on next.
        promoteFadeEqToMain(next)

        // Session / NP title switch only after focus + EQ handoff are done.
        session?.player = guardedPlayer(next)
        applyGapless(next)
        installPlayerListeners(next)
        updateSongPrefetch(next)
        // If dual CF somehow ran under external-EQ risk (race / gate miss), mute
        // until settle before RG→1f across outgoing AudioTrack teardown.
        // Normal dual-CF (no external EQ) restores RG immediately — no promote dip.
        if (dualPlayerCfBlocked || ExternalEqRisk.isDualPlayerCrossfadeUnsafe(this)) {
            next.volume = 0f
            beginMuteUntilBound(next, eqMain, reason = "cf-promote-external-eq")
        } else {
            applyReplayGain(next, next.currentMediaItem, eqMain)
        }

        // Retire outgoing off the critical path so AudioTrack teardown cannot glitch
        // the already-audible next track in the same frame as the promote.
        handler.post {
            runCatching {
                pcmGates.remove(from)
                pcmEqs.remove(from)
                pcmGains.remove(from)
                from.stop()
                from.release()
            }
        }
    }

    /**
     * Ensure eqMain owns platform effects for [next] without CLOSING/OPENING the
     * external EQ session when the audio session was shared (DVC-safe).
     */
    private fun promoteFadeEqToMain(next: ExoPlayer) {
        val prevMainSession = eqMain.audioSessionId
        val nextSession = next.audioSessionId
        if (nextSession != 0 && (nextSession == prevMainSession || nextSession == stickySessionId)) {
            // Shared / sticky session path: keep eqMain's DynamicsProcessing + OPEN.
            eqFade.release()
            return
        }
        // Separate-session fallback: move eqFade's live effects onto eqMain (no recreate).
        eqMain.release()
        eqMain.adoptFrom(eqFade)
        if (nextSession != 0) {
            notifyExternalEqSession(nextSession, open = true)
        }
        if (prevMainSession != 0 && prevMainSession != nextSession && stickySessionId == 0) {
            // Only CLOSE stale when we have no sticky lifetime session.
            notifyExternalEqSession(prevMainSession, open = false)
        }
    }

    private fun releasePendingFadePlayer() {
        if (fading) return
        fadeAnim?.removeAllListeners()
        fadeAnim?.removeAllUpdateListeners()
        fadeAnim?.cancel()
        fadeAnim = null
        fadePlayer?.let {
            pcmGates.remove(it)
            pcmEqs.remove(it)
            pcmGains.remove(it)
            it.stop()
            it.release()
        }
        fadePlayer = null
        pendingReady = false
        pendingReadyAtElapsed = 0L
        pendingNextIndex = C.INDEX_UNSET
    }

    private fun armSleepTimerFromSettings() {
        handler.removeCallbacks(sleepFire)
        sleepState.arm(settings.sleepTimerMinutes)
        if (sleepState.endOfTrack) cancelCrossfade()
        player?.let(::applyGapless)
        when (val minutes = settings.sleepTimerMinutes) {
            0 -> Unit
            PlayerSettings.SLEEP_END_OF_TRACK -> {
                // ExoPlayer pauses at the item boundary, including repeat, without polling.
            }
            else -> {
                val delay = SleepTimerDeadline.remainingDelayMs(
                    savedBootCount = settings.sleepDeadlineBootCount,
                    currentBootCount = settings.currentBootCount,
                    elapsedDeadlineMs = settings.sleepDeadlineElapsed,
                    wallDeadlineMs = settings.sleepDeadlineWallMs,
                    elapsedNowMs = SystemClock.elapsedRealtime(),
                    wallNowMs = System.currentTimeMillis(),
                    fallbackDurationMs = minutes * 60_000L,
                )
                if (delay <= 0L) {
                    onSleepTimerFired()
                } else {
                    handler.postDelayed(sleepFire, delay)
                }
            }
        }
    }

    private fun onSleepTimerFired() {
        handler.removeCallbacks(sleepFire)
        sleepState.arm(0)
        // Detach the animation callbacks before cancellation: cancel also dispatches end.
        cancelCrossfade(restoreVolume = false)
        player?.let { onGuardedPause(it, "sleep-timer") }
        player?.pause()
        settings.clearSleepTimer()
    }

    private fun cancelSleepFromUser() {
        if (settings.sleepTimerMinutes == 0 && !sleepState.endOfTrack) return
        handler.removeCallbacks(sleepFire)
        sleepState.arm(0)
        player?.let(::applyGapless)
        settings.clearSleepTimer()
    }

    private fun cancelCrossfade(restoreVolume: Boolean = true) {
        fadeAnim?.removeAllListeners()
        fadeAnim?.removeAllUpdateListeners()
        fadeAnim?.cancel()
        fadeAnim = null
        fadePlayer?.let {
            pcmGates.remove(it)
            pcmEqs.remove(it)
            pcmGains.remove(it)
            it.stop()
            it.release()
        }
        fadePlayer = null
        pendingReady = false
        pendingReadyAtElapsed = 0L
        pendingNextIndex = C.INDEX_UNSET
        fading = false
        player?.let { exo ->
            // Skip restore when a mute-until-bound is in flight (playlist/album change)
            // so we never snap volume=1f before PA EQ has settled.
            if (restoreVolume && !sessionBindPending) {
                applyReplayGain(exo, exo.currentMediaItem, eqMain)
            }
            applyGapless(exo)
        }
    }

    /**
     * Option B gate: dual-ExoPlayer crossfade only when external EQ risk is absent.
     * Re-check at most every [DUAL_CF_GATE_TTL_MS] so install/uninstall of PA EQ
     * is picked up without per-tick PackageManager spam.
     */
    private fun dualPlayerCrossfadeAllowed(): Boolean {
        refreshDualPlayerCfGate(force = false)
        return !dualPlayerCfBlocked
    }

    private fun refreshDualPlayerCfGate(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && dualPlayerCfCheckedAtElapsed != 0L &&
            now - dualPlayerCfCheckedAtElapsed < DUAL_CF_GATE_TTL_MS
        ) {
            return
        }
        dualPlayerCfCheckedAtElapsed = now
        val wasBlocked = dualPlayerCfBlocked
        val blocked = ExternalEqRisk.isDualPlayerCrossfadeUnsafe(this)
        if (blocked != dualPlayerCfBlocked) {
            Log.i(
                TAG,
                "Option B dual-player CF ${if (blocked) "BLOCKED" else "allowed"} " +
                    "(PA EQ installed=${ExternalEqRisk.isPowerampEqualizerInstalled(this)})",
            )
        }
        dualPlayerCfBlocked = blocked
        if (blocked != wasBlocked) {
            pcmGates.values.forEach { gate -> if (blocked) gate.enable() else gate.disable() }
            if (blocked) {
                if (stickySessionId != 0) {
                    dvcAnchor = DvcSessionAnchor(stickySessionId).also { it.start() }
                }
                eqMain.release()
                eqFade.release()
                player?.let { beginMuteUntilBound(it, eqMain, reason = "external-eq-installed") }
            } else {
                handler.removeCallbacks(stopDvcAnchor)
                dvcAnchor?.stop()
                dvcAnchor = null
                player?.let { attachEq(it, eqMain, openExternal = true, muteUntilBound = false) }
            }
        }
        if (blocked != wasBlocked) player?.let(::applyGapless)
        if (blocked) {
            if (fading) {
                player?.let { beginMuteUntilBound(it, eqMain, reason = "external-eq-during-crossfade") }
                cancelCrossfade(restoreVolume = false)
            } else {
                releasePendingFadePlayer()
            }
        }
    }

    companion object {
        private const val TAG = "Sonveil/DvcSession"
        private const val VOLUME_RAMP_MS = 120L
        /** How often to re-query PackageManager for external EQ presence. */
        private const val DUAL_CF_GATE_TTL_MS = 30_000L
        /**
         * After READY / OPEN: hold mute this long so Poweramp EQ can bind on the
         * sticky session before any audible output (album change / skip / CF).
         * 80ms was insufficient for OEM AudioTrack recreate under DVC.
         */
        private const val SESSION_SETTLE_MS = 250L
        /**
         * Seek flush always releases AudioTrack (Media3 DefaultAudioSink). PA DVC
         * needs a longer bind window after recreate than a same-track decoder skip.
         */
        private const val SESSION_SETTLE_EXTERNAL_EQ_MS = 450L
        private const val DVC_ANCHOR_PAUSE_GRACE_MS = 15_000L
        /** How far ahead of the fade window to prepare the next ExoPlayer. */
        private const val PREPARE_LEAD_MS = 8_000L
        /** Transient IO auto-retry after [Player.Listener.onPlayerError]. */
        private const val MAX_ERROR_RETRIES = 4
        private const val ERROR_RETRY_BASE_MS = 750L
    }
}

@UnstableApi
private class SonveilNotificationProvider(
    private val appContext: Context,
) : MediaNotification.Provider {

    private val inner = DefaultMediaNotificationProvider.Builder(appContext)
        // Keep the stored channel ID so upgrades retain users' notification settings.
        .setChannelId("auralis_playback")
        .setChannelName(R.string.playback_channel)
        .build()
        .also { it.setSmallIcon(R.drawable.ic_stat_sonveil) }

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val created = inner.createNotification(
            mediaSession,
            mediaButtonPreferences,
            actionFactory,
            onNotificationChangedCallback,
        )
        val rebuilt = Notification.Builder.recoverBuilder(appContext, created.notification)
            .setSubText(appContext.getString(R.string.app_name))
            .setContentInfo(appContext.getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_stat_sonveil)
            .setColorized(true)
            .build()
        return MediaNotification(created.notificationId, rebuilt)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = inner.handleCustomCommand(session, action, extras)
}
