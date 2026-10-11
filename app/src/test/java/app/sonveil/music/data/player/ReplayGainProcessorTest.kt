package app.sonveil.music.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun albumModeUsesAlbumGainAndPeak() {
        val gain = ReplayGainProcessor.computeLinearGain(ReplayGainMode.Album, -12f, -6f,
            Float.NaN, Float.NaN, Float.NaN, false)
        assertEquals(0.5012f, gain, 0.001f)
        val limited = ReplayGainProcessor.computeLinearGain(ReplayGainMode.Album, -12f, 6f,
            0.25f, 1.5f, Float.NaN, true)
        assertEquals(0.99f / 1.5f, limited, 0.001f)
    }

    @Test
    fun untaggedTrackUsesUnityAndMissingTrackGainFallsBackToAlbum() {
        assertEquals(1f, ReplayGainProcessor.computeLinearGain(ReplayGainMode.Track,
            Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, true), 0f)
        assertEquals(0.5012f, ReplayGainProcessor.computeLinearGain(ReplayGainMode.Track,
            Float.NaN, -6f, Float.NaN, Float.NaN, Float.NaN, false), 0.001f)
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

    @Test
    fun limiterCeilingIsFormatIndependent_pcm16AndFloatUseSameGain() {
        // The limiter is applied at the gain level (min(gain, 0.99/peak)), so PCM16 and float
        // receive the identical limited gain; process16's integer clamp only guards overflow.
        val gain = ReplayGainProcessor.computeLinearGain(
            mode = ReplayGainMode.Track,
            trackDb = 6f,
            albumDb = Float.NaN,
            trackPeak = 1.5f,
            albumPeak = Float.NaN,
            fallbackDb = Float.NaN,
            limiter = true,
        )
        assertEquals(0.99f / 1.5f, gain, 0.001f)
        // A peak-1.5 sample at this gain stays under full scale -> no clip on either path.
        assertTrue(1.5f * gain <= 0.99f + 1e-4f)
    }
}
