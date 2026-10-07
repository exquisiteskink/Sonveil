package app.sonveil.music.ui.search

import app.sonveil.music.data.remote.suspendRunCatching
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import app.sonveil.music.data.remote.AlbumID3
import app.sonveil.music.data.remote.ArtistID3
import app.sonveil.music.data.remote.Genre
import app.sonveil.music.data.remote.SearchResult3
import app.sonveil.music.data.remote.Song
import app.sonveil.music.ui.components.ArtistTile
import app.sonveil.music.ui.components.CoverArt
import app.sonveil.music.ui.components.GenreChip
import app.sonveil.music.ui.components.HorizontalAlbums
import app.sonveil.music.ui.components.SectionHeader
import app.sonveil.music.ui.components.SongRow
import app.sonveil.music.ui.theme.LocalClient
import app.sonveil.music.ui.theme.LocalContainer
import app.sonveil.music.ui.theme.LocalPalette
import app.sonveil.music.ui.theme.LocalPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class SearchSuggestion(
    val key: String,
    val title: String,
    val subtitle: String?,
    val icon: ImageVector,
    val coverArt: String? = null,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onArtist: (String) -> Unit,
    onAlbum: (String) -> Unit,
) {
    val client = LocalClient.current
    val player = LocalPlayer.current
    val container = LocalContainer.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf(SearchResult3()) }
    var moreArtists by remember { mutableStateOf<List<app.sonveil.music.data.remote.ArtistID3>>(emptyList()) }
    var moreAlbums by remember { mutableStateOf<List<AlbumID3>>(emptyList()) }
    var moreSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var canLoadMore by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf(SearchResult3()) }
    var recentQueries by remember { mutableStateOf(container.recentSearches.list()) }
    var allGenres by remember { mutableStateOf<List<Genre>>(emptyList()) }
    var recentAlbums by remember { mutableStateOf<List<AlbumID3>>(emptyList()) }
    var songForActions by remember { mutableStateOf<Song?>(null) }
    var actionSongQueue by remember { mutableStateOf<List<Song>>(emptyList()) }

    LaunchedEffect(Unit) {
        allGenres = suspendRunCatching { client.getGenres() }.getOrDefault(emptyList()).filter { it.value.isNotBlank() }
        recentAlbums = suspendRunCatching { client.getAlbumList2("recent", 24) }.getOrDefault(emptyList())
    }

    // Full results — existing search3 (OpenSubsonic / Subsonic / Navidrome).
    LaunchedEffect(query) {
        if (query.isBlank()) {
            result = SearchResult3()
            suggestions = SearchResult3()
            moreArtists = emptyList()
            moreAlbums = emptyList()
            moreSongs = emptyList()
            canLoadMore = false
            return@LaunchedEffect
        }
        val q = query.trim()
        // Navidrome ignores single-character search3 queries; still allow UI typing.
        delay(180)
        suggestions = suspendRunCatching {
            client.search3(q, artistCount = 6, albumCount = 6, songCount = 8)
        }.getOrDefault(SearchResult3())
        delay(120)
        val page = suspendRunCatching { client.search3(q) }.getOrDefault(SearchResult3())
        result = page
        moreArtists = emptyList()
        moreAlbums = emptyList()
        moreSongs = emptyList()
        canLoadMore = page.artist.size >= 20 || page.album.size >= 20 || page.song.size >= 20
        if (q.length >= 2 && (result.artist.isNotEmpty() || result.album.isNotEmpty() || result.song.isNotEmpty())) {
            container.recentSearches.add(q)
            recentQueries = container.recentSearches.list()
        }
    }

    val matchedGenres = remember(query, allGenres) {
        if (query.isBlank()) emptyList()
        else allGenres.filter { it.value.contains(query.trim(), ignoreCase = true) }.take(16)
    }
    val browseGenres = remember(allGenres) {
        allGenres.sortedByDescending { it.songCount }.take(24)
    }
    val recentArtists = remember(recentAlbums) { recentArtistsFromAlbums(recentAlbums) }
    // Spec: blank AND unfocused → genres + recent artists; focus or typing → results / suggestions.
    val showEmptyBrowse = query.isBlank() && !searchFocused
    val showRecentQueries = query.isBlank() && searchFocused && recentQueries.isNotEmpty()

    val suggestionRows = remember(suggestions, query) {
        if (query.isBlank()) emptyList()
        else buildSuggestions(suggestions, onArtist, onAlbum) { song, songs ->
            actionSongQueue = songs
            songForActions = song
        }
    }

    Column(Modifier.fillMaxSize().padding(bottom = 64.dp)) {
        Text(
            "Search",
            color = p.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
            textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .onFocusChanged { searchFocused = it.isFocused },
            placeholder = { Text("Artists, albums, songs, genres") },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = p.onBackground.copy(alpha = 0.45f)) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = p.onBackground.copy(alpha = 0.18f),
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = p.surfaceHigh,
                unfocusedContainerColor = p.surfaceHigh,
                focusedTextColor = p.onBackground,
                unfocusedTextColor = p.onBackground,
                cursorColor = p.onBackground,
            ),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            if (showEmptyBrowse) {
                if (browseGenres.isNotEmpty()) {
                    item { SectionHeader("Genres") }
                    item {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            browseGenres.forEach { genre ->
                                GenreChip(genre, onClick = {
                                    scope.launch {
                                        val songs = suspendRunCatching { client.getSongsByGenre(genre.value, 80) }.getOrDefault(emptyList())
                                        if (songs.isNotEmpty()) player.play(songs.shuffled(), 0)
                                    }
                                })
                            }
                        }
                    }
                }
                if (recentArtists.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        SectionHeader("Recently played artists")
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(recentArtists, key = { "ra-${it.id}" }) { artist ->
                                Column(Modifier.width(96.dp).padding(bottom = 8.dp)) {
                                    ArtistTile(artist, onClick = { onArtist(artist.id) }, circular = true)
                                }
                            }
                        }
                    }
                }
            } else if (showRecentQueries) {
                item { SectionHeader("Recent searches") }
                items(recentQueries, key = { "rq-$it" }) { recent ->
                    RecentQueryRow(recent) {
                        query = recent
                        container.recentSearches.add(recent)
                        recentQueries = container.recentSearches.list()
                    }
                }
            } else {
                if (suggestionRows.isNotEmpty()) {
                    item { SectionHeader("Suggestions") }
                    items(suggestionRows, key = { it.key }) { row ->
                        SuggestionRow(row)
                    }
                }
                if (matchedGenres.isNotEmpty()) {
                    item { SectionHeader("Genres") }
                    item {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            matchedGenres.forEach { genre ->
                                GenreChip(genre, onClick = {
                                    scope.launch {
                                        val songs = suspendRunCatching { client.getSongsByGenre(genre.value, 80) }.getOrDefault(emptyList())
                                        if (songs.isNotEmpty()) player.play(songs.shuffled(), 0)
                                    }
                                })
                            }
                        }
                    }
                }
                val artists = result.artist + moreArtists
                val albums = result.album + moreAlbums
                val songs = result.song + moreSongs
                if (artists.isNotEmpty()) {
                    item { SectionHeader("Artists") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(artists, key = { it.id }) { artist ->
                                Column(Modifier.width(120.dp).padding(bottom = 8.dp)) {
                                    ArtistTile(artist, onClick = { onArtist(artist.id) }, circular = true)
                                }
                            }
                        }
                    }
                }
                if (albums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    item { HorizontalAlbums(albums) { onAlbum(it.id) } }
                }
                if (songs.isNotEmpty()) {
                    item { SectionHeader("Songs") }
                    itemsIndexed(songs, key = { i, s -> "${s.id}-$i" }) { _, song ->
                        SongRow(
                            song = song,
                            onClick = {
                                actionSongQueue = songs
                                songForActions = song
                            },
                        )
                    }
                }
                if (canLoadMore && query.isNotBlank()) {
                    item {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                val q = query.trim()
                                scope.launch {
                                    val page = suspendRunCatching {
                                        client.search3(
                                            q,
                                            artistOffset = artists.size,
                                            albumOffset = albums.size,
                                            songOffset = songs.size,
                                        )
                                    }.getOrDefault(SearchResult3())
                                    moreArtists = moreArtists + page.artist.filter { next -> artists.none { it.id == next.id } }
                                    moreAlbums = moreAlbums + page.album.filter { next -> albums.none { it.id == next.id } }
                                    moreSongs = moreSongs + page.song.filter { next -> songs.none { it.id == next.id } }
                                    canLoadMore = page.artist.size >= 20 || page.album.size >= 20 || page.song.size >= 20
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            Text("More results")
                        }
                    }
                }
                if (query.isNotBlank() &&
                    suggestionRows.isEmpty() &&
                    artists.isEmpty() &&
                    albums.isEmpty() &&
                    songs.isEmpty() &&
                    matchedGenres.isEmpty()
                ) {
                    item {
                        Text(
                            "Nothing matched “$query”",
                            color = p.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }
        }
    }

    val actionsSong = songForActions
    if (actionsSong != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { songForActions = null },
            sheetState = sheetState,
            containerColor = p.surface,
            contentColor = p.onBackground,
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
                Text(
                    actionsSong.title,
                    color = p.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
                if (!actionsSong.artist.isNullOrBlank()) {
                    Text(
                        actionsSong.artist,
                        color = p.onBackground.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                }
                SearchSongActionRow(
                    icon = Icons.Rounded.PlayArrow,
                    label = "Play song",
                    onClick = {
                        val songs = actionSongQueue
                        val index = songs.indexOfFirst { it.id == actionsSong.id }.coerceAtLeast(0)
                        player.play(if (songs.isNotEmpty()) songs else listOf(actionsSong), index)
                        songForActions = null
                    },
                )
                val artistId = actionsSong.artistId
                if (!artistId.isNullOrBlank()) {
                    SearchSongActionRow(
                        icon = Icons.Rounded.Person,
                        label = "Go to artist",
                        onClick = {
                            songForActions = null
                            onArtist(artistId)
                        },
                    )
                }
                val albumId = actionsSong.albumId
                if (!albumId.isNullOrBlank()) {
                    SearchSongActionRow(
                        icon = Icons.Rounded.Album,
                        label = "Go to album",
                        onClick = {
                            songForActions = null
                            onAlbum(albumId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSongActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = p.onBackground.copy(alpha = 0.75f))
        Spacer(Modifier.width(16.dp))
        Text(label, color = p.onBackground, fontSize = 16.sp)
    }
}

@Composable
private fun RecentQueryRow(query: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.History, null, tint = p.onBackground.copy(alpha = 0.45f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(query, color = p.onBackground, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SuggestionRow(row: SearchSuggestion) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = row.onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.coverArt != null) {
            CoverArt(row.coverArt, Modifier.size(40.dp), row.title, corner = 4.dp, fallback = row.icon)
        } else {
            Icon(row.icon, null, tint = p.onBackground.copy(alpha = 0.45f), modifier = Modifier.size(40.dp).padding(8.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, color = p.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
            if (!row.subtitle.isNullOrBlank()) {
                Text(row.subtitle, color = p.onBackground.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            }
        }
    }
}

private fun buildSuggestions(
    result: SearchResult3,
    onArtist: (String) -> Unit,
    onAlbum: (String) -> Unit,
    onSong: (Song, List<Song>) -> Unit,
): List<SearchSuggestion> {
    val rows = ArrayList<SearchSuggestion>(12)
    result.artist.take(4).forEach { artist ->
        rows += SearchSuggestion(
            key = "sug-a-${artist.id}",
            title = artist.name,
            subtitle = "Artist",
            icon = Icons.Rounded.Person,
            coverArt = artist.coverArt ?: artist.id,
            onClick = { onArtist(artist.id) },
        )
    }
    result.album.take(4).forEach { album ->
        rows += SearchSuggestion(
            key = "sug-al-${album.id}",
            title = album.displayName,
            subtitle = album.artist ?: "Album",
            icon = Icons.Rounded.Album,
            coverArt = album.coverArt,
            onClick = { onAlbum(album.id) },
        )
    }
    result.song.take(4).forEachIndexed { index, song ->
        rows += SearchSuggestion(
            key = "sug-s-${song.id}-$index",
            title = song.title,
            subtitle = song.artist ?: "Song",
            icon = Icons.Rounded.MusicNote,
            coverArt = song.coverArt,
            onClick = { onSong(song, result.song) },
        )
    }
    return rows.take(10)
}

private fun recentArtistsFromAlbums(albums: List<AlbumID3>): List<ArtistID3> {
    val seen = LinkedHashSet<String>()
    return albums.mapNotNull { album ->
        val id = album.artistId ?: return@mapNotNull null
        if (!seen.add(id)) null
        else ArtistID3(
            id = id,
            name = album.artist ?: "Artist",
            coverArt = album.coverArt,
            albumCount = 0,
        )
    }
}
