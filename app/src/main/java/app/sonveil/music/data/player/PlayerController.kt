package app.sonveil.music.data.player

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import app.sonveil.music.data.download.DownloadStore
import app.sonveil.music.data.art.CoverArtContentProvider
import app.sonveil.music.data.remote.Song
import app.sonveil.music.data.remote.SongLyrics
import app.sonveil.music.data.remote.SubsonicClient
import app.sonveil.music.data.remote.suspendRunCatching
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.CancellationException
import com.google.common.util.concurrent.ListenableFuture
import kotlin.random.Random

internal fun <T> shuffledQueueFromCurrent(items: List<T>, currentIndex: Int, random: Random = Random.Default): List<T> {
    if (currentIndex !in items.indices) return items
    return listOf(items[currentIndex]) + items.filterIndexed { index, _ -> index != currentIndex }.shuffled(random)
}

data class PlayerUiState(
    val queue: List<Song> = emptyList(),
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val palette: AuralisPalette = AuralisPalette.darkDefault(),
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
    val favoriteById: Map<String, Boolean> = emptyMap(),
    val favoriteEpoch: Int = 0,
    val upcomingIndices: List<Int>? = null,
    val playbackError: String? = null,
    val lyrics: SongLyrics? = null,
    val artworkSongId: String? = null,
    val artworkCoverId: String? = null,
) {
    val current: Song? get() = queue.getOrNull(index)
    val currentCoverArt: String?
        get() = current?.let { song ->
            if (artworkSongId == song.id) artworkCoverId ?: song.coverArt else song.coverArt
        }
    val upNextIndices: List<Int> get() = upcomingIndices ?: ((index + 1) until queue.size).toList()
    val upNext: List<Song> get() = upNextIndices.mapNotNull(queue::getOrNull)
    fun isFavorite(song: Song): Boolean = favoriteById[song.id] ?: song.isFavorite
}

