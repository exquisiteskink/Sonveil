package app.sonveil.music.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sonveil.music.data.remote.AlbumID3
import app.sonveil.music.data.remote.MusicFolder
import app.sonveil.music.data.remote.suspendRunCatching
import app.sonveil.music.ui.components.AlbumListRow
import app.sonveil.music.ui.components.ErrorText
import app.sonveil.music.ui.components.ScreenTopBar
import app.sonveil.music.ui.theme.LocalClient
import app.sonveil.music.ui.theme.LocalPalette
import kotlinx.coroutines.flow.distinctUntilChanged

private const val PAGE = 60

@Composable
fun AlbumsScreen(
    onBack: () -> Unit,
    onAlbum: (String) -> Unit,
) {
    val client = LocalClient.current
    val p = LocalPalette.current
    var folders by remember { mutableStateOf<List<MusicFolder>>(emptyList()) }
    var folderId by remember { mutableStateOf<String?>(null) }
    var albums by remember { mutableStateOf<List<AlbumID3>>(emptyList()) }
    var offset by remember { mutableStateOf(0) }
    var more by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    suspend fun loadPage(reset: Boolean) {
        if (loading) return
        if (!reset && !more) return
        loading = true
        val start = if (reset) 0 else offset
        try {
            val page = client.getAlbumList2("alphabeticalByName", PAGE, start, folderId)
            albums = if (reset) page else albums + page.filter { next -> albums.none { it.id == next.id } }
            offset = start + page.size
            more = page.size >= PAGE
            error = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (albums.isEmpty()) error = e.message
            more = false
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        folders = suspendRunCatching { client.getMusicFolders() }.getOrDefault(emptyList())
    }
    // One coroutine owns the selected folder's initial load and pagination.
    // Switching folders cancels its request before starting the new one.
    LaunchedEffect(client, folderId) {
        albums = emptyList()
        offset = 0
        more = true
        error = null
        listState.scrollToItem(0)
        loadPage(reset = true)
        snapshotFlow {
            (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) to albums.size
        }
            .distinctUntilChanged()
            .collect { (last, _) ->
                if (more && !loading && albums.isNotEmpty() && last >= albums.size - 8) {
                    loadPage(reset = false)
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        item { ScreenTopBar("Albums", onBack) }
        if (folders.size > 1) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FolderChip("All", folderId == null) { folderId = null }
                    }
                    items(folders, key = { it.id }) { folder ->
                        FolderChip(folder.name.ifBlank { "Library" }, folderId == folder.id) {
                            folderId = folder.id
                        }
                    }
                }
            }
        }
        if (error != null && albums.isEmpty()) {
            item { ErrorText(error ?: "") }
        }
        items(albums, key = { it.id }) { album ->
            AlbumListRow(album, onClick = { onAlbum(album.id) })
        }
        if (loading) {
            item {
                Text(
                    "Loading albums",
                    color = p.onBackground.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun FolderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(
        label,
        color = if (selected) p.background else p.onBackground,
        fontSize = 13.sp,
        modifier = Modifier
            .background(
                if (selected) p.onBackground else p.onBackground.copy(alpha = 0.12f),
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
