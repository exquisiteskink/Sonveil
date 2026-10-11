package app.sonveil.music.data.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import app.sonveil.music.data.auth.StoredCredentials
import app.sonveil.music.data.remote.SubsonicClient

class PlaybackCacheTest {
    @Test fun productionCacheReusesNonceAndKeepsRedirectsOpaqueAndAccountsSeparate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = SubsonicClient()
        client.credentials = StoredCredentials("https://music.test", username = "first", password = "password")
        val bytes = ByteArray(4096) { (it % 251).toByte() }
        var opens = 0
        var offline = false
        var requestedUser: String? = null
        val fakeHttp = DataSource.Factory {
            val delegate = ByteArrayDataSource(bytes)
            object : DataSource {
                override fun open(dataSpec: DataSpec): Long {
                    if (offline) throw IOException("No network")
                    opens++
                    requestedUser = dataSpec.uri.getQueryParameter("u")
                    // Simulate switching accounts between the cache and HTTP open.
                    if (opens == 1) client.credentials = client.credentials!!.copy(username = "second")
                    return delegate.open(dataSpec)
                }
                override fun read(buffer: ByteArray, offset: Int, length: Int) = delegate.read(buffer, offset, length)
                override fun getUri(): Uri = Uri.parse("https://cdn.test/audio?signature=secret")
                override fun addTransferListener(listener: androidx.media3.datasource.TransferListener) = delegate.addTransferListener(listener)
                override fun close() = delegate.close()
            }
        }
        val cache = PlaybackCache(context, client, fakeHttp)
        try {
            val songId = "cache-regression-${System.nanoTime()}"
            val source = cache.dataSourceFactory.createDataSource()
            val spec = DataSpec.Builder().setUri(PlaybackStreamUri.build(songId, 320, "first-nonce")).build()
            assertArrayEquals(bytes, read(source, spec) {
                assertEquals("sonveil", source.uri!!.scheme)
            })
            assertEquals("first", requestedUser)
            // The second account must perform its own network read for this song.
            assertArrayEquals(bytes, read(cache.dataSourceFactory.createDataSource(), spec))
            assertEquals("second", requestedUser)
            assertEquals(2, opens)
            offline = true
            val replaySpec = spec.buildUpon().setUri(PlaybackStreamUri.build(songId, 320, "other-nonce")).build()
            assertArrayEquals(bytes, read(cache.dataSourceFactory.createDataSource(), replaySpec))
            assertArrayEquals(bytes.copyOfRange(1000, 1600), read(cache.dataSourceFactory.createDataSource(),
                replaySpec.buildUpon().setPosition(1000).setLength(600).build()))
            assertEquals(2, opens)
            client.credentials = client.credentials!!.copy(username = "first")
            assertArrayEquals(bytes, read(cache.dataSourceFactory.createDataSource(), replaySpec))
            assertEquals(2, opens)
        } finally {
            cache.release()
        }
    }

    @Test fun replaysCachedBytesAndSeeksWhenNetworkIsUnavailable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "playback-test-${System.nanoTime()}")
        val cache = SimpleCache(directory, LeastRecentlyUsedCacheEvictor(1024 * 1024), StandaloneDatabaseProvider(context))
        try {
            val bytes = ByteArray(4096) { (it % 251).toByte() }
            val key = PlaybackCacheKey.build("server", "user", "song", 0)
            val spec = DataSpec.Builder().setUri(Uri.parse("https://server/stream?salt=one")).setKey(key).build()
            val online = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory { ByteArrayDataSource(bytes) }
            assertArrayEquals(bytes, read(online.createDataSource(), spec))
            var networkOpened = false
            val offline = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory {
                object : DataSource {
                    override fun open(dataSpec: DataSpec): Long {
                        networkOpened = true
                        throw IOException("No network")
                    }
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw IOException("No network")
                    override fun getUri(): Uri? = null
                    override fun addTransferListener(listener: androidx.media3.datasource.TransferListener) = Unit
                    override fun close() = Unit
                }
            }
            // A new auth salt must still hit the same key, including arbitrary seeks.
            assertArrayEquals(bytes.copyOfRange(1200, 1800), read(offline.createDataSource(), spec.buildUpon()
                .setUri(Uri.parse("https://server/stream?salt=two")).setPosition(1200).setLength(600).build()))
            assertEquals(false, networkOpened)
        } finally {
            cache.release()
            directory.deleteRecursively()
        }
    }

    @Test fun leastRecentlyUsedSongsAreEvictedAtTheLimit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "playback-test-${System.nanoTime()}")
        val cache = SimpleCache(directory, LeastRecentlyUsedCacheEvictor(4096), StandaloneDatabaseProvider(context))
        try {
            val factory = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory { ByteArrayDataSource(ByteArray(3072)) }
            read(factory.createDataSource(), DataSpec.Builder().setUri("https://server/first").setKey("first").build())
            read(factory.createDataSource(), DataSpec.Builder().setUri("https://server/second").setKey("second").build())
            assertTrue(cache.cacheSpace <= 4096)
            assertTrue(cache.getCachedSpans("first").isEmpty())
            assertTrue(cache.getCachedSpans("second").isNotEmpty())
        } finally {
            cache.release()
            directory.deleteRecursively()
        }
    }

    private fun read(source: DataSource, spec: DataSpec, onOpen: () -> Unit = {}): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        try {
            source.open(spec)
            onOpen()
            val buffer = ByteArray(1024)
            while (true) {
                val count = source.read(buffer, 0, buffer.size)
                if (count == C.RESULT_END_OF_INPUT) break
                output.write(buffer, 0, count)
            }
        } finally {
            source.close()
        }
        return output.toByteArray()
    }
}
