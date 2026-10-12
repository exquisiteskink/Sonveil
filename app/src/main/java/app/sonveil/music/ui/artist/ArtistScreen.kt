package app.sonveil.music.ui.artist

import app.sonveil.music.data.remote.suspendRunCatching
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sonveil.music.data.remote.AlbumID3
import app.sonveil.music.data.remote.ArtistID3
import app.sonveil.music.data.remote.ArtistInfo2
import app.sonveil.music.data.remote.ArtistWithAlbums
import app.sonveil.music.data.remote.SimilarArtist
import app.sonveil.music.data.remote.Song
import app.sonveil.music.ui.components.AlbumCard
import app.sonveil.music.ui.components.AlbumListRow
import app.sonveil.music.ui.components.CoverArt
import app.sonveil.music.ui.components.ErrorText
import app.sonveil.music.ui.components.HeaderPlay
import app.sonveil.music.ui.components.ScreenTopBar
import app.sonveil.music.ui.components.SectionHeader
import app.sonveil.music.ui.components.SongRow
import app.sonveil.music.ui.components.SongRowSkeleton
import app.sonveil.music.ui.theme.LocalClient
import app.sonveil.music.ui.theme.LocalContainer
import app.sonveil.music.ui.theme.LocalPalette
import app.sonveil.music.ui.theme.LocalPlayer
import app.sonveil.music.ui.theme.LocalPlayerContentInset
import app.sonveil.music.ui.theme.textSecondary
import app.sonveil.music.ui.components.DetailLoadingScreen
import app.sonveil.music.ui.components.ErrorState
import app.sonveil.music.ui.components.EmptyState
import app.sonveil.music.ui.components.LoadingStatus
import app.sonveil.music.ui.components.readKeeping
import app.sonveil.music.ui.components.libraryError
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun ArtistScreen(
    artistId: String,
    onBack: () -> Unit,
    onAlbum: (String) -> Unit,
    onPopular: (String, String) -> Unit,
    onAlbums: (String, String) -> Unit,
    onArtist: (String) -> Unit,
) {
    val client = LocalClient.current
    val metadata = LocalContainer.current.metadata
    val player = LocalPlayer.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var artist by remember(artistId) { mutableStateOf<ArtistWithAlbums?>(null) }
    var info by remember(artistId) { mutableStateOf(ArtistInfo2()) }
    var popular by remember(artistId) { mutableStateOf<List<Song>>(emptyList()) }
    var popularLoading by remember(artistId) { mutableStateOf(true) }
    var bio by remember(artistId) { mutableStateOf<String?>(null) }
    var error by remember(artistId) { mutableStateOf<String?>(null) }
    var libraryArtists by remember(artistId) { mutableStateOf<List<ArtistID3>>(emptyList()) }

    var retryEpoch by remember(artistId) { mutableIntStateOf(0) }
    LaunchedEffect(artistId, retryEpoch) {
        error = null
        popular = emptyList()
        popularLoading = true
        try {
            val a = client.getArtist(artistId)
            artist = a
            coroutineScope {
                launch {
                    popular = suspendRunCatching {
                        metadata.popularLibrarySongs(artistName = a.name, artistId = a.id, count = 20)
                    }.getOrDefault(emptyList())
                    popularLoading = false
                }
                launch {
                    val loaded = suspendRunCatching { client.getArtistInfo2(artistId) }.getOrDefault(ArtistInfo2())
                    info = loaded
                    bio = metadata.biography(a.name, loaded.biography)
                }
                launch {
                    libraryArtists = suspendRunCatching { client.getArtists() }.getOrDefault(emptyList())
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = libraryError(e)
            popularLoading = false
        }
    }

    val current = artist
    if (current == null) {
        DetailLoadingScreen("Artist", onBack, error, { retryEpoch++ })
        return
    }

    val similarInLibrary = info.similarArtist.mapNotNull { sim ->
        val match = libraryArtists.find { lib ->
            lib.id != artistId && (
                (!sim.id.isNullOrBlank() && lib.id == sim.id) ||
                    lib.name.equals(sim.name, ignoreCase = true)
                )
        } ?: return@mapNotNull null
        sim.copy(id = match.id, coverArt = sim.coverArt ?: match.coverArt, artistImageUrl = sim.artistImageUrl ?: match.artistImageUrl)
    }.distinctBy { it.id }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = LocalPlayerContentInset.current),
    ) {
        ScreenTopBar(current.name, onBack)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(
                coverId = current.coverArt ?: current.id,
                modifier = Modifier.size(128.dp),
                contentDescription = current.name,
                corner = 64.dp,
                imageUrl = current.artistImageUrl ?: info.largeImageUrl ?: info.mediumImageUrl,
            )
            Spacer(Modifier.weight(1f))
            HeaderPlay(
                onClick = {
                    when {
                        popular.isNotEmpty() -> player.play(popular, 0)
                        current.album.isNotEmpty() -> scope.launch {
                            val songs = suspendRunCatching { client.getAlbum(current.album.first().id).song }.getOrDefault(emptyList())
                            if (songs.isNotEmpty()) player.play(songs, 0)
                        }
                    }
                },
                size = 64.dp,
            )
        }

        if (current.album.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionHeader(
                if (current.album.size == 1) "1 Album" else "${current.album.size} Albums",
            ) { onAlbums(artistId, current.name) }
            current.album.take(6).forEach { album ->
                AlbumListRow(album, onClick = { onAlbum(album.id) })
            }
        }

        if (popularLoading || popular.isNotEmpty()) {
            SectionHeader("Popular tracks") { onPopular(artistId, current.name) }
            if (popular.isEmpty()) {
                repeat(4) { SongRowSkeleton() }
            } else {
                popular.take(5).forEachIndexed { i, song ->
                    SongRow(
                        song = song,
                        rank = i + 1,
                        showArtist = false,
                        showCover = false,
                        onClick = { player.play(popular, i) },
                    )
                }
            }
        }

        if (!bio.isNullOrBlank()) {
            SectionHeader("Artist bio")
            Text(
                bio!!,
                color = p.textSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        if (similarInLibrary.isNotEmpty()) {
            SectionHeader("Similar artists")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(similarInLibrary, key = { it.id ?: it.name }) { similar ->
                    SimilarChip(similar, onClick = { similar.id?.let(onArtist) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SimilarChip(artist: SimilarArtist, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.width(96.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverArt(
            coverId = artist.coverArt ?: artist.id,
            modifier = Modifier.size(88.dp).aspectRatio(1f),
            contentDescription = artist.name,
            corner = 44.dp,
            imageUrl = artist.artistImageUrl,
        )
        Spacer(Modifier.height(6.dp))
        Text(artist.name, color = p.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
    }
}

@Composable
fun PopularSongsScreen(
    artistId: String,
    artistName: String,
    onBack: () -> Unit,
) {
    val client = LocalClient.current
    val metadata = LocalContainer.current.metadata
    val player = LocalPlayer.current
    val p = LocalPalette.current
    var songs by remember(artistId) { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember(artistId) { mutableStateOf(true) }
    var error by remember(artistId) { mutableStateOf<String?>(null) }
    var retry by remember(artistId) { mutableIntStateOf(0) }
    LaunchedEffect(artistId, retry) {
        loading = true
        val result = readKeeping(songs) {
            val artist = client.getArtist(artistId)
            metadata.popularLibrarySongs(artistName = artist.name, artistId = artist.id, count = 20)
        }
        songs = result.value.orEmpty(); error = result.error; loading = false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LocalPlayerContentInset.current)) {
        ScreenTopBar("Popular tracks", onBack)
        if (loading) LoadingStatus("Loading popular tracks")
        error?.let { ErrorState(it, { retry++ }) }
        if (!loading && error == null && songs.isEmpty()) EmptyState("No popular tracks on this server yet.")
        Text(
            artistName,
            color = p.textSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        songs.forEachIndexed { i, song ->
            SongRow(
                song = song,
                rank = i + 1,
                showCover = false,
                onClick = { player.play(songs, i) },
            )
        }
    }
}

@Composable
fun ArtistAlbumsScreen(
    artistId: String,
    artistName: String,
    onBack: () -> Unit,
    onAlbum: (String) -> Unit,
) {
    val client = LocalClient.current
    val p = LocalPalette.current
    var albums by remember(artistId) { mutableStateOf<List<AlbumID3>>(emptyList()) }
    var loading by remember(artistId) { mutableStateOf(true) }
    var error by remember(artistId) { mutableStateOf<String?>(null) }
    var retry by remember(artistId) { mutableIntStateOf(0) }
    LaunchedEffect(artistId, retry) {
        loading = true
        val result = readKeeping(albums) { client.getArtist(artistId).album }
        albums = result.value.orEmpty(); error = result.error; loading = false
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Albums", onBack)
        if (loading) LoadingStatus("Loading albums")
        error?.let { ErrorState(it, { retry++ }) }
        if (!loading && error == null && albums.isEmpty()) EmptyState("No albums for this artist yet.")
        Text(
            artistName,
            color = p.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalPlayerContentInset.current),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            gridItems(albums, key = { it.id }) { album ->
                AlbumCard(album, onClick = { onAlbum(album.id) }, width = 200.dp)
            }
        }
    }
}
