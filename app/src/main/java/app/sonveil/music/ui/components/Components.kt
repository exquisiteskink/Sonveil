package app.sonveil.music.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.scale
import androidx.compose.ui.composed
import app.sonveil.music.ui.theme.AuralisMotion
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sonveil.music.data.remote.AlbumID3
import app.sonveil.music.data.remote.ArtistID3
import app.sonveil.music.data.remote.Genre
import app.sonveil.music.data.remote.Playlist
import app.sonveil.music.data.remote.Song
import app.sonveil.music.data.remote.formatDuration
import app.sonveil.music.ui.theme.LocalClient
import app.sonveil.music.ui.theme.LocalPalette
import app.sonveil.music.ui.theme.LocalPlayer
import app.sonveil.music.ui.theme.LocalPlayerState
import app.sonveil.music.ui.theme.sonveilGlass
import app.sonveil.music.data.remote.allowedArtworkUrl
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext


/** Soft press scale — keeps ripple via default [clickable] indication. */
private fun Modifier.pressScale(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = AuralisMotion.standard(AuralisMotion.DurationPressMs),
        label = "card-press",
    )
    this
        .scale(scale)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    radius: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Column(
        modifier = modifier
            .sonveilGlass(p, radius)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun CoverArt(
    coverId: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    corner: Dp = 8.dp,
    fallback: ImageVector = Icons.Rounded.Album,
    imageUrl: String? = null,
    retainPreviousOnChange: Boolean = false,
    localUri: Uri? = null,
    localCacheKey: String? = null,
) {
    val client = LocalClient.current
    val context = LocalContext.current
    val p = LocalPalette.current
    val credentials = client.credentials
    val sources = remember(client, credentials, coverId, imageUrl, localUri) {
        val url = allowedArtworkUrl(imageUrl, credentials?.serverUrl)
        listOfNotNull(localUri, url, client.coverUrl(coverId, 600)).distinct()
    }
    var sourceIndex by remember(sources, localCacheKey) { mutableStateOf(0) }
    val source = sources.getOrNull(sourceIndex)
    var imageLoaded by remember(source, localCacheKey) { mutableStateOf(false) }
    val request = remember(context, source, localUri, localCacheKey) {
        ImageRequest.Builder(context)
            .data(source)
            .apply { if (source != null && source == localUri) memoryCacheKey(localCacheKey) }
            .build()
    }
    val shape = if (corner >= 48.dp) CircleShape else RoundedCornerShape(corner)
    var previousPainter by remember(credentials, retainPreviousOnChange) { mutableStateOf<Painter?>(null) }
    Box(
        modifier
            .clip(shape)
            .background(p.surfaceHigh.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageLoaded && (!retainPreviousOnChange || previousPainter == null)) {
            Icon(fallback, if (source == null) contentDescription else null,
                tint = p.onSurface.copy(alpha = 0.35f), modifier = Modifier.size(36.dp))
        }
        if (source != null) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = if (retainPreviousOnChange) previousPainter else null,
                onSuccess = {
                    imageLoaded = true
                    if (retainPreviousOnChange) previousPainter = it.painter
                },
                onError = {
                    previousPainter = null
                    sourceIndex += 1
                },
            )
        }
    }
}

@Composable
fun ScreenTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = p.onBackground)
        }
        Text(
            title,
            color = p.onBackground,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
fun PageTitle(title: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text(
        title,
        color = p.onBackground,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title.uppercase(),
                color = p.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.1.sp,
                modifier = Modifier.weight(1f),
            )
            if (onClick != null) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "See all",
                    tint = p.onBackground.copy(alpha = 0.7f),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.onBackground.copy(alpha = 0.22f)))
    }
}

