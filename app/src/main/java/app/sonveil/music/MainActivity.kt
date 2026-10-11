package app.sonveil.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.sonveil.music.ui.album.AlbumScreen
import app.sonveil.music.ui.album.AlbumsScreen
import app.sonveil.music.ui.artist.ArtistAlbumsScreen
import app.sonveil.music.ui.artist.ArtistScreen
import app.sonveil.music.ui.artist.PopularSongsScreen
import app.sonveil.music.ui.artists.ArtistsScreen
import app.sonveil.music.ui.home.FavoritesScreen
import app.sonveil.music.ui.home.HomeScreen
import app.sonveil.music.ui.login.LoginScreen
import app.sonveil.music.ui.player.NowPlayingHost
import app.sonveil.music.ui.playlist.PlaylistScreen
import app.sonveil.music.ui.search.SearchScreen
import app.sonveil.music.ui.settings.AppearancePrefs
import app.sonveil.music.ui.settings.SettingsScreen
import app.sonveil.music.ui.theme.SonveilMotion
import app.sonveil.music.ui.theme.SonveilTheme
import app.sonveil.music.ui.theme.LocalClient
import app.sonveil.music.ui.theme.LocalContainer
import app.sonveil.music.ui.theme.LocalPalette
import app.sonveil.music.ui.theme.LocalPlayer
import app.sonveil.music.ui.theme.LocalPlayerState
import app.sonveil.music.data.player.SonveilPalette
import app.sonveil.music.ui.theme.ThemeMode
import app.sonveil.music.ui.theme.sonveilGlass
import androidx.compose.runtime.CompositionLocalProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }
        val app = application as SonveilApp
        val container = app.container
        setContent {
            val prefs = remember { AppearancePrefs(this) }
            var themeMode by remember { mutableStateOf(prefs.themeMode) }
            var transcode by remember { mutableIntStateOf(prefs.transcode) }
            val playerState = container.player.state.collectAsStateWithLifecycle()
            val playerPalette by remember(playerState) {
                androidx.compose.runtime.derivedStateOf { playerState.value.palette }
            }
            val loggedIn by container.loggedIn.collectAsStateWithLifecycle()
            val authResolved by container.authResolved.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = when (themeMode) {
                ThemeMode.System -> systemDark
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
            }
            LaunchedEffect(dark) { container.player.setPreferDark(dark) }
            LaunchedEffect(transcode) { container.player.applyTranscode(transcode) }

            LaunchedEffect(Unit) {
                container.restoreSession()
            }

            val palette = playerPalette.let { pal ->
                if (pal.isDark == dark) pal
                else if (dark) SonveilPalette.darkDefault()
                else SonveilPalette.lightDefault()
            }

            CompositionLocalProvider(
                LocalContainer provides container,
                LocalClient provides container.client,
                LocalPlayer provides container.player,
                LocalPlayerState provides playerState,
            ) {
                SonveilTheme(palette = palette, themeMode = themeMode) {
                    val p = LocalPalette.current
                    SideEffect {
                        WindowCompat.getInsetsController(window, window.decorView)
                            .isAppearanceLightStatusBars = !p.isDark
                    }
                    // Login only after auth is resolved AND there is no session.
                    // Saved credentials → splash then app (never a Login flash/loop).
                    if (!authResolved) {
                        SessionSplash()
                    } else {
                        SonveilRoot(
                            loggedIn = loggedIn,
                            themeMode = themeMode,
                            onThemeMode = { themeMode = it; prefs.themeMode = it },
                            transcode = transcode,
                            onTranscode = {
                                transcode = it
                                prefs.transcode = it
                                container.player.applyTranscode(it)
                                val creds = container.credentials.load()
                                if (creds != null) container.credentials.save(creds.copy(transcodeBitrate = it))
                            },
                        )
                    }
                }
            }
        }
    }


}

@Composable
private fun SessionSplash() {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = p.onBackground)
    }
}

private data class Tab(val route: String, val label: String, val icon: Int)

private val tabs = listOf(
    Tab("home", "Home", R.drawable.ic_nav_home),
    Tab("artists", "Artists", R.drawable.ic_nav_artists),
    Tab("search", "Search", R.drawable.ic_nav_search),
    Tab("settings", "Settings", R.drawable.ic_nav_settings),
)

