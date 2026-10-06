package app.sonveil.music.data.player

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.log10

/**
 * 10-band EQ attached to an ExoPlayer audio session.
 * Uses DynamicsProcessing on API 28+; otherwise the platform Equalizer.
 *
 * ReplayGain is applied in [ReplayGainProcessor], not here. Enabling
 * DynamicsProcessing (and its pre-EQ stage) for gain tags sounds metallic or
 * robotic on some devices. [applyReplayGainLinear] only disables a previously
 * attached platform gain so AudioTrack volume can stay at 1f for DVC.
 */
class EqController {
    private var dynamics: DynamicsProcessing? = null
    private var equalizer: Equalizer? = null
    private var sessionId = 0
    /** Last ReplayGain applied via platform effect, in dB (0 = unity). */
    private var replayGainDb = 0f

    fun attach(audioSessionId: Int, settings: PlayerSettings) {
        if (audioSessionId == 0 || audioSessionId == sessionId) {
            apply(settings)
            return
        }
        release()
        sessionId = audioSessionId
        if (Build.VERSION.SDK_INT >= 28) {
            dynamics = runCatching {
                val cfg = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    true,
                    10,
                    false,
                    0,
                    false,
                    0,
                    false,
                ).build()
                DynamicsProcessing(0, audioSessionId, cfg).apply {
                    enabled = false
                }
            }.getOrNull()
        }
        if (dynamics == null) {
            equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        }
        apply(settings)
    }

    /**
     * Park platform ReplayGain at unity and disable DynamicsProcessing.
     * Gain itself is applied in decoded PCM. Returns true so callers keep player volume at 1f.
     */
    fun applyReplayGainLinear(@Suppress("UNUSED_PARAMETER") linear: Float, settings: PlayerSettings): Boolean {
        // Platform input gain is what made tagged tracks robotic. Ignore the requested gain.
        settings.replayGainMode
        replayGainDb = 0f
        val dp = dynamics
        if (dp != null && Build.VERSION.SDK_INT >= 28) {
            val ok = runCatching {
                applyDynamics(dp)
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    /**
     * Session effects carry no ReplayGain and no tone. Tone (10-band or parametric,
     * plus preamp) is applied in [GraphicEqProcessor] so it does not fight Poweramp.
     */
    fun apply(settings: PlayerSettings) {
        dynamics?.let { dp ->
            if (Build.VERSION.SDK_INT >= 28) applyDynamics(dp)
            return
        }
        equalizer?.let { eq -> runCatching { eq.enabled = false } }
    }

    @RequiresApi(28)
    private fun applyDynamics(dp: DynamicsProcessing) {
        runCatching {
            dp.enabled = false
            dp.setInputGainAllChannelsTo(0f)
        }
    }

    fun release() {
        runCatching { dynamics?.release() }
        runCatching { equalizer?.release() }
        dynamics = null
        equalizer = null
        sessionId = 0
        // Keep replayGainDb so a re-attach restores the same gain.
    }

    /**
     * Steal platform effects from [other] without releasing them.
     * Used when promoting the crossfade player so an already-open session effect
     * stays attached to the audible AudioTrack.
     */
    fun adoptFrom(other: EqController) {
        if (other === this) return
        release()
        dynamics = other.dynamics
        equalizer = other.equalizer
        sessionId = other.sessionId
        replayGainDb = other.replayGainDb
        other.dynamics = null
        other.equalizer = null
        other.sessionId = 0
    }

    val audioSessionId: Int get() = sessionId

    companion object {
        private const val RG_DB_MIN = -30f
        private const val RG_DB_MAX = 12f

        fun linearToDb(linear: Float): Float {
            if (!linear.isFinite() || linear <= 0f) return RG_DB_MIN
            return (20.0 * log10(linear.toDouble())).toFloat().coerceIn(RG_DB_MIN, RG_DB_MAX)
        }
    }
}

fun replayGainToPlayerVolume(linear: Float): Float = linear.coerceIn(0.05f, 1f)
