package app.sonveil.music.ui.album

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sonveil.music.data.remote.AlbumWithSongs
import app.sonveil.music.data.remote.Song
import app.sonveil.music.data.remote.formatDuration
import app.sonveil.music.ui.components.CoverArt
import app.sonveil.music.ui.components.ErrorText
import app.sonveil.music.ui.components.HeaderPlay
import app.sonveil.music.ui.components.ScreenTopBar
import app.sonveil.music.ui.components.SongRow
import app.sonveil.music.data.download.DownloadPhase
import app.sonveil.music.ui.theme.LocalContainer
import app.sonveil.music.ui.theme.LocalClient
import app.sonveil.music.ui.theme.LocalPalette
import app.sonveil.music.ui.theme.LocalPlayer
import app.sonveil.music.ui.theme.LocalPlayerContentInset
import app.sonveil.music.ui.theme.textSecondary
import app.sonveil.music.ui.components.DetailLoadingScreen
import app.sonveil.music.ui.components.libraryError

internal data class DiscSection(
    val number: Int,
    val songs: List<Song>,
)

internal fun albumDiscSections(songs: List<Song>): List<DiscSection> =
    songs
        .sortedWith(
            compareBy<Song>(
                { it.discNumber.coerceAtLeast(1) },
                { if (it.track > 0) it.track else Int.MAX_VALUE },
                { it.title },
            ),
        )
        .groupBy { it.discNumber.coerceAtLeast(1) }
        .map { (number, tracks) -> DiscSection(number, tracks) }

@Composable
fun AlbumScreen(
    albumId: String,
    onBack: () -> Unit,
    onArtist: (String) -> Unit,
) {
    val client = LocalClient.current
    val player = LocalPlayer.current
    val container = LocalContainer.current
    val dlState by container.downloads.state.collectAsState()
    val p = LocalPalette.current
    var album by remember(albumId) { mutableStateOf<AlbumWithSongs?>(null) }
    var error by remember(albumId) { mutableStateOf<String?>(null) }

    var retryEpoch by remember(albumId) { mutableIntStateOf(0) }
    LaunchedEffect(albumId, retryEpoch) {
        error = null
        try {
            album = client.getAlbum(albumId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = libraryError(e)
        }
    }

    val current = album
    if (current == null) {
        DetailLoadingScreen("Album", onBack, error, { retryEpoch++ })
        return
    }

    val discSections = albumDiscSections(current.song)
    val songs = discSections.flatMap(DiscSection::songs)
    val showDiscHeaders = discSections.size > 1 || discSections.any { it.number > 1 }
    val labelName = current.recordLabels
        .map { it.name.trim() }
        .firstOrNull { it.isNotEmpty() }
    val stats = listOfNotNull(
        current.year.takeIf { it > 0 }?.toString(),
        labelName,
        "${songs.size} tracks",
        "${discSections.size} discs".takeIf { discSections.size > 1 },
        formatDuration(current.duration).takeIf { current.duration > 0 },
    ).joinToString(" · ")

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(bottom = LocalPlayerContentInset.current),
    ) {
        item {
            ScreenTopBar(current.displayName, onBack)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                CoverArt(
                    current.coverArt,
                    Modifier
                        .size(132.dp)
                        .shadow(10.dp, RoundedCornerShape(8.dp))
                        .border(2.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
                    current.displayName,
                    corner = 8.dp,
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    HeaderPlay(
                        onClick = { if (songs.isNotEmpty()) player.play(songs, 0) },
                        size = 64.dp,
                    )
                    Spacer(Modifier.height(8.dp))
                    IconButton(onClick = { if (songs.isNotEmpty()) player.play(songs.shuffled(), 0) }) {
                        Icon(Icons.Rounded.Shuffle, "Shuffle", tint = p.textSecondary)
                    }
                    IconButton(
                        onClick = {
                            if (songs.isNotEmpty()) {
                                container.downloads.enqueueAlbum(albumId, songs, current.displayName)
                            }
                        },
                    ) {
                        val tint = when (dlState.phase) {
                            DownloadPhase.Running -> p.primary
                            else -> p.textSecondary
                        }
                        Icon(Icons.Rounded.Download, "Download album", tint = tint)
                    }
                }
            }
            Text(
                stats,
                color = p.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (dlState.phase == DownloadPhase.Running ||
                dlState.phase == DownloadPhase.PausedWifi ||
                dlState.phase == DownloadPhase.Failed
            ) {
                val status = when (dlState.phase) {
                    DownloadPhase.Running -> "Downloading ${dlState.done}/${dlState.total}" +
                        (dlState.currentTitle?.let { " · $it" } ?: "")
                    else -> dlState.message.orEmpty()
                }
                if (status.isNotBlank()) {
                    Text(
                        status,
                        color = p.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(1.dp)
                    .background(p.onBackground.copy(alpha = 0.22f)),
            )
        }
        var queueOffset = 0
        discSections.forEach { section ->
            val sectionOffset = queueOffset
            if (showDiscHeaders) {
                item(key = "disc-${section.number}") {
                    Text(
                        "Disc ${section.number}",
                        color = p.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                    )
                }
            }
            itemsIndexed(section.songs, key = { sectionIndex, song -> "${song.id}-$sectionIndex-${section.number}" }) { sectionIndex, song ->
                SongRow(
                    song = song,
                    showArtist = true,
                    showCover = false,
                    rank = if (song.track > 0) song.track else sectionIndex + 1,
                    onClick = { player.play(songs, sectionOffset + sectionIndex) },
                )
            }
            queueOffset += section.songs.size
        }
        item {
            current.artist?.takeIf { it.isNotBlank() }?.let { artistName ->
                Spacer(Modifier.height(8.dp))
                Text(
                    artistName,
                    color = p.textSecondary,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clickable(enabled = current.artistId != null) {
                            current.artistId?.let(onArtist)
                        },
                )
            }
        }
    }
}
