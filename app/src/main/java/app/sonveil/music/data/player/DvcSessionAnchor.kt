package app.sonveil.music.data.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log

/** Keeps an external session effect active with silent PCM across audible-track pauses. */
internal class DvcSessionAnchor(private val sessionId: Int) {
    @Volatile private var running = false
    @Volatile private var track: AudioTrack? = null
    private var writer: Thread? = null
    private var failed = false
    private var startedAtMs = 0L

    val isWarm: Boolean
        get() = track != null && running &&
            SystemClock.elapsedRealtime() - startedAtMs >= WARM_UP_MS

    fun start() {
        if (sessionId <= 0 || failed) return
        if (track != null) {
            if (running) return
            stop()
        }
        var created: AudioTrack? = null
        try {
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val minBytes = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            check(minBytes > 0)
            created = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBytes, BUFFER_BYTES))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setSessionId(sessionId)
                .build()
            check(created.state == AudioTrack.STATE_INITIALIZED) {
                "AudioTrack state=${created.state}"
            }
            val output = created
            val silence = ByteArray(CHUNK_BYTES)
            running = true
            track = output
            output.play()
            startedAtMs = SystemClock.elapsedRealtime()
            writer = Thread({
                while (running) {
                    val written = try {
                        output.write(silence, 0, silence.size, AudioTrack.WRITE_NON_BLOCKING)
                    } catch (_: Exception) {
                        break
                    }
                    if (written < 0) break
                    try {
                        Thread.sleep(10)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
                if (track === output) running = false
            }, "Sonveil-DVC-anchor").also { it.start() }
            Log.i(TAG, "silent anchor started session=$sessionId")
        } catch (e: Exception) {
            running = false
            track = null
            created?.release()
            failed = true
            Log.w(TAG, "silent anchor failed session=$sessionId", e)
        }
    }

    fun stop() {
        val previous = track ?: return
        track = null
        running = false
        startedAtMs = 0L
        writer?.interrupt()
        runCatching { writer?.join(100) }
        writer = null
        runCatching { previous.pause() }
        previous.release()
        Log.i(TAG, "silent anchor stopped session=$sessionId")
    }

    companion object {
        private const val TAG = "Sonveil/DvcAnchor"
        private const val SAMPLE_RATE = 48_000
        private const val BYTES_PER_FRAME = 4
        private const val BUFFER_BYTES = SAMPLE_RATE / 4 * BYTES_PER_FRAME
        private const val CHUNK_BYTES = SAMPLE_RATE / 50 * BYTES_PER_FRAME
        private const val WARM_UP_MS = 500L
    }
}
