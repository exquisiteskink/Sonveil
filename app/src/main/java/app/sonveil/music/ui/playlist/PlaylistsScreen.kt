package app.sonveil.music.ui.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.sonveil.music.data.remote.Playlist
import app.sonveil.music.ui.components.*
import app.sonveil.music.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PlaylistsScreen(onBack: () -> Unit, onPlaylist: (String) -> Unit) {
    val client = LocalClient.current
    val scope = rememberCoroutineScope()
    var playlists by remember(client) { mutableStateOf<List<Playlist>>(emptyList()) }
    var loading by remember(client) { mutableStateOf(false) }
    var error by remember(client) { mutableStateOf<String?>(null) }
    suspend fun load() {
        if (loading) return
        loading = true
        error = null
        try {
            val result = readKeeping(playlists) { client.getPlaylists() }
            result.value?.let { playlists = it }
            error = result.error
        } finally { loading = false }
    }
    LaunchedEffect(client) { load() }
    PlaylistsContent(playlists, loading, error, { scope.launch { load() } }, onBack, onPlaylist)
}

@Composable
internal fun PlaylistsContent(playlists: List<Playlist>, loading: Boolean, error: String?,
    onRetry: () -> Unit, onBack: () -> Unit, onPlaylist: (String) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Playlists", onBack)
        LazyColumn(Modifier.weight(1f).testTag("Playlists list"),
            contentPadding = PaddingValues(bottom = LocalPlayerContentInset.current)) {
            if (loading) item { LoadingStatus("Loading playlists") }
            if (error != null) item { ErrorState(error, onRetry) }
            if (!loading && error == null && playlists.isEmpty()) item { EmptyState("No playlists yet") }
            items(playlists, key = { it.id }) { playlist ->
                Text(playlist.name.ifBlank { "Playlist" }, color = p.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().heightIn(min = SonveilSize.Action)
                        .clickable(role = Role.Button, onClick = { onPlaylist(playlist.id) })
                        .padding(horizontal = SonveilSize.PageGutter, vertical = 16.dp))
            }
        }
    }
}
