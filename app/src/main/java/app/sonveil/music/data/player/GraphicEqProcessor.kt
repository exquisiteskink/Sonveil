package app.sonveil.music.data.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * In-player tone control: 10 peaking bands or AutoEQ parametric filters, plus preamp.
 * Unsupported PCM encodings deactivate this processor instead of failing the sink.
 */
@UnstableApi
class GraphicEqProcessor : BaseAudioProcessor() {
    private class RenderState(
        val enabled: Boolean,
        val preamp: Float,
        val filters: Array<EqBiquad>,
        val channels: Int,
    )

    @Volatile
    private var render = RenderState(false, 1f, emptyArray(), 2)

    private var spec: List<EqFilter> = emptyList()
    private var preampLinear = 1f
    private var active = false
    private var channels = 2
    private var floatPcm = false
    private var sampleRate = 44100

    @Synchronized
    fun setProgram(program: EqProgram) {
        spec = program.filters
        preampLinear = if (program.active) program.preampLinear else 1f
        active = program.active && (abs(program.preampDb) > 0.05f || program.filters.any { it.gainDb != 0f })
        rebuild()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        floatPcm = enc == C.ENCODING_PCM_FLOAT
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        sampleRate = inputAudioFormat.sampleRate
        rebuild()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // Media3 polls with its shared empty buffer. Copying that buffer into
        // itself throws "The source buffer is this buffer" and stops playback.
        if (!inputBuffer.hasRemaining()) return
        val raw = ByteArray(inputBuffer.remaining())
        inputBuffer.get(raw)
        val out = replaceOutputBuffer(raw.size)
        if (!render.enabled) {
            out.put(raw)
            out.flip()
            return
        }
        val src = ByteBuffer.wrap(raw).order(java.nio.ByteOrder.nativeOrder())
        if (floatPcm) processFloat(src, out) else process16(src, out)
    }

    private fun process16(input: ByteBuffer, out: ByteBuffer) {
        val state = render
        val ch = state.channels
        while (input.remaining() >= 2 * ch) {
            for (c in 0 until ch) {
                val x = apply(input.short / 32768f, c)
                out.putShort((x * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        out.flip()
    }

    private fun processFloat(input: ByteBuffer, out: ByteBuffer) {
        val state = render
        val ch = state.channels
        while (input.remaining() >= 4 * ch) {
            for (c in 0 until ch) {
                out.putFloat(apply(input.float, c))
            }
        }
        out.flip()
    }

    private fun apply(sample: Float, channel: Int): Float {
        val state = render
        var x = sample * state.preamp
        val f = state.filters
        var i = channel
        val step = state.channels
        while (i < f.size) {
            x = f[i].process(x)
            i += step
        }
        return x
    }

    @Synchronized
    private fun rebuild() {
        if (channels <= 0 || sampleRate <= 0) return
        val built = Array(spec.size * channels) { EqBiquad() }
        spec.forEachIndexed { band, filter ->
            for (c in 0 until channels) {
                val biquad = built[band * channels + c]
                when (filter.type) {
                    "LS" -> biquad.lowShelf(sampleRate, filter.fc, filter.q, filter.gainDb)
                    "HS" -> biquad.highShelf(sampleRate, filter.fc, filter.q, filter.gainDb)
                    else -> biquad.peaking(sampleRate, filter.fc, filter.q, filter.gainDb)
                }
            }
        }
        render = RenderState(active, preampLinear, built, channels)
    }

    override fun onFlush() {
        render.filters.forEach { it.reset() }
    }

    override fun onReset() {
        render.filters.forEach { it.reset() }
    }
}
