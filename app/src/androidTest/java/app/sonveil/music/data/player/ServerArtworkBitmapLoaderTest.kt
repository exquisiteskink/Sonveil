package app.sonveil.music.data.player

import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@UnstableApi
class ServerArtworkBitmapLoaderTest {
    private val uri = Uri.parse("content://app.sonveil.music.coverart/cover/400/album")

    private class FakeLoader : BitmapLoader {
        var calls = 0
        var decodeCalls = 0
        var next: ListenableFuture<Bitmap> = SettableFuture.create()

        override fun supportsMimeType(mimeType: String) = true
        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
            calls++
            return next
        }
        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
            decodeCalls++
            return next
        }
    }

    @Test fun pendingRequestsForSameCoverShareOneLoad() {
        val delegate = FakeLoader()
        val loader = ServerArtworkBitmapLoader(delegate)
        assertSame(loader.loadBitmap(uri), loader.loadBitmap(uri))
        assertEquals(1, delegate.calls)
    }

    @Test fun successfulCoverIsReused() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            val delegate = FakeLoader().apply { next = Futures.immediateFuture(bitmap) }
            val loader = ServerArtworkBitmapLoader(delegate)
            assertSame(loader.loadBitmap(uri), loader.loadBitmap(uri))
            assertEquals(1, delegate.calls)
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun failedCoverCanLoadAgainAfterConnectionRecovers() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            val delegate = FakeLoader().apply { next = Futures.immediateFailedFuture(IOException("offline")) }
            val loader = ServerArtworkBitmapLoader(delegate)
            loader.loadBitmap(uri)
            delegate.next = Futures.immediateFuture(bitmap)
            assertSame(bitmap, loader.loadBitmap(uri).get())
            assertEquals(2, delegate.calls)
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun cancelledCoverDoesNotPoisonLaterRequests() {
        val delegate = FakeLoader().apply {
            next = SettableFuture.create<Bitmap>().apply { cancel(false) }
        }
        val loader = ServerArtworkBitmapLoader(delegate)
        val cancelled = loader.loadBitmap(uri)
        delegate.next = SettableFuture.create()
        assertNotSame(cancelled, loader.loadBitmap(uri))
        assertEquals(2, delegate.calls)
    }

    @Test fun differentCoverStartsANewLoad() {
        val delegate = FakeLoader()
        val loader = ServerArtworkBitmapLoader(delegate)
        loader.loadBitmap(uri)
        loader.loadBitmap(uri.buildUpon().appendPath("other").build())
        assertEquals(2, delegate.calls)
    }

    @Test fun serverCoverWinsOverEmbeddedArtwork() {
        val delegate = FakeLoader()
        val loader = ServerArtworkBitmapLoader(delegate)
        val metadata = MediaMetadata.Builder()
            .setArtworkUri(uri)
            .setArtworkData(byteArrayOf(1, 2, 3), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build()
        assertSame(delegate.next, loader.loadBitmapFromMetadata(metadata))
        assertEquals(1, delegate.calls)
        assertEquals(0, delegate.decodeCalls)
    }

    @Test fun missingServerCoverDoesNotFallBackToEmbeddedArtwork() {
        val delegate = FakeLoader()
        val loader = ServerArtworkBitmapLoader(delegate)
        val metadata = MediaMetadata.Builder()
            .setArtworkData(byteArrayOf(1, 2, 3), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build()
        assertNull(loader.loadBitmapFromMetadata(metadata))
        assertEquals(0, delegate.calls)
        assertEquals(0, delegate.decodeCalls)
    }
}