@Composable
fun AlbumCard(
    album: AlbumID3,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 148.dp,
) {
    val p = LocalPalette.current
    Column(
        modifier
            .width(width)
            .pressScale(onClick),
    ) {
        CoverArt(album.coverArt, Modifier.fillMaxWidth().aspectRatio(1f), album.displayName, corner = 6.dp)
        Spacer(Modifier.height(8.dp))
        Text(album.displayName, color = p.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Text(album.artist.orEmpty(), color = p.onBackground.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
    }
}

@Composable
fun ArtistTile(
    artist: ArtistID3,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
) {
    val p = LocalPalette.current
    val client = LocalClient.current
    Column(modifier.pressScale(onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        CoverArt(
            coverId = artist.coverArt ?: artist.id,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentDescription = artist.name,
            corner = if (circular) 999.dp else 6.dp,
            fallback = Icons.Rounded.Person,
            imageUrl = artist.artistImageUrl ?: client.coverUrl(artist.coverArt ?: artist.id, 600),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            artist.name,
            color = p.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HorizontalAlbums(
    albums: List<AlbumID3>,
    onAlbum: (AlbumID3) -> Unit,
) {
    val listState = rememberLazyListState()
    val snap = rememberSnapFlingBehavior(lazyListState = listState)
    LazyRow(
        state = listState,
        flingBehavior = snap,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCard(album, onClick = { onAlbum(album) })
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    showArtist: Boolean = true,
    showCover: Boolean = true,
    playing: Boolean = false,
) {
    val p = LocalPalette.current
    val player = LocalPlayer.current
    val playerState = LocalPlayerState.current
    // Position ticks and changes to other songs must not recompose every row.
    val favorite by remember(playerState, song) {
        derivedStateOf { playerState.value.isFavorite(song) }
    }
    val current by remember(playerState, song.id) {
        derivedStateOf { playerState.value.current?.id == song.id }
    }
    var menu by remember(song.id) { mutableStateOf(false) }
    val isPlaying = playing || current
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank != null) {
            Text(
                rank.toString(),
                color = p.onBackground.copy(alpha = if (isPlaying) 0.9f else 0.45f),
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                modifier = Modifier.width(28.dp),
            )
        }
        if (showCover) {
            CoverArt(song.coverArt, Modifier.size(48.dp), song.title, corner = 4.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = p.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 16.sp,
            )
            if (showArtist && !song.artist.isNullOrBlank()) {
                Text(
                    song.artist,
                    color = p.onBackground.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                )
            }
            Text(
                formatDuration(song.duration),
                color = p.onBackground.copy(alpha = 0.45f),
                fontSize = 13.sp,
            )
        }
        if (favorite) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = "Favorite",
                tint = p.primary,
                modifier = Modifier.padding(end = 6.dp).size(16.dp),
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = p.onBackground.copy(alpha = 0.45f))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Play next") },
                    onClick = { menu = false; player.playNext(song) },
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    onClick = { menu = false; player.addToQueue(song) },
                )
                DropdownMenuItem(
                    text = { Text(if (favorite) "Remove from favorites" else "Add to favorites") },
                    onClick = {
                        menu = false
                        player.setFavorite(song, !favorite)
                    },
                )
            }
        }
    }
}

@Composable
fun PlayFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    HeaderPlay(onClick = onClick, modifier = modifier, size = 56.dp)
}

@Composable
fun HeaderPlay(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val p = LocalPalette.current
    Box(
        modifier
            .size(size)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(p.playButton)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = "Play",
            tint = p.onPlayButton,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

@Composable
fun MixOrb(
    title: String,
    subtitle: String,
    coverId: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    Column(
        modifier
            .width(108.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverArt(coverId, Modifier.size(96.dp).clip(CircleShape), title, corner = 48.dp)
        Spacer(Modifier.height(8.dp))
        Text(title, color = p.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        Text(subtitle, color = p.onBackground.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
    }
}

@Composable
fun GenreChip(genre: Genre, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LibraryChip(genre.value.ifBlank { "Genre" }, onClick, modifier)
}

@Composable
fun PlaylistChip(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LibraryChip(playlist.name.ifBlank { "Playlist" }, onClick, modifier)
}

@Composable
private fun LibraryChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text(
        label,
        color = p.onBackground,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        modifier = modifier
            .sonveilGlass(p, 50.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
fun ArtistListRow(artist: ArtistID3, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val client = LocalClient.current
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            coverId = artist.coverArt ?: artist.id,
            modifier = Modifier.size(56.dp),
            contentDescription = artist.name,
            corner = 28.dp,
            fallback = Icons.Rounded.Person,
            imageUrl = artist.artistImageUrl ?: client.coverUrl(artist.coverArt ?: artist.id, 300),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(artist.name, color = p.onBackground, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (artist.albumCount > 0) {
                Text("${artist.albumCount} albums", color = p.onBackground.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun AlbumListRow(album: AlbumID3, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(album.coverArt, Modifier.size(48.dp), album.displayName, corner = 3.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(album.displayName, color = p.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
            if (album.year > 0) {
                Text(album.year.toString(), color = p.onBackground.copy(alpha = 0.5f), fontSize = 13.sp)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = p.onBackground.copy(alpha = 0.45f))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Open album") },
                    onClick = { menu = false; onClick() },
                )
            }
        }
    }
}

@Composable
fun ErrorText(message: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text(message, color = p.onBackground.copy(alpha = 0.7f), modifier = modifier.padding(24.dp))
}

@Composable
fun SongRowSkeleton() {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp).height(12.dp).clip(RoundedCornerShape(2.dp)).background(p.surfaceHigh.copy(alpha = 0.5f)))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.7f).height(12.dp).clip(RoundedCornerShape(2.dp)).background(p.surfaceHigh.copy(alpha = 0.55f)))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(0.3f).height(10.dp).clip(RoundedCornerShape(2.dp)).background(p.surfaceHigh.copy(alpha = 0.4f)))
        }
    }
}
