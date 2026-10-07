package app.sonveil.music.data.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.pow

@UnstableApi
class ReplayGainProcessor : BaseAudioProcessor() {
    @Volatile
    var linearGain: Float = 1f

    @Volatile
    var limiter: Boolean = true

    private var floatPcm = false
    private var channels = 2

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        floatPcm = enc == C.ENCODING_PCM_FLOAT
        channels = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val gain = linearGain
        val limit = limiter
        if (!inputBuffer.hasRemaining()) return
        // Media3 delivers native-order PCM. ByteBuffer defaults to big endian, and
        // reading samples that way byte-swaps the frame into metallic/robotic audio.
        inputBuffer.order(ByteOrder.nativeOrder())
        if (gain == 1f) {
            val out = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.nativeOrder())
            out.put(inputBuffer)
            out.flip()
            return
        }
        if (floatPcm) processFloat(inputBuffer, gain, limit) else process16(inputBuffer, gain, limit)
    }

    private fun process16(input: ByteBuffer, gain: Float, limit: Boolean) {
        val out = replaceOutputBuffer(input.remaining()).order(ByteOrder.nativeOrder())
        while (input.remaining() >= 2) {
            val sample = input.short.toFloat() * gain
            out.putShort(sample.toInt().coerceIn(-32768, 32767).toShort())
        }
        out.flip()
    }

    private fun processFloat(input: ByteBuffer, gain: Float, limit: Boolean) {
        val out = replaceOutputBuffer(input.remaining()).order(ByteOrder.nativeOrder())
        while (input.remaining() >= 4) {
            var x = input.float * gain
            if (limit) x = x.coerceIn(-1f, 1f)
            out.putFloat(x)
        }
        out.flip()
    }

    companion object {
        /** ReplayGain peak is a linear ratio. Values above this are not peaks (sample counts, percents). */
        const val MAX_LINEAR_PEAK = 4f

        fun computeLinearGain(
            mode: ReplayGainMode,
            trackDb: Float,
            albumDb: Float,
            trackPeak: Float,
            albumPeak: Float,
            fallbackDb: Float,
            limiter: Boolean,
        ): Float {
            if (mode == ReplayGainMode.Off) return 1f
            val db = when (mode) {
                ReplayGainMode.Track -> firstFinite(trackDb, fallbackDb, albumDb)
                ReplayGainMode.Album -> firstFinite(albumDb, fallbackDb, trackDb)
                ReplayGainMode.Off -> 0f
            }
            var linear = 10.0.pow(db / 20.0).toFloat()
            if (limiter) {
                val peak = when (mode) {
                    ReplayGainMode.Track -> firstLinearPeak(trackPeak, albumPeak)
                    ReplayGainMode.Album -> firstLinearPeak(albumPeak, trackPeak)
                    ReplayGainMode.Off -> 0f
                }
                if (peak > 0f) {
                    val ceiling = 0.99f / peak
                    linear = min(linear, ceiling)
                }
            }
            return linear.coerceIn(0.05f, 4f)
        }

        private fun firstFinite(vararg values: Float): Float {
            for (v in values) if (v.isFinite()) return v
            return 0f
        }

        private fun firstLinearPeak(vararg values: Float): Float {
            for (v in values) if (v.isFinite() && v > 0f && v <= MAX_LINEAR_PEAK) return v
            return 0f
        }

        fun fromExtras(
            extras: android.os.Bundle?,
            mode: ReplayGainMode,
            limiter: Boolean,
        ): Float {
            if (extras == null) return 1f
            return computeLinearGain(
                mode = mode,
                trackDb = extras.getFloat(PlayerSettings.EXTRA_RG_TRACK, Float.NaN),
                albumDb = extras.getFloat(PlayerSettings.EXTRA_RG_ALBUM, Float.NaN),
                trackPeak = extras.getFloat(PlayerSettings.EXTRA_RG_TRACK_PEAK, Float.NaN),
                albumPeak = extras.getFloat(PlayerSettings.EXTRA_RG_ALBUM_PEAK, Float.NaN),
                fallbackDb = extras.getFloat(PlayerSettings.EXTRA_RG_FALLBACK, Float.NaN),
                limiter = limiter,
            )
        }
    }
}
