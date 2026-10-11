package app.sonveil.music.data.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Keeps decoded samples silent while an external EQ may be binding to a new
 * AudioTrack. This runs on the playback thread, before samples reach AudioTrack,
 * so a temporarily ineffective AudioTrack volume cannot expose full-scale PCM.
 */
@UnstableApi
internal class DvcPcmGate(private val onFlushWhileEnabled: (DvcPcmGate) -> Unit) : BaseAudioProcessor() {
    @Volatile private var enabled = false
    @Volatile private var closed = true
    @Volatile private var replayGain = 1f
    @Volatile var keepOpenOnFlush = false
        private set
    private var gain = 0f // playback thread only

    fun enable() {
        enabled = true
        close()
    }

    fun disable() {
        enabled = false
        closed = false
        keepOpenOnFlush = false
    }

    fun setKeepOpenOnFlush(keepOpen: Boolean) {
        keepOpenOnFlush = keepOpen
    }

    fun close() {
        closed = true
    }

    fun open() {
        closed = false
    }

    fun setReplayGain(linear: Float) {
        replayGain = linear.coerceIn(0f, 4f)
    }

    val isClosed: Boolean get() = enabled && closed

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // Deactivate (NOT_SET) on any non-PCM16 format rather than throwing: an
        // UnhandledAudioFormatException becomes a fatal ConfigurationException that stops
        // playback, whereas NOT_SET lets Media3 drop this processor and keep audio flowing.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        if (enabled && keepOpenOnFlush && !closed) {
            gain = 1f
            return
        }
        gain = 0f
        if (enabled) {
            close()
            onFlushWhileEnabled(this)
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val output = replaceOutputBuffer(bytes).order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        if (!enabled) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        // PCM16 is frame-aligned at this point in DefaultAudioSink's chain.
        val frameBytes = inputAudioFormat.bytesPerFrame
        val frameCount = bytes / frameBytes
        val samplesPerFrame = inputAudioFormat.channelCount
        val rampFrames = (inputAudioFormat.sampleRate * RAMP_MS / 1000).coerceAtLeast(1)
        for (frame in 0 until frameCount) {
            gain = when {
                closed -> 0f
                gain >= 1f -> 1f
                else -> (gain + 1f / rampFrames).coerceAtMost(1f)
            }
            repeat(samplesPerFrame) {
                val sample = inputBuffer.short.toInt()
                output.putShort((sample * gain * replayGain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
        }
        // AudioProcessor input is frame-aligned. Preserve any unexpected tail as
        // silence rather than allowing unguarded samples through.
        while (inputBuffer.hasRemaining()) {
            inputBuffer.get()
            output.put(0)
        }
        output.flip()
    }

    companion object {
        private const val RAMP_MS = 80
    }
}
