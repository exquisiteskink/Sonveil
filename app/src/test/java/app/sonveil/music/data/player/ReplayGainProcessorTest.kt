package app.sonveil.music.data.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayGainProcessorTest {
    @Test
    fun trackGain_convertsDbToLinear() {
        val linear = ReplayGainProcessor.computeLinearGain(
            mode = ReplayGainMode.Track,
            trackDb = -6f,
            albumDb = Float.NaN,
            trackPeak = Float.NaN,
            albumPeak = Float.NaN,
            fallbackDb = Float.NaN,
            limiter = false,
        )
        assertEquals(0.5012f, linear, 0.001f)
    }

    @Test
    fun off_isUnity() {
        assertEquals(
            1f,
            ReplayGainProcessor.computeLinearGain(
                ReplayGainMode.Off, -12f, -12f, 1f, 1f, -12f, true,
            ),
            0f,
        )
    }

    @Test
    fun limiter_respectsLinearPeak() {
        val linear = ReplayGainProcessor.computeLinearGain(
            mode = ReplayGainMode.Track,
            trackDb = 6f,
            albumDb = Float.NaN,
            trackPeak = 1.5f,
            albumPeak = Float.NaN,
            fallbackDb = Float.NaN,
            limiter = true,
        )
        assertEquals(0.99f / 1.5f, linear, 0.001f)
    }

    @Test
    fun bogusPeakAboveEight_isIgnored() {
        val withBogusPeak = ReplayGainProcessor.computeLinearGain(
            mode = ReplayGainMode.Track,
            trackDb = -3f,
            albumDb = Float.NaN,
            trackPeak = 98f,
            albumPeak = 32767f,
            fallbackDb = Float.NaN,
            limiter = true,
        )
        val withoutPeak = ReplayGainProcessor.computeLinearGain(
            mode = ReplayGainMode.Track,
            trackDb = -3f,
            albumDb = Float.NaN,
            trackPeak = Float.NaN,
            albumPeak = Float.NaN,
            fallbackDb = Float.NaN,
            limiter = true,
        )
        assertEquals(withoutPeak, withBogusPeak, 0.0001f)
    }
}
