package org.cyclingcommons.scout.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cyclingcommons.scout.R

/**
 * Scout's palette for one appearance. Read the active one as [ScoutColors].
 *
 * Neither appearance is the "correct" one for a bar-mounted phone. Under direct sun
 * a dark field has too little emitted light to beat the ambient and the screen turns
 * into a mirror, so light reads better; at dusk and on OLED, dark is easier on the
 * eyes and on the battery (SPEC §12.1 — the screen is optional burn). The rider picks.
 */
@Immutable
data class ScoutColorScheme(
    /** Brand vermillion, sampled from `Brand/Scout-logo`. Identical in both appearances. */
    val Brand: Color,
    val BrandDim: Color,
    val Recording: Color,
    val Screen: Color,
    val Surface: Color,
    val SurfaceRaised: Color,
    val Outline: Color,
    /** For controls that must read as interactive against a card, not as a divider. */
    val OutlineStrong: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextOnBrand: Color,
    /** Ink for a filled tile whose normative hue is too pale to carry white (SAND). */
    val TextOnPale: Color,
    val Warning: Color,
    /** How much of a tile's normative hue shows through before the tile is lit. */
    val tileIdleAlpha: Float,
)

private val Vermillion = Color(0xFFF0321C)
private val Ink = Color(0xFF14141A)

/** Keep [Screen] in step with `@color/screen_background_dark`. */
private val DarkColors = ScoutColorScheme(
    Brand = Vermillion,
    BrandDim = Color(0xFF8C1E10),
    Recording = Color(0xFF2E8B57),
    Screen = Color(0xFF0B0B0C),
    Surface = Color(0xFF161618),
    SurfaceRaised = Color(0xFF202024),
    Outline = Color(0xFF303036),
    OutlineStrong = Color(0xFF6A6A74),
    TextPrimary = Color(0xFFF5F5F6),
    TextSecondary = Color(0xFFA6A6AD),
    TextOnBrand = Color.White,
    TextOnPale = Ink,
    Warning = Color(0xFFE8A33D),
    tileIdleAlpha = 0.18f,
)

/** Keep [Screen] in step with `@color/screen_background_light`. */
private val LightColors = ScoutColorScheme(
    Brand = Vermillion,
    BrandDim = Color(0xFFB32410),
    Recording = Color(0xFF1B7A45),
    Screen = Color(0xFFF4F4F1),
    Surface = Color(0xFFFFFFFF),
    SurfaceRaised = Color(0xFFE6E6E2),
    Outline = Color(0xFFD6D6D0),
    OutlineStrong = Color(0xFF8A8A82),
    TextPrimary = Ink,
    TextSecondary = Color(0xFF55555E),
    TextOnBrand = Color.White,
    TextOnPale = Ink,
    Warning = Color(0xFF9A5B00),
    tileIdleAlpha = 0.16f,
)

private val LocalScoutColors = staticCompositionLocalOf { DarkColors }

/** The palette of the appearance in force. Valid inside [ScoutTheme]. */
val ScoutColors: ScoutColorScheme
    @Composable
    @ReadOnlyComposable
    get() = LocalScoutColors.current

/** What the rider chose in Settings; [SYSTEM] hands the decision to the phone. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    @Composable
    fun isDark(): Boolean = when (this) {
        SYSTEM -> isSystemInDarkTheme()
        LIGHT -> false
        DARK -> true
    }
}

/** 8-point rhythm; every gap and inset in the app comes from here. */
object ScoutSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object ScoutDimens {
    /** Comfortable for a gloved thumb on a moving bike. */
    val controlHeight = 52.dp
    val tileCorner = 14.dp
    val cardCorner = 18.dp
    val photoThumb = 72.dp
}

private val Quicksand = FontFamily(
    Font(R.font.quicksand_medium, FontWeight.Medium),
    Font(R.font.quicksand_bold, FontWeight.Bold),
)

/** Condensed: fits "CONCRETE" and big numbers into a tile without shrinking type. */
private val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

/** Tile labels, tallies and the radar readout — the glanceable layer. */
object ScoutType {
    val tileLabel = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 0.8.sp,
    )
    val tileCount = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
    )
    val countdown = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp,
    )
    val rideClock = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = 1.sp,
        fontFeatureSettings = "tnum",
    )
    val metric = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
    )
    val overline = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 1.4.sp,
    )
}

private val typography = Typography().run {
    val display = TextStyle(fontFamily = Quicksand, fontWeight = FontWeight.Bold)
    val body = TextStyle(fontFamily = Quicksand, fontWeight = FontWeight.Medium)
    copy(
        headlineMedium = headlineMedium.merge(display).copy(fontSize = 26.sp),
        headlineSmall = headlineSmall.merge(display).copy(fontSize = 22.sp),
        titleLarge = titleLarge.merge(display).copy(fontSize = 20.sp),
        titleMedium = titleMedium.merge(display).copy(fontSize = 16.sp),
        bodyLarge = bodyLarge.merge(body).copy(fontSize = 16.sp),
        bodyMedium = bodyMedium.merge(body).copy(fontSize = 14.sp),
        bodySmall = bodySmall.merge(body).copy(fontSize = 12.sp),
        labelLarge = labelLarge.merge(display).copy(fontSize = 15.sp),
    )
}

/** Material's own scheme, so stock components land on the Scout palette too. */
private fun materialScheme(c: ScoutColorScheme, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.Brand,
        onPrimary = c.TextOnBrand,
        primaryContainer = c.BrandDim,
        onPrimaryContainer = c.TextOnBrand,
        secondary = c.SurfaceRaised,
        onSecondary = c.TextPrimary,
        secondaryContainer = c.SurfaceRaised,
        onSecondaryContainer = c.TextPrimary,
        background = c.Screen,
        onBackground = c.TextPrimary,
        surface = c.Surface,
        onSurface = c.TextPrimary,
        surfaceVariant = c.SurfaceRaised,
        onSurfaceVariant = c.TextSecondary,
        outline = c.Outline,
        outlineVariant = c.Outline,
        error = c.Brand,
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(ScoutDimens.tileCorner),
    large = RoundedCornerShape(ScoutDimens.cardCorner),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ScoutTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalScoutColors provides colors) {
        MaterialTheme(
            colorScheme = materialScheme(colors, dark),
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}
