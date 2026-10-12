package app.sonveil.music.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sonveil.music.data.remote.MusicFolder
import app.sonveil.music.data.remote.suspendRunCatching
import app.sonveil.music.ui.components.*
import app.sonveil.music.ui.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged

private const val PAGE = 60

@Composable
fun AlbumsScreen(onBack: () -> Unit, onAlbum: (String) -> Unit) {
    val client = LocalClient.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val pager = remember(client, scope) { AlbumPager(scope, { folder, offset ->
        client.getAlbumList2("alphabeticalByName", PAGE, offset, folder)
    }) }
    val page by pager.state.collectAsStateWithLifecycle()
    var folders by remember { mutableStateOf<List<MusicFolder>>(emptyList()) }
    var folderId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(client) { folders = suspendRunCatching { client.getMusicFolders() }.getOrDefault(emptyList()) }
    LaunchedEffect(pager, folderId) {
        pager.selectFolder(folderId)
        listState.scrollToItem(0)
    }
    LaunchedEffect(pager, listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged().collect { last ->
                val current = pager.state.value
                if (current.albums.isNotEmpty() && current.error == null && last >= current.albums.size - 8) pager.loadMore()
            }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = LocalPlayerContentInset.current)) {
        item { ScreenTopBar("Albums", onBack) }
        if (folders.size > 1) item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FolderChip("All", folderId == null) { folderId = null } }
                items(folders, key = { it.id }) { folder ->
                    FolderChip(folder.name.ifBlank { "Library" }, folderId == folder.id) { folderId = folder.id }
                }
            }
        }
        items(page.albums, key = { it.id }) { album -> AlbumListRow(album, onClick = { onAlbum(album.id) }) }
        if (page.loading) item { LoadingStatus("Loading albums") }
        page.error?.let { message -> item { ErrorState(message, pager::loadMore) } }
        if (!page.loading && page.error == null && !page.more && page.albums.isEmpty()) {
            item { EmptyState("No albums in this library yet. Add music on your server, then refresh.") }
            item { androidx.compose.material3.TextButton(onClick = { pager.selectFolder(folderId) }) { Text("Refresh") } }
        }
    }
}

@Composable
private fun FolderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.heightIn(min = SonveilSize.Action)
        .semantics { this.selected = selected }
        .background(if (selected) p.primary else p.surfaceHigh, RoundedCornerShape(24.dp))
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, color = if (selected) p.onPrimary else p.onBackground, fontSize = 14.sp)
    }
}
