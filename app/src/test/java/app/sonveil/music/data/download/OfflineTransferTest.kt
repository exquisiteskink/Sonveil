package app.sonveil.music.data.download

import app.sonveil.music.data.auth.StoredCredentials
import app.sonveil.music.data.remote.Song
import app.sonveil.music.data.remote.SubsonicClient
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineTransferTest {
    @get:Rule val temp = TemporaryFolder()
    private val http = OkHttpClient()
    private val audio = "ID3" + "a".repeat(1024)

    @Test fun aggregateQuotaRejectsDeclaredAndChunkedBodies(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.start()
            for (chunked in listOf(false, true)) {
                val response = MockResponse().setHeader("Content-Type", "audio/mpeg")
                if (chunked) response.setChunkedBody(audio, 128) else response.setBody(audio)
                server.enqueue(response)
                val target = temp.newFolder().resolve("quota.mp3")
                try {
                    OfflineTransfer.download(http.newCall(Request.Builder().url(server.url("/")).build()),
                        target, 0, storageBudget = 512) { fail("Over-quota download committed") }
                    fail("Over-quota download accepted")
                } catch (_: IOException) { }
                assertFalse(target.exists())
                assertEquals(0, target.parentFile!!.listFiles()!!.size)
            }
        }
    }

    @Test fun oversizedDeclaredAndChunkedBodiesAreNeverPublished(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.start()
            for (chunked in listOf(false, true)) {
                val response = MockResponse().setHeader("Content-Type", "audio/mpeg")
                if (chunked) response.setChunkedBody(audio, 128) else response.setBody(audio)
                server.enqueue(response)
                val target = temp.newFolder().resolve("oversized.mp3")
                try {
                    OfflineTransfer.download(
                        http.newCall(Request.Builder().url(server.url("/")).build()), target, 0,
                        limits = OfflineTransfer.Limits(maxBytes = 512, minFreeBytes = 0),
                    ) { fail("Oversized download committed") }
                    fail("Oversized download accepted")
                } catch (_: IOException) { }
                assertFalse(target.exists())
                assertEquals(0, target.parentFile!!.listFiles()!!.size)
            }
        }
    }

    @Test fun storageReserveStopsTransferAndRemovesTemporaryFile(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "audio/mpeg").setBody(audio))
            server.start()
            val target = temp.newFolder().resolve("reserve.mp3")
            try {
                OfflineTransfer.download(
                    http.newCall(Request.Builder().url(server.url("/")).build()), target, 0,
                    limits = OfflineTransfer.Limits(minFreeBytes = Long.MAX_VALUE - 65536),
                ) { fail("Download crossed storage reserve") }
                fail("Download ignored storage reserve")
            } catch (_: IOException) { }
            assertFalse(target.exists())
            assertEquals(0, target.parentFile!!.listFiles()!!.size)
        }
    }

    @Test fun overallDeadlineStopsBlockedTransferAndCleansUp(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()
            val target = temp.newFolder().resolve("timeout.mp3")
            try {
                withTimeout(5000) {
                    OfflineTransfer.download(
                        http.newCall(Request.Builder().url(server.url("/")).build()), target, 0,
                        limits = OfflineTransfer.Limits(timeoutMillis = 250),
                    ) { fail("Timed-out transfer committed") }
                }
                fail("Transfer ignored deadline")
            } catch (_: IOException) { }
            assertFalse(target.exists())
            assertEquals(0, target.parentFile!!.listFiles()!!.size)
        }
    }

    @Test fun clearWaitsForBlockedTransferAndNothingReappears() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()
            val root = temp.newFolder()
            val store = DownloadStore(root)
            val key = store.serverKey(StoredCredentials(server.url("/").toString(), "u"))
            val song = Song("1", suffix = "mp3")
            val work = DownloadWorkQueue(this)
            work.replace {
                OfflineTransfer.download(http.newCall(Request.Builder().url(server.url("/download")).build()), store.targetFile(key, song), 0) {
                    store.markDownloaded(key, song, it)
                }
            }.join()
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
            withTimeout(5000) { work.stop { store.clearAll(key) }.join() }
            assertTrue(store.availableSongs(key).isEmpty())
            assertTrue(root.walkTopDown().none { it.extension == "part" || it.extension == "mp3" })
        }
    }

    @Test fun replacementJoinsCancelledTransferBeforePublishingNewFile() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "audio/mpeg").setBody(audio)
                .throttleBody(512, 2, TimeUnit.SECONDS))
            server.enqueue(MockResponse().setHeader("Content-Type", "audio/mpeg").setBody(audio))
            server.start()
            val target = temp.newFolder().resolve("song.mp3")
            val work = DownloadWorkQueue(this)
            val committed = CompletableDeferred<Unit>()
            fun call() = http.newCall(Request.Builder().url(server.url("/download")).build())
            work.replace { OfflineTransfer.download(call(), target, 0) { error("Cancelled transfer committed") } }.join()
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
            withTimeout(5000) {
                while (target.parentFile!!.listFiles()!!.none { it.extension == "part" && it.length() > 0 }) delay(10)
            }
            work.replace { OfflineTransfer.download(call(), target, audio.length.toLong()) { committed.complete(Unit) } }.join()
            withTimeout(5000) { committed.await() }
            work.stop().join()
            assertEquals(audio, target.readText())
            assertTrue(target.parentFile!!.listFiles()!!.none { it.extension == "part" })
        }
    }

    @Test fun rejectsErrorDocumentsAndShortOriginalFiles() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val cases = listOf(
                "text/xml" to "<subsonic-response status='failed'/>",
                "audio/mpeg" to "<html>Proxy error</html>",
                "application/json" to "{\"error\":1}",
                "application/octet-stream" to "not audio",
            )
            for ((type, body) in cases) {
                server.enqueue(MockResponse().setHeader("Content-Type", type).setBody(body))
                val target = temp.newFolder().resolve("track.mp3")
                try {
                    OfflineTransfer.download(http.newCall(Request.Builder().url(server.url("/")).build()), target, 9999) {
                        fail("Invalid file must not be indexed")
                    }
                    fail("Invalid download accepted")
                } catch (_: IOException) { }
                assertFalse(target.exists())
                assertEquals(0, target.parentFile!!.listFiles()!!.size)
            }
            // No Content-Length: metadata size must still catch a mismatched body.
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "audio/mpeg")
                    .setChunkedBody(audio, 128),
            )
            val shortTarget = temp.newFolder().resolve("short.mp3")
            try {
                OfflineTransfer.download(
                    http.newCall(Request.Builder().url(server.url("/")).build()),
                    shortTarget,
                    9999,
                ) { fail("Mismatched metadata size must not be indexed") }
                fail("Short download accepted")
            } catch (_: IOException) { }
            assertFalse(shortTarget.exists())
        }
    }

    @Test fun acceptsAudioWhenContentLengthMatchesDespiteWrongMetadataSize() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "audio/mpeg")
                    .setBody(audio),
            )
            server.start()
            val target = temp.newFolder().resolve("ok.mp3")
            val committed = CompletableDeferred<Unit>()
            OfflineTransfer.download(
                http.newCall(Request.Builder().url(server.url("/")).build()),
                target,
                expectedSize = 9999,
            ) { committed.complete(Unit) }
            withTimeout(5000) { committed.await() }
            assertEquals(audio, target.readText())
        }
    }

    @Test fun downloadUrlUsesCapturedCredentialsAfterAccountSwitch() {
        val client = SubsonicClient()
        val original = StoredCredentials("https://first.example", "first", "password")
        client.credentials = StoredCredentials("https://second.example", "second", "other")
        val request = Request.Builder().url(client.downloadUrl("song", original)).build()
        assertEquals("first.example", request.url.host)
        assertEquals("first", request.url.queryParameter("u"))
    }
}
