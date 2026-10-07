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

    @Test fun firstBufferedSamplesHaveTrackGainWhileSessionIsStillMuted() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val settings = PlayerSettings(context)
            val previousMode = settings.replayGainMode
            val previousLimiter = settings.peakLimiter
            val exo = androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
            try {
                settings.replayGainMode = ReplayGainMode.Track
                settings.peakLimiter = true
                exo.volume = 0f
                val service = PlaybackService()
                fun setField(name: String, value: Any) {
                    PlaybackService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
                }
                setField("settings", settings)
                setField("sessionBindPending", true)
                val processor = ReplayGainProcessor()
                processor.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
                processor.flush()
                val field = PlaybackService::class.java.getDeclaredField("pcmGains").apply { isAccessible = true }
                @Suppress("UNCHECKED_CAST")
                val gains = field.get(service) as MutableMap<androidx.media3.exoplayer.ExoPlayer, ReplayGainProcessor>
                gains[exo] = processor
                val item = androidx.media3.common.MediaItem.Builder().setMediaId("cold-start")
                    .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setExtras(android.os.Bundle().apply {
                        putFloat(PlayerSettings.EXTRA_RG_TRACK, -6f)
                    }).build()).build()
                val method = PlaybackService::class.java.getDeclaredMethod("applyReplayGain",
                    androidx.media3.exoplayer.ExoPlayer::class.java, androidx.media3.common.MediaItem::class.java, EqController::class.java)
                method.isAccessible = true
                method.invoke(service, exo, item, EqController())
                val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
                input.putShort(10000).putShort(-10000).flip()
                processor.queueInput(input)
                val output = processor.output.order(ByteOrder.nativeOrder())
                assertEquals(5011, output.short.toInt())
                assertEquals(-5011, output.short.toInt())
                assertEquals("PCM gain must not unmute the AudioTrack before settling", 0f, exo.volume, 0f)
            } finally {
                exo.release()
                settings.replayGainMode = previousMode
                settings.peakLimiter = previousLimiter
            }
        }
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
