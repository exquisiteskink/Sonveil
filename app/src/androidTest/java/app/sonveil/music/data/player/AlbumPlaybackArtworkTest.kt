package app.sonveil.music.data.player

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sonveil.music.AuralisApp
import app.sonveil.music.data.art.CoverArtContentProvider
import app.sonveil.music.data.auth.StoredCredentials
import app.sonveil.music.data.player.auto.AutoMediaItemFactory
import app.sonveil.music.data.remote.Song
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlbumPlaybackArtworkTest {
    private fun withServer(failFirstAlbum: Boolean = false, block: (AuralisApp, MockWebServer, AtomicInteger) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuralisApp
        val previous = app.container.client.credentials
        val albumRequests = AtomicInteger()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val bytes = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
        bitmap.recycle()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.requestUrl!!.encodedPath.endsWith("/getAlbum") -> {
                    val attempt = albumRequests.incrementAndGet()
                    if (failFirstAlbum && attempt == 1) MockResponse().setResponseCode(503)
                    else MockResponse().setHeader("Content-Type", "application/json").setBody(
                        """{"subsonic-response":{"status":"ok","version":"1.16.1","album":{"id":"album-id","name":"Album","coverArt":"album-cover","song":[]}}}""",
                    )
                }
                request.requestUrl!!.encodedPath.endsWith("/getCoverArt") &&
                    request.requestUrl!!.queryParameter("id") == "album-cover" ->
                    MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(bytes))
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            app.container.client.credentials = StoredCredentials(
                server.url("/").toString(), "test", "password", allowInsecureLanHttp = true,
            )
            block(app, server, albumRequests)
        } finally {
            app.container.client.credentials = previous
            server.shutdown()
        }
    }

    @Test fun phoneAndSessionUseAlbumCoverEvenWhenSongCoverIsDifferent() = withServer { app, server, albumRequests ->
        val client = app.container.client
        val song = Song("song-id", albumId = "album-id", coverArt = "different-song-cover")
        val item = AutoMediaItemFactory(client, app.packageName) { 0 }.song(song, "parent", 0)
        val uri = requireNotNull(item.mediaMetadata.artworkUri)
        assertEquals(true, CoverArtContentProvider.parse(uri)!!.isAlbum)
        assertEquals(song.albumId, CoverArtContentProvider.parse(uri)!!.coverId)
        assertEquals(uri, CoverArtContentProvider.authorizedAlbumContentUri(song.albumId, 800, client.credentials, client.artworkNamespace))
        val sessionBitmap = mediaArtworkBitmapLoader(app).loadBitmap(uri).get(5, TimeUnit.SECONDS)
        assertEquals(Color.RED, sessionBitmap.getPixel(4, 4))
        val phoneResult = runBlocking {
            app.imageLoader.execute(ImageRequest.Builder(app).data(uri).allowHardware(false).build())
        }
        assertTrue(phoneResult is SuccessResult)
        assertEquals(Color.RED, ((phoneResult as SuccessResult).drawable as BitmapDrawable).bitmap.getPixel(4, 4))
        assertEquals(1, albumRequests.get())
        assertEquals(2, server.requestCount)
    }

    @Test fun failedAlbumLookupCanBeRetriedWithoutFallingBackToSongArt() = withServer(true) { app, _, albumRequests ->
        val client = app.container.client
        val uri = requireNotNull(CoverArtContentProvider.authorizedAlbumContentUri("album-id", 800, client.credentials, client.artworkNamespace))
        val loader = mediaArtworkBitmapLoader(app)
        assertThrows(ExecutionException::class.java) { loader.loadBitmap(uri).get(5, TimeUnit.SECONDS) }
        assertEquals(Color.RED, loader.loadBitmap(uri).get(5, TimeUnit.SECONDS).getPixel(4, 4))
        assertEquals(2, albumRequests.get())
    }

    @Test fun trackArtworkCapabilityCannotBeUsedForAlbumLookup() = withServer { app, server, _ ->
        val client = app.container.client
        val issued = requireNotNull(CoverArtContentProvider.authorizedContentUri("album-id", 800, client.credentials, client.artworkNamespace))
        val tampered = issued.buildUpon().encodedPath(CoverArtContentProvider.buildEncodedPath("album-id", 800, isAlbum = true)).build()
        assertThrows(ExecutionException::class.java) { mediaArtworkBitmapLoader(app).loadBitmap(tampered).get(5, TimeUnit.SECONDS) }
        assertEquals(0, server.requestCount)
    }
}
