package app.sonveil.music.data.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DvcPcmGateTest {
    private fun samples(vararg values: Short): ByteBuffer =
        ByteBuffer.allocateDirect(values.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach(::putShort)
            flip()
        }

    private fun output(gate: DvcPcmGate, input: ByteBuffer): ShortArray {
        gate.queueInput(input)
        val result = gate.output.order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray(result.remaining() / 2) { result.short }
    }

    @Test fun flushClosesGateBeforeAudioCanReachNewTrack() {
        var flushes = 0
        val gate = DvcPcmGate { flushes++ }
        gate.enable()
        gate.configure(AudioFormat(1_000, 2, C.ENCODING_PCM_16BIT))
        gate.flush()
        assertEquals(1, flushes)
        assertArrayEquals(shortArrayOf(0, 0, 0, 0), output(gate, samples(10_000, -10_000, 8_000, -8_000)))

        gate.open()
        val ramped = output(gate, samples(10_000, -10_000, 8_000, -8_000))
        assertTrue(ramped[0] > 0)
        assertTrue(ramped[0] < 10_000)
        assertTrue(ramped[2] > ramped[0])

        gate.flush()
        assertEquals(2, flushes)
        assertArrayEquals(shortArrayOf(0, 0), output(gate, samples(10_000, -10_000)))
    }

    @Test fun disabledGatePreservesPcm() {
        val gate = DvcPcmGate { error("disabled gate must not report a flush") }
        gate.disable()
        gate.configure(AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        gate.flush()
        assertArrayEquals(shortArrayOf(123, -456, 789, -101), output(gate, samples(123, -456, 789, -101)))
    }

    @Test fun enabledGateAppliesReplayGainWithoutChangingTrackVolume() {
        val gate = DvcPcmGate { }
        gate.enable()
        gate.configure(AudioFormat(1_000, 1, C.ENCODING_PCM_16BIT))
        gate.flush()
        gate.setReplayGain(0.5f)
        gate.open()
        val input = ShortArray(800) { 10_000 }
        val result = output(gate, samples(*input))
        assertTrue(result.first() < 100)
        assertEquals(5_000, result.last().toInt())
    }

    @Test fun warmAnchorKeepsPcmAudibleAcrossSinkFlush() {
        var guardedFlushes = 0
        val gate = DvcPcmGate { guardedFlushes++ }
        gate.enable()
        gate.configure(AudioFormat(1_000, 1, C.ENCODING_PCM_16BIT))
        gate.flush()
        gate.open()
        output(gate, samples(*ShortArray(80) { 1_000 }))
        gate.setKeepOpenOnFlush(true)

        gate.flush()
        assertEquals(1, guardedFlushes)
        assertTrue(!gate.isClosed)
        assertArrayEquals(shortArrayOf(1_000), output(gate, samples(1_000)))
    }

    @Test fun floatFormatDeactivatesInsteadOfFailingTheSink() {
        // A non-PCM16 format must deactivate the gate (NOT_SET) like GraphicEqProcessor, not
        // throw UnhandledAudioFormatException, which Media3 turns into a fatal playback stop.
        val gate = DvcPcmGate { }
        assertEquals(
            AudioFormat.NOT_SET,
            gate.configure(AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT)),
        )
    }
}
