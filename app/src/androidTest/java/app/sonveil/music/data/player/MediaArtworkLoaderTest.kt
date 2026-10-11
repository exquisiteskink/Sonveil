package app.sonveil.music.data.player

import android.graphics.Bitmap
import android.graphics.Color
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sonveil.music.SonveilApp
import app.sonveil.music.data.art.ArtOverrideStore
import app.sonveil.music.data.art.CoverArtContentProvider
import app.sonveil.music.data.auth.StoredCredentials
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@UnstableApi
class MediaArtworkLoaderTest {
    private fun withArtwork(block: (SonveilApp, String) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SonveilApp
        val previous = app.container.client.credentials
        var cachedCover: File? = null
        val overrides = ArtOverrideStore(app)
        val coverId = "instrumentation-art-${UUID.randomUUID()}"
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        try {
            app.container.client.credentials = StoredCredentials("https://test.invalid", "test")
            val bytes = ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
            // Seed the same cache entry populated by the server's getCoverArt response.
            // An old client override must not replace that server image.
            val url = requireNotNull(app.container.client.coverUrl(coverId, 128))
            cachedCover = File(app.cacheDir, "coverart-v2/${ArtOverrideStore.sanitizeId(url)}.img")
                .apply { parentFile!!.mkdirs(); writeBytes(bytes) }
            bitmap.eraseColor(Color.BLUE)
            val localBytes = ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
            overrides.setAlbumOverride(coverId, localBytes)
            block(app, coverId)
        } finally {
            bitmap.recycle()
            overrides.clearAlbumOverride(coverId)
            cachedCover?.delete()
            app.container.client.credentials = previous
        }
    }

    @Test fun sessionArtworkLoaderDecodesScopedContentUri() = withArtwork { app, coverId ->
        val client = app.container.client
        val uri = requireNotNull(CoverArtContentProvider.authorizedContentUri(coverId, 128, client.credentials, client.artworkNamespace))
        val decoded = mediaArtworkBitmapLoader(app).loadBitmap(uri).get(5, TimeUnit.SECONDS)
        assertEquals(8, decoded.width)
        assertEquals(8, decoded.height)
        assertEquals(Color.RED, decoded.getPixel(4, 4))
    }

    @Test fun sessionArtworkLoaderRejectsTamperedSize() = withArtwork { app, coverId ->
        val client = app.container.client
        val issued = requireNotNull(CoverArtContentProvider.authorizedContentUri(coverId, 128, client.credentials, client.artworkNamespace))
        val uri = issued.buildUpon().encodedPath(CoverArtContentProvider.buildEncodedPath(coverId, 256)).build()
        val error = assertThrows(ExecutionException::class.java) {
            mediaArtworkBitmapLoader(app).loadBitmap(uri).get(5, TimeUnit.SECONDS)
        }
        assertTrue(error.cause is java.io.IOException)
    }

    @Test fun sessionArtworkLoaderRejectsUnsignedUri() = withArtwork { app, coverId ->
        val uri = requireNotNull(CoverArtContentProvider.contentUri(coverId, 128))
        val error = assertThrows(ExecutionException::class.java) {
            mediaArtworkBitmapLoader(app).loadBitmap(uri).get(5, TimeUnit.SECONDS)
        }
        assertTrue(error.cause is java.io.IOException)
    }

    @Test fun sessionArtworkLoaderRejectsAnotherAccountsUri() = withArtwork { app, coverId ->
        val uri = requireNotNull(CoverArtContentProvider.contentUri(coverId, 128, "wrong-account"))
        val error = assertThrows(ExecutionException::class.java) {
            mediaArtworkBitmapLoader(app).loadBitmap(uri).get(5, TimeUnit.SECONDS)
        }
        assertTrue(error.cause is java.io.IOException)
    }
}
