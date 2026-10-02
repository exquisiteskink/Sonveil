package app.sonveil.music.ui.components

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sonveil.music.data.art.ArtOverrideStore

/**
 * Drop-in around [CoverArt] when an album id is known.
 * Prefers [ArtOverrideStore] local artwork, with network fallback if decoding fails.
 */
@Composable
fun OverrideAwareCoverArt(
    albumId: String?,
    coverId: String?,
    artOverrides: ArtOverrideStore?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    corner: Dp = 8.dp,
    fallback: ImageVector = Icons.Rounded.Album,
    imageUrl: String? = null,
    retainPreviousOnChange: Boolean = false,
) {
    val revisions = artOverrides?.revisions?.collectAsStateWithLifecycle()?.value
    val revision = albumId?.let { revisions?.get(artOverrides?.albumRevisionKey(it)) } ?: 0L
    val overrideUri: Uri? = remember(albumId, artOverrides, revision) {
        if (albumId.isNullOrBlank() || artOverrides == null) null
        else artOverrides.getAlbumOverrideUri(albumId)
    }
    CoverArt(
        coverId = coverId,
        modifier = modifier,
        contentDescription = contentDescription,
        corner = corner,
        fallback = fallback,
        imageUrl = imageUrl,
        retainPreviousOnChange = retainPreviousOnChange,
        localUri = overrideUri,
        localCacheKey = "${artOverrides?.cacheNamespace}:$overrideUri:$revision",
    )
}
