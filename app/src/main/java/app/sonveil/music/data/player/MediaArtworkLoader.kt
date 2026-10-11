package app.sonveil.music.data.player

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

/** The session/notification loader must resolve the local URIs supplied to car clients. */
@UnstableApi
internal fun mediaArtworkBitmapLoader(context: Context): BitmapLoader =
    ServerArtworkBitmapLoader(DataSourceBitmapLoader(context))

/** Cache the last server cover, but allow a failed or cancelled request to be retried. */
@UnstableApi
internal class ServerArtworkBitmapLoader(private val delegate: BitmapLoader) : BitmapLoader {
    private var lastUri: Uri? = null
    private var lastFuture: ListenableFuture<Bitmap>? = null

    override fun supportsMimeType(mimeType: String): Boolean = delegate.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = delegate.decodeBitmap(data)

    @Synchronized
    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val cached = lastFuture
        if (lastUri == uri && cached != null && isReusable(cached)) return cached
        return delegate.loadBitmap(uri).also {
            lastUri = uri
            lastFuture = it
        }
    }

    // Embedded audio tags can contain a different cover than Navidrome. Keep the server
    // URI authoritative, including when the player supplies both URI and embedded bytes.
    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? =
        metadata.artworkUri?.let(::loadBitmap)

    private fun isReusable(future: ListenableFuture<Bitmap>): Boolean {
        if (!future.isDone) return true
        return try {
            Futures.getDone(future)
            true
        } catch (_: ExecutionException) {
            false
        } catch (_: CancellationException) {
            false
        }
    }
}