@Composable
private fun SonveilRoot(
    loggedIn: Boolean,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    transcode: Int,
    onTranscode: (Int) -> Unit,
) {
    // Discard saved tab stacks and their library state when the account changes.
    val nav = androidx.compose.runtime.key(loggedIn) { rememberNavController() }
    val p = LocalPalette.current
    val container = LocalContainer.current
    val playerState = LocalPlayerState.current
    val hasCurrentSong by remember(playerState) {
        androidx.compose.runtime.derivedStateOf { playerState.value.current != null }
    }
    val sheet = remember { mutableFloatStateOf(0f) }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val onTabs = route in tabs.map { it.route }
    val showNav = loggedIn && onTabs

    LaunchedEffect(loggedIn) {
        sheet.floatValue = 0f
        val current = nav.currentDestination?.route
        if (loggedIn && (current == "login" || current == null)) {
            nav.navigate("home") { popUpTo("login") { inclusive = true } }
        } else if (!loggedIn && current != null && current != "login") {
            nav.navigate("login") { popUpTo(0) { inclusive = true } }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (showNav) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val progress = sheet.floatValue
                                translationY = size.height * progress
                                alpha = 1f - progress
                            }
                            .sonveilGlass(p, 20.dp),
                    ) {
                        NavigationBar(
                            modifier = Modifier.height(56.dp),
                            containerColor = Color.Transparent,
                            contentColor = p.onBackground,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                        ) {
                            tabs.forEach { tab ->
                                val selected = route == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        nav.navigate(tab.route) {
                                            popUpTo("home") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            painter = painterResource(tab.icon),
                                            contentDescription = tab.label,
                                            modifier = Modifier.size(24.dp),
                                            tint = if (selected) p.primary else p.onBackground.copy(alpha = 0.38f),
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = p.primary,
                                        unselectedIconColor = p.onBackground.copy(alpha = 0.38f),
                                        indicatorColor = Color.Transparent,
                                    ),
                                )
                            }
                        }
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                                .background(Color.Transparent),
                        )
                    }
                }
            },
        ) { contentPadding ->
            NavHost(
                navController = nav,
                startDestination = if (loggedIn) "home" else "login",
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                enterTransition = {
                    fadeIn(animationSpec = SonveilMotion.fade()) +
                        slideInHorizontally(
                            animationSpec = SonveilMotion.emphasized(),
                            initialOffsetX = { it / 28 },
                        )
                },
                exitTransition = {
                    fadeOut(animationSpec = SonveilMotion.standard())
                },
                popEnterTransition = {
                    fadeIn(animationSpec = SonveilMotion.fade())
                },
                popExitTransition = {
                    fadeOut(animationSpec = SonveilMotion.standard()) +
                        slideOutHorizontally(
                            animationSpec = SonveilMotion.emphasized(),
                            targetOffsetX = { it / 28 },
                        )
                },
            ) {
                composable("login") { LoginScreen() }
                composable("home") {
                    HomeScreen(
                        onDownloads = { nav.navigate("downloads") },
                        onAlbums = { nav.navigate("albums") },
                        onFavorites = { nav.navigate("favorites") },
                        onPlaylist = { nav.navigate("playlist/${encode(it)}") },
                        onAlbum = { nav.navigate("album/${encode(it)}") },
                        onArtist = { nav.navigate("artist/${encode(it)}") },
                    )
                }
                composable("albums") {
                    AlbumsScreen(
                        onBack = { nav.popBackStack() },
                        onAlbum = { nav.navigate("album/${encode(it)}") },
                    )
                }
                composable("favorites") {
                    FavoritesScreen(
                        onBack = { nav.popBackStack() },
                        onAlbum = { nav.navigate("album/${encode(it)}") },
                        onArtist = { nav.navigate("artist/${encode(it)}") },
                    )
                }
                composable("downloads") { app.sonveil.music.ui.download.DownloadsScreen(onBack = { nav.popBackStack() }) }
                composable("artists") { ArtistsScreen(onArtist = { nav.navigate("artist/${encode(it)}") }) }
                composable("search") {
                    SearchScreen(
                        onArtist = { nav.navigate("artist/${encode(it)}") },
                        onAlbum = { nav.navigate("album/${encode(it)}") },
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        themeMode = themeMode,
                        onThemeMode = onThemeMode,
                        transcode = transcode,
                        onTranscode = onTranscode,
                        onLoggedOut = {
                            sheet.floatValue = 0f
                        },
                    )
                }
                composable(
                    "artist/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    val id = entry.arguments?.getString("id") ?: return@composable
                    ArtistScreen(
                        artistId = id,
                        onBack = { nav.popBackStack() },
                        onAlbum = { nav.navigate("album/${encode(it)}") },
                        onPopular = { artistId, name -> nav.navigate("artist/${encode(artistId)}/popular/${encode(name)}") },
                        onAlbums = { artistId, name -> nav.navigate("artist/${encode(artistId)}/albums/${encode(name)}") },
                        onArtist = { nav.navigate("artist/${encode(it)}") },
                    )
                }
                composable(
                    "artist/{id}/popular/{name}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType },
                    ),
                ) { entry ->
                    PopularSongsScreen(
                        artistId = entry.arguments?.getString("id") ?: return@composable,
                        artistName = entry.arguments?.getString("name").orEmpty(),
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(
                    "artist/{id}/albums/{name}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType },
                    ),
                ) { entry ->
                    ArtistAlbumsScreen(
                        artistId = entry.arguments?.getString("id") ?: return@composable,
                        artistName = entry.arguments?.getString("name").orEmpty(),
                        onBack = { nav.popBackStack() },
                        onAlbum = { nav.navigate("album/${encode(it)}") },
                    )
                }
                composable(
                    "album/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    AlbumScreen(
                        albumId = entry.arguments?.getString("id") ?: return@composable,
                        onBack = { nav.popBackStack() },
                        onArtist = { nav.navigate("artist/${encode(it)}") },
                    )
                }
                composable(
                    "playlist/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    PlaylistScreen(
                        playlistId = entry.arguments?.getString("id") ?: return@composable,
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }

        if (loggedIn && hasCurrentSong) {
            NowPlayingHost(
                sheet = sheet,
                onArtist = { nav.navigate("artist/${encode(it)}") },
                bottomNavVisible = onTabs,
            )
        }
    }
}

private fun encode(value: String): String = android.net.Uri.encode(value)
