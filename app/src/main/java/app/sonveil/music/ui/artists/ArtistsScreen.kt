package app.sonveil.music.ui.artists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sonveil.music.data.remote.ArtistID3
import app.sonveil.music.ui.components.ArtistListRow
import app.sonveil.music.ui.components.ArtistTile
import app.sonveil.music.ui.settings.AppearancePrefs
import app.sonveil.music.ui.theme.LocalClient
import app.sonveil.music.ui.theme.LocalPalette
import app.sonveil.music.ui.theme.LocalPlayerContentInset
import app.sonveil.music.ui.theme.textSecondary
import app.sonveil.music.ui.components.ErrorState
import app.sonveil.music.ui.components.libraryError
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(onArtist: (String) -> Unit) {
    val client = LocalClient.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { AppearancePrefs(context) }
    var artists by remember { mutableStateOf<List<ArtistID3>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var grid by remember { mutableStateOf(prefs.artistGrid) }

    suspend fun load() {
        loading = true
        error = null
        try {
            artists = client.getArtists().sortedBy { it.name.lowercase() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = libraryError(e)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    PullToRefreshBox(isRefreshing = loading, onRefresh = { scope.launch { load() } }) {
        if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = LocalPlayerContentInset.current),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { ArtistsHeader(grid) { grid = it; prefs.artistGrid = it } }
                if (error != null && artists.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { ErrorState(error ?: "", { scope.launch { load() } }) }
                }
                items(artists, key = { it.id }) { artist ->
                    ArtistTile(artist, onClick = { onArtist(artist.id) })
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LocalPlayerContentInset.current),
            ) {
                item { ArtistsHeader(grid) { grid = it; prefs.artistGrid = it } }
                if (error != null && artists.isEmpty()) {
                    item { ErrorState(error ?: "", { scope.launch { load() } }) }
                }
                items(artists, key = { it.id }) { artist ->
                    ArtistListRow(artist, onClick = { onArtist(artist.id) })
                }
            }
        }
    }
}

@Composable
private fun ArtistsHeader(grid: Boolean, onGrid: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Artists",
            color = p.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        IconButton(onClick = { onGrid(true) }) {
            Icon(
                Icons.Rounded.GridView,
                contentDescription = "Grid",
                tint = if (grid) p.primary else p.textSecondary,
            )
        }
        IconButton(onClick = { onGrid(false) }) {
            Icon(
                Icons.AutoMirrored.Rounded.ViewList,
                contentDescription = "List",
                tint = if (!grid) p.primary else p.textSecondary,
            )
        }
    }
}
