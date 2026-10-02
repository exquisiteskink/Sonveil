package app.sonveil.music.data.art

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test

class CoverArtCacheTest {
    private fun image(body: String) = MockResponse().setHeader("Content-Type", "image/jpeg").setBody(body)

    private fun withCache(block: (MockWebServer, File, CoverArtCache) -> Unit) {
        val directory = Files.createTempDirectory("sonveil-art-test").toFile()
        try {
            MockWebServer().use { server ->
                server.start()
                block(server, directory, CoverArtCache(directory, OkHttpClient()))
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun sameCoverIdIsIsolatedByServerAccountAndSize() = withCache { server, directory, cache ->
        val base = server.url("/rest/getCoverArt?id=shared").toString()
        val urls = listOf(
            "$base&u=alice&size=400", "$base&u=bob&size=400",
            "$base&u=alice&size=800", server.url("/other/rest/getCoverArt?id=shared&u=alice&size=400").toString(),
        )
        urls.forEachIndexed { index, url ->
            server.enqueue(image("art-$index"))
            assertEquals("art-$index", cache.get(url).readText())
        }
        urls.forEachIndexed { index, url -> assertEquals("art-$index", cache.get(url).readText()) }
        assertEquals(4, server.requestCount)
        assertEquals(4, directory.listFiles()!!.size)
        assertTrue(directory.listFiles()!!.all { it.name.matches(Regex("[a-f0-9]{64}\\.img")) })
    }

    @Test fun concurrentRequestsPublishOneCompleteFile() = withCache { server, _, cache ->
        val body = "image-data".repeat(20_000)
        server.enqueue(image(body))
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = executor.invokeAll(List(4) { Callable { cache.get(server.url("/cover").toString()).readText() } })
            results.forEach { assertEquals(body, it.get()) }
            assertEquals(1, server.requestCount)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun unrelatedCoversDownloadInParallel() = withCache { server, _, cache ->
        val arrived = CountDownLatch(2)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                arrived.countDown()
                return if (arrived.await(3, TimeUnit.SECONDS)) image("art")
                else MockResponse().setResponseCode(500)
            }
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = executor.invokeAll(listOf("/a", "/b").map { path ->
                Callable { cache.get(server.url(path).toString()).readText() }
            })
            results.forEach { assertEquals("art", it.get()) }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun openedFileRemainsReadableAfterEviction() = withCache { server, directory, _ ->
        val cache = CoverArtCache(directory, OkHttpClient(), maxImageBytes = 10, maxCacheBytes = 10)
        server.enqueue(image("a".repeat(10)))
        cache.withFile(server.url("/a").toString()) { it.inputStream() }.use { input ->
            server.enqueue(image("b".repeat(10)))
            assertEquals("b".repeat(10), cache.get(server.url("/b").toString()).readText())
            assertEquals(1, directory.listFiles()!!.size)
            assertEquals("a".repeat(10), input.reader().readText())
        }
    }

    @Test fun failuresLeaveNoCacheEntryAndNextRequestCanRecover() = withCache { server, directory, cache ->
        val url = server.url("/cover").toString()
        for (response in listOf(MockResponse().setResponseCode(500), image(""),
            MockResponse().setHeader("Content-Type", "text/html").setBody("sign in"))) {
            server.enqueue(response)
            assertThrows(IOException::class.java) { cache.get(url) }
            assertTrue(directory.listFiles()!!.isEmpty())
        }
        server.enqueue(image("recovered"))
        assertEquals("recovered", cache.get(url).readText())
    }

    @Test fun oversizedKnownAndChunkedBodiesAreNotCached() = withCache { server, directory, _ ->
        val cache = CoverArtCache(directory, OkHttpClient(), maxImageBytes = 10)
        val url = server.url("/cover").toString()
        server.enqueue(image("x".repeat(11)))
        assertThrows(IOException::class.java) { cache.get(url) }
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setChunkedBody("x".repeat(11), 3))
        assertThrows(IOException::class.java) { cache.get(url) }
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun evictionKeepsMostRecentlyUsedArt() = withCache { server, directory, _ ->
        val cache = CoverArtCache(directory, OkHttpClient(), maxImageBytes = 10, maxCacheBytes = 20)
        val a = server.url("/a").toString()
        val b = server.url("/b").toString()
        val c = server.url("/c").toString()
        server.enqueue(image("a".repeat(10)))
        val aFile = cache.get(a)
        server.enqueue(image("b".repeat(10)))
        val bFile = cache.get(b)
        aFile.setLastModified(1)
        bFile.setLastModified(2)
        cache.get(a)
        server.enqueue(image("c".repeat(10)))
        assertEquals("c".repeat(10), cache.get(c).readText())
        assertTrue(aFile.exists())
        assertFalse(bFile.exists())
        assertEquals(20L, directory.listFiles()!!.sumOf { it.length() })
    }
}
