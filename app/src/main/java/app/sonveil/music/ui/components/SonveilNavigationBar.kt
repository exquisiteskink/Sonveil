package app.sonveil.music.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.sonveil.music.R
import app.sonveil.music.ui.theme.LocalPalette
import app.sonveil.music.ui.theme.SonveilSize
import app.sonveil.music.ui.theme.textSecondary

internal data class NavigationTab(val route: String, val label: String, val icon: Int)
internal val navigationTabs = listOf(
    NavigationTab("home", "Home", R.drawable.ic_nav_home),
    NavigationTab("artists", "Artists", R.drawable.ic_nav_artists),
    NavigationTab("search", "Search", R.drawable.ic_nav_search),
    NavigationTab("settings", "Settings", R.drawable.ic_nav_settings),
)

@Composable
internal fun SonveilNavigationBar(route: String?, onNavigate: (String) -> Unit) {
    val p = LocalPalette.current
    NavigationBar(modifier = Modifier.height(SonveilSize.Navigation), containerColor = Color.Transparent,
        contentColor = p.onBackground, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
        navigationTabs.forEach { tab ->
            NavigationBarItem(selected = route == tab.route, onClick = { onNavigate(tab.route) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null, modifier = Modifier.size(24.dp)) },
                label = { Text(tab.label) }, alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(selectedIconColor = p.primary,
                    selectedTextColor = p.onBackground, unselectedIconColor = p.textSecondary,
                    unselectedTextColor = p.textSecondary, indicatorColor = Color.Transparent))
        }
    }
}
