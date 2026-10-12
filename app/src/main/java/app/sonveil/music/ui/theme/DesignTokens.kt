package app.sonveil.music.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

object SonveilSize {
    val Action = 48.dp
    val PageGutter = 20.dp
    val PlayerGutter = 24.dp
    val MiniPlayer = 72.dp
    val MiniGap = 12.dp
    val Navigation = 80.dp
}

/** Additional space occupied by the overlay, beyond Scaffold's own navigation/system insets. */
val LocalPlayerContentInset = staticCompositionLocalOf { 0.dp }
