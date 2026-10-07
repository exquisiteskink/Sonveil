package app.sonveil.music.data.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class ReplayGainPcmTest {
    private fun process(gain: Float, samples: ShortArray): ShortArray {
        val processor = ReplayGainProcessor().apply { linearGain = gain; limiter = true }
        processor.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        // Deliberately leave the buffer's default byte order: PCM bytes are native order.
        val input = ByteBuffer.allocateDirect(samples.size * 2)
        input.order(ByteOrder.nativeOrder())
        samples.forEach { input.putShort(it) }
        input.flip()
        input.order(ByteOrder.BIG_ENDIAN)
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        return ShortArray(output.remaining() / 2) { output.short }
    }

    @Test fun nativePcmGainChangesLevelWithoutByteSwapping() {
        assertArrayEquals(shortArrayOf(5000, -5000, 128, -128), process(0.5f, shortArrayOf(10000, -10000, 256, -256)))
    }

    @Test fun unityIsBitExactIncludingFullScale() {
        val samples = shortArrayOf(0, 1, -1, 32767, -32768, 256)
        assertArrayEquals(samples, process(1f, samples))
    }

    @Test fun amplificationClipsWithoutWrapping() {
        assertArrayEquals(shortArrayOf(32767, -32768), process(4f, shortArrayOf(20000, -20000)))
    }
}