internal fun PlayerUiState.withRestoredQueue(snapshot: PlaybackQueueStore.Snapshot): PlayerUiState {
    val index = snapshot.index.coerceIn(0, snapshot.songs.lastIndex)
    return copy(
        queue = snapshot.songs,
        index = index,
        positionMs = snapshot.positionMs,
        durationMs = snapshot.songs[index].duration?.times(1000L) ?: 0L,
        repeatMode = snapshot.repeatMode,
        shuffle = snapshot.shuffle,
        upcomingIndices = null,
        playbackError = null,
    )
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerController(
    private val context: Context,
    private val client: SubsonicClient,
    private val downloadStore: DownloadStore? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    private var controller: MediaController? = null
    private var connection: ListenableFuture<MediaController>? = null
    private var artworkJob: Job? = null
    private var lyricsJob: Job? = null
    private val albumCoverCache = mutableMapOf<String, String>()
    private val favoriteInFlight = mutableSetOf<String>()
    private var positionJob: Job? = null
    private var scrobbledId: String? = null
    private var listenedMs: Long = 0
    private var preferDark: Boolean = true
    private data class PendingQueue(val songs: List<Song>, val index: Int, val autoPlay: Boolean)
    private var pendingPlay: PendingQueue? = null
    private val queueStore = PlaybackQueueStore(context)
    private val queueWriter = PlaybackQueueWriter(queueStore)
    private var lastPersistAtMs = 0L
    private var restoreAttempted = false
    private data class RestoredPlayback(val positionMs: Long, val shuffle: Boolean, val repeatMode: Int)
    private var pendingRestoredPlayback: RestoredPlayback? = null
    var transcodeBitrate: Int = 0

    fun setPreferDark(dark: Boolean) {
        val changed = preferDark != dark
        preferDark = dark
        if (changed) {
            _state.value.current?.let { refreshArtwork(it) } ?: resetPalette()
        }
    }

    fun connect() {
        if (controller != null || connection != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(disconnected: MediaController) {
                    if (controller === disconnected) {
                        controller = null
                        positionJob?.cancel()
                        _state.update { it.copy(isPlaying = false) }
                    }
                }
            }).buildAsync()
        connection = future
        future.addListener(
            {
                if (connection !== future) return@addListener
                connection = null
                val c = runCatching { future.get() }.getOrNull() ?: run {
                    _state.update { it.copy(isPlaying = false) }
                    return@addListener
                }
                controller = c
                c.addListener(listener)
                applyPendingRestoredPlaybackIfReady()
                syncFromPlayer(preserveRestoredState = pendingRestoredPlayback != null)
                startPositionLoop()
                pendingPlay?.let { (songs, idx, autoPlay) ->
                    pendingPlay = null
                    setQueue(songs, idx, autoPlay)
                } ?: maybeRestoreAfterProcessDeath(playWhenReady = false)
                if (pendingRestoredPlayback == null) {
                    val st = _state.value
                    applyShuffleAndRepeat(st.shuffle, st.repeatMode)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun play(songs: List<Song>, startIndex: Int = 0) {
        if (_state.value.shuffle && songs.isNotEmpty()) {
            setQueue(shuffledQueueFromCurrent(songs, startIndex.coerceIn(0, songs.lastIndex)), 0, autoPlay = true)
        } else {
            setQueue(songs, startIndex, autoPlay = true)
        }
    }

    private fun setQueue(
        songs: List<Song>,
        startIndex: Int,
        autoPlay: Boolean,
        positionMs: Long = 0L,
    ) {
        if (songs.isEmpty()) return
        if (client.credentials == null) return
        val idx = startIndex.coerceIn(0, songs.lastIndex)
        val pos = positionMs.coerceAtLeast(0L)
        queueWriter.activate()
        val c = controller
        if (c == null) {
            pendingPlay = PendingQueue(songs.toList(), idx, autoPlay)
            _state.update {
                it.copy(queue = songs.toList(), index = idx, positionMs = pos, isPlaying = false)
            }
            connect()
            return
        }
        val items = songs.map { it.toMediaItem() }
        // MediaController may notify listeners synchronously from setMediaItems.
        _state.update {
            it.copy(
                queue = songs.toList(),
                index = idx,
                positionMs = pos,
                durationMs = 0,
                upcomingIndices = null,
                playbackError = null,
            )
        }
        scrobbledId = null
        c.playWhenReady = autoPlay
        c.setMediaItems(items, idx, pos)
        c.prepare()
        syncFromPlayer()
        persistQueue(force = true)
    }

    fun applyTranscode(bps: Int) {
        if (transcodeBitrate == bps) return
        transcodeBitrate = bps
        val c = controller ?: return
        val st = _state.value
        if (st.queue.isEmpty()) return
        val pos = c.currentPosition.coerceAtLeast(0L)
        val idx = c.currentMediaItemIndex.coerceAtLeast(0)
        val playing = c.playWhenReady
        c.setMediaItems(st.queue.map { it.toMediaItem() }, idx.coerceIn(0, st.queue.lastIndex), pos)
        c.prepare()
        if (playing) c.play()
    }

    fun stopAndReset() {
        scope.coroutineContext.cancelChildren()
        artworkJob = null
        lyricsJob = null
        albumCoverCache.clear()
        favoriteInFlight.clear()
        connection?.let { MediaController.releaseFuture(it) }
        connection = null
        pendingPlay = null
        scrobbledId = null
        restoreAttempted = false
        pendingRestoredPlayback = null
        queueWriter.invalidateAndClear()
        runCatching {
            controller?.removeListener(listener)
            controller?.stop()
            controller?.clearMediaItems()
            controller?.release()
        }
        controller = null
        _state.value = PlayerUiState(
            palette = if (preferDark) AuralisPalette.darkDefault() else AuralisPalette.lightDefault(),
        )
    }

    fun playPause() {
        val c = controller ?: run {
            val st = _state.value
            if (st.queue.isNotEmpty()) {
                setQueue(st.queue, st.index, autoPlay = true, positionMs = st.positionMs)
            } else {
                maybeRestoreAfterProcessDeath(playWhenReady = true)
            }
            return
        }
        if (c.isPlaying) {
            c.pause()
            return
        }
        // After PlaybackException ExoPlayer is IDLE with a sticky playerError until prepare succeeds.
        if (c.playerError != null || _state.value.playbackError != null) {
            retryPlayback()
            return
        }
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    /**
     * Recover from a sticky playback error without force-stopping the app.
     * Same energy as playing a new album: rebuild stream MediaItems (fresh salt/token
     * query params) + prepare/play on the **current queue** — no force-stop required.
     * Does not recreate the MediaSession / sticky audio session — service owns those.
     */
    fun retryPlayback() {
        val st = _state.value
        if (st.queue.isEmpty()) return
        val c = controller
        val idx = (c?.currentMediaItemIndex ?: st.index).coerceIn(0, st.queue.lastIndex)
        val pos = when {
            c != null && c.currentPosition > 0L -> c.currentPosition
            st.positionMs > 0L -> st.positionMs
            else -> 0L
        }
        reprepareFromQueue(idx, pos, autoPlay = true, status = "Retrying…")
    }

    /**
     * Rebuild every MediaItem URI from song id + current credentials and prepare.
     * This is the path that recovers when "play a new album" works but the same
     * queue is stuck after ERROR_CODE_IO_NETWORK_CONNECTION_*.
     */
    private fun reprepareFromQueue(
        index: Int,
        positionMs: Long,
        autoPlay: Boolean,
        status: String? = null,
    ) {
        val st = _state.value
        if (st.queue.isEmpty()) return
        if (client.credentials == null) return
        val idx = index.coerceIn(0, st.queue.lastIndex)
        val pos = positionMs.coerceAtLeast(0L)
        _state.update {
            it.copy(
                playbackError = status,
                isPlaying = false,
                index = idx,
                positionMs = pos,
            )
        }
        val c = controller
        if (c == null) {
            setQueue(st.queue, idx, autoPlay = autoPlay, positionMs = pos)
            return
        }
        runCatching {
            c.playWhenReady = autoPlay
            c.setMediaItems(st.queue.map { it.toMediaItem() }, idx, pos)
            c.prepare()
            if (autoPlay) c.play()
            if (status != null) {
                _state.update { s -> s.copy(playbackError = null) }
            }
        }.onFailure {
            _state.update { s -> s.copy(playbackError = "Connection error", isPlaying = false) }
        }
    }

    private fun hasStickyPlaybackError(c: Player): Boolean =
        c.playerError != null || _state.value.playbackError != null

    fun next() {
        val c = controller ?: return
        if (hasStickyPlaybackError(c)) {
            val nextIdx = c.nextMediaItemIndex
            if (nextIdx != C.INDEX_UNSET && nextIdx in _state.value.queue.indices) {
                // Skip must rebuild URIs — seek+prepare alone leaves the sticky error trap.
                reprepareFromQueue(nextIdx, 0L, autoPlay = true)
            } else {
                retryPlayback()
            }
            return
        }
        c.seekToNext()
    }

    fun previous() {
        val c = controller ?: return
        if (hasStickyPlaybackError(c)) {
            val prevIdx = c.previousMediaItemIndex
            if (prevIdx != C.INDEX_UNSET && prevIdx in _state.value.queue.indices) {
                reprepareFromQueue(prevIdx, 0L, autoPlay = true)
            } else {
                retryPlayback()
            }
            return
        }
        c.seekToPrevious()
    }

    fun seek(ms: Long) {
        val bounded = ms.coerceIn(0L, _state.value.durationMs.coerceAtLeast(0L))
        controller?.seekTo(bounded)
        _state.update { it.copy(positionMs = bounded) }
    }

    fun playNext(song: Song) {
        val c = controller
        if (c == null) {
            val pending = pendingPlay
            val q = _state.value.queue.toMutableList()
            q.add((_state.value.index + 1).coerceAtMost(q.size), song)
            setQueue(q, pending?.index ?: 0, pending?.autoPlay ?: false)
            return
        }
        val insertAt = (_state.value.index + 1).coerceAtMost(_state.value.queue.size)
        val item = song.toMediaItem()
        _state.update {
            val q = it.queue.toMutableList()
            q.add(insertAt, song)
            it.copy(queue = q)
        }
        c.addMediaItem(insertAt, item)
        persistQueue(force = true)
    }

    fun addToQueue(song: Song) {
        val c = controller
        if (c == null) {
            val pending = pendingPlay
            setQueue(_state.value.queue + song, pending?.index ?: 0, pending?.autoPlay ?: false)
            return
        }
        val item = song.toMediaItem()
        _state.update { it.copy(queue = it.queue + song) }
        c.addMediaItem(item)
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        persistQueue(force = true)
    }

    fun playFromQueue(index: Int) {
        if (index !in _state.value.queue.indices) return
        val c = controller
        if (c == null || hasStickyPlaybackError(c)) {
            reprepareFromQueue(index, 0L, autoPlay = true)
            return
        }
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun toggleRepeat() {
        val c = controller ?: return
        val next = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        c.repeatMode = next
        _state.update { it.copy(repeatMode = next) }
        persistQueue(force = true)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        val st = _state.value
        if (st.shuffle) {
            _state.update { it.copy(shuffle = false) }
        } else {
            if (st.queue.isEmpty() || c.mediaItemCount != st.queue.size) return
            val index = c.currentMediaItemIndex.takeIf { it in st.queue.indices } ?: st.index
            val mediaItems = (st.queue.indices).map(c::getMediaItemAt)
            val order = shuffledQueueFromCurrent(st.queue.indices.toList(), index)
            val shuffled = order.map(st.queue::get)
            // Moving the current item keeps its playback stream and position intact.
            // Replacing only the following items leaves the current item playing.
            c.shuffleModeEnabled = false
            if (index != 0) {
                val moved = st.queue.toMutableList().apply { add(0, removeAt(index)) }
                _state.update { it.copy(queue = moved, index = 0, shuffle = true, upcomingIndices = null) }
                c.moveMediaItem(index, 0)
            }
            _state.update {
                it.copy(queue = shuffled, index = 0, shuffle = true, upcomingIndices = null)
            }
            c.replaceMediaItems(1, c.mediaItemCount, order.drop(1).map(mediaItems::get))
            syncFromPlayer()
        }
        persistQueue(force = true)
    }

    fun toggleFavorite(song: Song? = _state.value.current) {
        val target = song ?: return
        setFavorite(target, !_state.value.isFavorite(target))
    }

    fun setFavorite(song: Song, favorite: Boolean) {
        val currentlyFav = _state.value.isFavorite(song)
        if (currentlyFav == favorite) return
        if (!favoriteInFlight.add(song.id)) return
        val previous = _state.value.favoriteById[song.id]
        val previousStarred = _state.value.queue.firstOrNull { it.id == song.id }?.starred ?: song.starred
        val stamped = if (favorite) java.time.Instant.now().toString() else null
        _state.update { st ->
            st.copy(
                favoriteById = st.favoriteById + (song.id to favorite),
                queue = st.queue.map { if (it.id == song.id) it.copy(starred = stamped) else it },
            )
        }
        scope.launch {
            try {
                if (favorite) client.starSong(song.id) else client.unstarSong(song.id)
                _state.update { it.copy(favoriteEpoch = it.favoriteEpoch + 1) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { st ->
                    val reverted = st.favoriteById.toMutableMap()
                    if (previous == null) reverted.remove(song.id) else reverted[song.id] = previous
                    st.copy(
                        favoriteById = reverted,
                        queue = st.queue.map {
                            if (it.id == song.id) it.copy(starred = previousStarred) else it
                        },
                    )
                }
            } finally {
                favoriteInFlight.remove(song.id)
            }
        }
    }

    fun extractPalette(bitmap: Bitmap) {
        scope.launch(Dispatchers.Default) {
            val palette = PaletteExtractor.from(bitmap, preferDark)
            _state.update { it.copy(palette = palette) }
        }
    }

    fun resetPalette() {
        _state.update {
            it.copy(palette = if (preferDark) AuralisPalette.darkDefault() else AuralisPalette.lightDefault())
        }
    }

    /**
     * Adopt a queue set by Android Auto / MediaLibrarySession without calling
     * MediaController.setMediaItems (session already applies the playable items).
     */
    fun adoptExternalQueue(
        songs: List<Song>,
        startIndex: Int,
        positionMs: Long = 0L,
        restoredShuffle: Boolean? = null,
        restoredRepeatMode: Int? = null,
        persist: Boolean = true,
    ) {
        if (songs.isEmpty()) return
        val idx = startIndex.coerceIn(0, songs.lastIndex)
        queueWriter.activate()
        scrobbledId = null
        listenedMs = 0
        _state.update {
            it.copy(
                queue = songs.toList(),
                index = idx,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = songs.getOrNull(idx)?.duration?.times(1000L) ?: 0L,
                upcomingIndices = null,
                playbackError = null,
                shuffle = restoredShuffle ?: it.shuffle,
                repeatMode = restoredRepeatMode ?: it.repeatMode,
            )
        }
        if (controller == null) connect()
        else {
            songs.getOrNull(idx)?.let {
                refreshArtwork(it)
                refreshLyrics(it)
            }
        }
        if (persist) persistQueue(force = true)
    }

    /**
     * Media3 playback resumption after process death (notification / BT / Auto).
     * Rebuilds stream MediaItems and syncs the Song queue into UI state.
     */
    fun resumptionMediaItems(): MediaSession.MediaItemsWithStartPosition? {
        val snap = queueStore.load() ?: return null
        val creds = client.credentials ?: return null
        val key = downloadStore?.serverKey(creds)
        if (key != null && snap.serverKey != key) {
            queueStore.clear()
            return null
        }
        if (snap.songs.isEmpty()) {
            queueStore.clear()
            return null
        }
        val idx = snap.index.coerceIn(0, snap.songs.lastIndex)
        pendingRestoredPlayback = RestoredPlayback(snap.positionMs, snap.shuffle, snap.repeatMode)
        _state.update { it.withRestoredQueue(snap) }
        adoptExternalQueue(
            songs = snap.songs,
            startIndex = idx,
            positionMs = snap.positionMs,
            restoredShuffle = snap.shuffle,
            restoredRepeatMode = snap.repeatMode,
            persist = false,
        )
        applyPendingRestoredPlaybackIfReady()
        val items = snap.songs.map { it.toMediaItem() }
        return MediaSession.MediaItemsWithStartPosition(items, idx, snap.positionMs)
    }

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            // Keep a short sticky label until STATE_READY (or explicit retryPlayback clears it).
            _state.update {
                it.copy(playbackError = PlaybackErrors.userMessage(error), isPlaying = false)
            }
        }
        override fun onEvents(player: Player, events: Player.Events) {
            applyPendingRestoredPlaybackIfReady()
            syncFromPlayer(preserveRestoredState = pendingRestoredPlayback != null)
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            applyPendingRestoredPlaybackIfReady()
            syncFromPlayer(preserveRestoredState = pendingRestoredPlayback != null)
            scrobbledId = null
            listenedMs = 0
            // Rebuild upcoming stream URIs (fresh salt) so track N+1 is not the
            // enqueue-time URL — mirrors new-album recovery without waiting for error.
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                refreshUpcomingStreamUris()
            }
            val song = _state.value.current
            if (song != null) {
                scope.launch { client.scrobble(song.id, submission = false) }
                refreshArtwork(song)
                refreshLyrics(song)
            } else {
                _state.update { it.copy(lyrics = null) }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) _state.update { it.copy(playbackError = null) }
            applyPendingRestoredPlaybackIfReady()
            syncFromPlayer(preserveRestoredState = pendingRestoredPlayback != null)
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            applyPendingRestoredPlaybackIfReady()
        }
    }

    private fun syncFromPlayer(preserveRestoredState: Boolean = false) {
        val c = controller ?: return
        val idx = c.currentMediaItemIndex.coerceAtLeast(0)
        val timeline = c.currentTimeline
        val upcoming = mutableListOf<Int>()
        if (!timeline.isEmpty && idx < timeline.windowCount) {
            var next = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            while (next != androidx.media3.common.C.INDEX_UNSET && upcoming.size < timeline.windowCount) {
                upcoming.add(next)
                next = timeline.getNextWindowIndex(next, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            }
        }
        _state.update {
            it.copy(
                index = if (it.queue.isEmpty()) 0 else idx.coerceIn(0, it.queue.lastIndex),
                isPlaying = c.isPlaying,
                positionMs = if (preserveRestoredState) it.positionMs else c.currentPosition.coerceAtLeast(0L),
                durationMs = c.duration.takeIf { d -> d > 0 } ?: (it.queue.getOrNull(idx)?.duration?.times(1000L) ?: 0L),
                repeatMode = if (preserveRestoredState) it.repeatMode else c.repeatMode,
                shuffle = it.shuffle,
                upcomingIndices = upcoming,
            )
        }
        persistQueue(force = false)
    }

    private fun startPositionLoop() {
        positionJob?.cancel()
        positionJob = scope.launch {
            var lastTick = android.os.SystemClock.elapsedRealtime()
            var wasPlaying = false
            while (isActive) {
                val tick = android.os.SystemClock.elapsedRealtime()
                val elapsed = (tick - lastTick).coerceIn(0, 2000)
                lastTick = tick
                val c = controller
                if (c != null) {
                    if (wasPlaying && c.isPlaying) listenedMs += elapsed
                    wasPlaying = c.isPlaying
                    val pos = c.currentPosition.coerceAtLeast(0L)
                    val dur = c.duration.takeIf { it > 0 } ?: (_state.value.current?.duration?.times(1000L) ?: 0L)
                    _state.update { it.copy(positionMs = pos, durationMs = dur, isPlaying = c.isPlaying) }
                    persistQueue(force = false)
                    val song = _state.value.current
                    if (song != null && scrobbledId != song.id && dur > 0) {
                        val threshold = minOf(30_000L, dur / 2)
                        // Seeking forward (especially while paused) is not listening.
                        if (listenedMs >= threshold) {
                            scrobbledId = song.id
                            launch { client.scrobble(song.id, submission = true) }
                        }
                    }
                }
                delay(400)
            }
        }
    }

    private fun refreshLyrics(song: Song) {
        lyricsJob?.cancel()
        _state.update { it.copy(lyrics = null) }
        lyricsJob = scope.launch {
            val lyrics = suspendRunCatching { client.lyricsForSong(song) }.getOrNull()
            if (_state.value.current?.id == song.id) _state.update { it.copy(lyrics = lyrics) }
        }
    }

    private fun refreshArtwork(song: Song) {
        artworkJob?.cancel()
        _state.update { it.copy(artworkSongId = song.id, artworkCoverId = song.coverArt) }
        artworkJob = scope.launch {
            val coverId = song.albumId?.let { albumId ->
                albumCoverCache[albumId] ?: try {
                    client.getAlbum(albumId).coverArt?.also { albumCoverCache[albumId] = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            } ?: song.coverArt
            if (_state.value.current?.id != song.id) return@launch
            _state.update { it.copy(artworkSongId = song.id, artworkCoverId = coverId) }
            val palette = try {
                loadArtwork(coverId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null // Missing or malformed artwork must never crash playback.
            }
            if (_state.value.current?.id == song.id) {
                if (palette == null) resetPalette()
                else _state.update { it.copy(palette = palette) }
            }
        }
    }

    private suspend fun loadArtwork(coverId: String?): AuralisPalette? {
        val url = client.coverUrl(coverId, 800) ?: return null
        // Palette extraction needs a small software bitmap, not full-size cover art.
        val req = ImageRequest.Builder(context).data(url).size(256).allowHardware(false).build()
        val result = context.imageLoader.execute(req)
        if (result is SuccessResult) {
            val bmp = (result.drawable as? BitmapDrawable)?.bitmap ?: return null
            return withContext(Dispatchers.Default) { PaletteExtractor.from(bmp, preferDark) }
        }
        return null
    }


    private fun maybeRestoreAfterProcessDeath(playWhenReady: Boolean) {
        if (restoreAttempted) return
        restoreAttempted = true
        if (client.credentials == null) return
        if (_state.value.queue.isNotEmpty()) return
        val c = controller
        if (c != null && c.mediaItemCount > 0) return
        val snap = queueStore.load() ?: return
        val key = downloadStore?.serverKey(client.credentials!!)
        if (key != null && snap.serverKey != key) {
            queueStore.clear()
            return
        }
        val idx = snap.index.coerceIn(0, snap.songs.lastIndex)
        pendingRestoredPlayback = RestoredPlayback(snap.positionMs, snap.shuffle, snap.repeatMode)
        _state.update { it.withRestoredQueue(snap) }
        setQueue(
            snap.songs,
            idx,
            autoPlay = playWhenReady && snap.playWhenReady,
            positionMs = snap.positionMs,
        )
        applyPendingRestoredPlaybackIfReady()
    }

    /** Applies modes to the service player after Media3 installs resumed items. */
    fun applyPendingRestoredPlaybackWhenReady(player: Player) {
        if (applyPendingRestoredPlayback(player)) return
        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (applyPendingRestoredPlayback(player)) player.removeListener(this)
            }
        }
        player.addListener(listener)
    }

    private fun applyPendingRestoredPlayback(player: Player): Boolean {
        val restored = pendingRestoredPlayback ?: return true
        if (player.mediaItemCount == 0) return false
        player.shuffleModeEnabled = false
        player.repeatMode = restored.repeatMode
        _state.update {
            it.copy(
                positionMs = restored.positionMs,
                shuffle = restored.shuffle,
                repeatMode = restored.repeatMode,
            )
        }
        pendingRestoredPlayback = null
        return true
    }

    private fun applyPendingRestoredPlaybackIfReady() {
        val c = controller ?: return
        applyPendingRestoredPlayback(c)
    }

    private fun applyShuffleAndRepeat(shuffle: Boolean, repeatMode: Int) {
        val c = controller ?: return
        c.shuffleModeEnabled = false
        c.repeatMode = repeatMode
        _state.update { it.copy(shuffle = shuffle, repeatMode = repeatMode) }
    }

    private fun persistQueue(force: Boolean) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastPersistAtMs < 5_000L) return
        val st = _state.value
        if (st.queue.isEmpty()) return
        val creds = client.credentials ?: return
        val key = downloadStore?.serverKey(creds) ?: "default"
        val c = controller
        lastPersistAtMs = now
        val snap = PlaybackQueueStore.Snapshot(
            serverKey = key,
            songs = st.queue,
            index = st.index,
            positionMs = (c?.currentPosition ?: st.positionMs).coerceAtLeast(0L),
            playWhenReady = c?.playWhenReady ?: st.isPlaying,
            shuffle = st.shuffle,
            repeatMode = c?.repeatMode ?: st.repeatMode,
        )
        queueWriter.submit(snap)
    }

    /**
     * Replace upcoming (not current) MediaItems with freshly built stream URLs.
     * Current item is left alone to avoid interrupting playback.
     */
    private fun refreshUpcomingStreamUris() {
        val c = controller ?: return
        val st = _state.value
        if (st.queue.isEmpty() || client.credentials == null) return
        if (c.mediaItemCount == 0) return
        val current = c.currentMediaItemIndex.coerceAtLeast(0)
        val first = current + 1
        val end = minOf(st.queue.size, c.mediaItemCount)
        if (first < end) {
            val upcoming = (first until end).map { st.queue[it].toMediaItem() }
            runCatching { c.replaceMediaItems(first, end, upcoming) }
        }
    }

    private fun Song.toMediaItem(): MediaItem {
        val art = CoverArtContentProvider.contentUri(
            coverArt, size = 800,
            accountScope = client.credentials?.let { CoverArtContentProvider.accountScope(it, client.artworkNamespace) },
        )
        val localUri = downloadStore?.let { store ->
            val creds = client.credentials ?: return@let null
            store.playbackUri(store.serverKey(creds), id)
        }
        val extras = android.os.Bundle().apply {
            putString("app_name", "Sonveil")
            putString("com.android.music.musicsource", "Sonveil")
            replayGain?.trackGain?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_TRACK, it) }
            replayGain?.albumGain?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_ALBUM, it) }
            replayGain?.trackPeak?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_TRACK_PEAK, it) }
            replayGain?.albumPeak?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_ALBUM_PEAK, it) }
            replayGain?.fallbackGain?.takeIf { it.isFinite() }?.let { putFloat(PlayerSettings.EXTRA_RG_FALLBACK, it) }
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(localUri ?: PlaybackStreamUri.build(id, transcodeBitrate, java.util.UUID.randomUUID().toString()))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setSubtitle(artist)
                    .setDescription("Sonveil")
                    .setWriter("Sonveil")
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setArtworkUri(art)
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }
}
