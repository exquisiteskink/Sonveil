package app.sonveil.music.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sonveil.music.AppContainer
import app.sonveil.music.data.player.SonveilPalette
import app.sonveil.music.data.player.PlayerController
import app.sonveil.music.data.player.PlayerUiState
import app.sonveil.music.data.remote.SubsonicClient

val LocalPalette = staticCompositionLocalOf { SonveilPalette.darkDefault() }
val LocalClient = staticCompositionLocalOf<SubsonicClient> { error("SubsonicClient not provided") }
val LocalPlayer = staticCompositionLocalOf<PlayerController> { error("PlayerController not provided") }
val LocalPlayerState = staticCompositionLocalOf<State<PlayerUiState>> { error("Player state not provided") }
val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

private val SonveilTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.4).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = (-0.2).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.8.sp),
)

enum class ThemeMode { System, Dark, Light }

@Composable
fun SonveilTheme(
    palette: SonveilPalette,
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    // The palette (hoisted from PlayerController: album art, or the static app scheme)
    // is the single source of truth. MaterialTheme gets *every* role from it, so stock
    // Material components (TextButton, Switch, Slider, RadioButton, menus, sheets, text
    // fields) follow the art too instead of falling back to Material's baseline purple.
    val scheme = remember(palette, dark) { palette.toColorScheme(dark) }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = SonveilTypography) {
            Box(Modifier.fillMaxSize()) {
                UltraBlurBackground(Modifier.fillMaxSize())
                content()
            }
        }
    }
}

/**
 * Maps the art-derived [SonveilPalette] (+ its tonal [app.sonveil.music.data.player.ArtScheme])
 * to a complete Material 3 [ColorScheme]. Page/surface colors keep the art blur surfaces the
 * app already draws; accent and container roles come from the contrast-checked tonal scheme.
 */
fun SonveilPalette.toColorScheme(dark: Boolean = isDark): ColorScheme {
    val s = scheme
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(s.primary),
        onPrimary = Color(s.onPrimary),
        primaryContainer = Color(s.primaryContainer),
        onPrimaryContainer = Color(s.onPrimaryContainer),
        inversePrimary = Color(s.inversePrimary),
        secondary = Color(s.secondary),
        onSecondary = Color(s.onSecondary),
        secondaryContainer = Color(s.secondaryContainer),
        onSecondaryContainer = Color(s.onSecondaryContainer),
        tertiary = Color(s.tertiary),
        onTertiary = Color(s.onTertiary),
        tertiaryContainer = Color(s.tertiaryContainer),
        onTertiaryContainer = Color(s.onTertiaryContainer),
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = Color(s.onSurfaceVariant),
        surfaceTint = Color(s.primary),
        inverseSurface = Color(s.inverseSurface),
        inverseOnSurface = Color(s.inverseOnSurface),
        outline = Color(s.outline),
        outlineVariant = Color(s.outlineVariant),
        surfaceBright = Color(s.surfaceBright),
        surfaceDim = Color(s.surfaceDim),
        surfaceContainerLowest = Color(s.surfaceContainerLowest),
        surfaceContainerLow = Color(s.surfaceContainerLow),
        surfaceContainer = Color(s.surfaceContainer),
        surfaceContainerHigh = Color(s.surfaceContainerHigh),
        surfaceContainerHighest = Color(s.surfaceContainerHighest),
    )
}

/** Seek bar / progress colors, shared by the Now Playing seek bar and the mini player. */
data class SeekColors(val active: Color, val inactive: Color, val thumb: Color)

val SonveilPalette.seekColors: SeekColors
    get() = SeekColors(
        active = Color(scheme.seekActive),
        inactive = Color(scheme.seekInactive),
        thumb = Color(scheme.seekActive),
    )

/**
 * Slider colors for every Settings/EQ slider: thumb + active track take the art primary from
 * MaterialTheme; the inactive track uses the same translucent token as the seek bar so it
 * stays visible on glass cards (Material's default secondaryContainer can vanish there).
 */
@Composable
fun sonveilSliderColors(): SliderColors {
    val seek = LocalPalette.current.seekColors
    return SliderDefaults.colors(
        inactiveTrackColor = seek.inactive,
        inactiveTickColor = seek.active.copy(alpha = 0.6f),
    )
}

@Composable
private fun AnimatedUltraBlurLayer(
    background: State<Color>,
    blurA: State<Color>,
    blurB: State<Color>,
    blurC: State<Color>,
    dark: Boolean,
    modifier: Modifier,
) {
    // Read animation state during drawing so palette transitions don't recompose
    // the navigation tree on every animation frame.
    Canvas(modifier.fillMaxSize()) {
        drawRect(background.value)
        drawUltraBlur(blurA.value, blurB.value, blurC.value, dark)
    }
}

@Composable
fun UltraBlurLayer(
    blurA: Color,
    blurB: Color,
    blurC: Color,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxSize()) {
        drawUltraBlur(blurA, blurB, blurC, dark)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawUltraBlur(
    blurA: Color,
    blurB: Color,
    blurC: Color,
    dark: Boolean,
) {
    val w = size.width
    val h = size.height
    val r = maxOf(w, h)
    drawRect(
        Brush.radialGradient(
            colors = listOf(blurA.copy(alpha = if (dark) 0.90f else 0.88f), Color.Transparent),
            center = Offset(w * 0.18f, h * 0.12f),
            radius = r * 0.85f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(blurB.copy(alpha = if (dark) 0.75f else 0.80f), Color.Transparent),
            center = Offset(w * 0.92f, h * 0.38f),
            radius = r * 0.80f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(blurC.copy(alpha = if (dark) 0.88f else 0.86f), Color.Transparent),
            center = Offset(w * 0.45f, h * 1.05f),
            radius = r * 0.95f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(blurA.copy(alpha = if (dark) 0.40f else 0.50f), Color.Transparent),
            center = Offset(w * 0.70f, h * 0.08f),
            radius = r * 0.45f,
        ),
    )
    drawRect(Color.Black.copy(alpha = if (dark) 0.28f else 0.06f))
}

@Composable
fun UltraBlurBackground(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val spec = SonveilMotion.emphasized<Color>(SonveilMotion.DurationPaletteMs)
    val bg = animateColorAsState(p.background, spec, label = "background")
    val a = animateColorAsState(p.blurA, spec, label = "blurA")
    val b = animateColorAsState(p.blurB, spec, label = "blurB")
    val c = animateColorAsState(p.blurC, spec, label = "blurC")
    AnimatedUltraBlurLayer(bg, a, b, c, p.isDark, modifier)
}

/** Translucent, edge-lit surface shared by player chrome and settings cards. */
fun Modifier.sonveilGlass(
    palette: SonveilPalette,
    radius: Dp,
    opaque: Boolean = false,
): Modifier {
    val shape = RoundedCornerShape(radius)
    val fill = Brush.verticalGradient(
        listOf(
            palette.surfaceHigh.copy(alpha = if (opaque) 1f else if (palette.isDark) 0.70f else 0.82f),
            palette.surface.copy(alpha = if (opaque) 1f else if (palette.isDark) 0.48f else 0.68f),
        ),
    )
    val edge = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (palette.isDark) 0.30f else 0.72f),
            palette.primary.copy(alpha = 0.13f),
            Color.White.copy(alpha = if (palette.isDark) 0.08f else 0.22f),
        ),
    )
    return clip(shape).background(fill).border(1.dp, edge, shape)
}
