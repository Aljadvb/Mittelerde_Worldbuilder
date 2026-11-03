package io.github.aljadvb.worldbuilder.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aljadvb.worldbuilder.R
import java.io.File

/** Parchment and ink: one look for the whole app (also in the system's dark mode). */
private val Parchment = lightColorScheme(
    primary = Color(0xFF5B2A17), onPrimary = Color(0xFFF6EDD6),
    primaryContainer = Color(0xFFE6CFA3), onPrimaryContainer = Color(0xFF3D1C0F),
    secondary = Color(0xFF7A5A2E), onSecondary = Color(0xFFF6EDD6),
    secondaryContainer = Color(0xFFE9D9B4), onSecondaryContainer = Color(0xFF3A2A12),
    tertiary = Color(0xFF3F5A3A), onTertiary = Color(0xFFF6EDD6),
    background = Color(0xFFEFE3C6), onBackground = Color(0xFF2B2117),
    surface = Color(0xFFF4EAD2), onSurface = Color(0xFF2B2117),
    surfaceVariant = Color(0xFFE6D6B2), onSurfaceVariant = Color(0xFF5E4C35),
    surfaceContainerLowest = Color(0xFFFAF3E2), surfaceContainerLow = Color(0xFFF6EDD6),
    surfaceContainer = Color(0xFFF1E5C9), surfaceContainerHigh = Color(0xFFEBDDBD), surfaceContainerHighest = Color(0xFFE5D5B1),
    outline = Color(0xFF8F7340), outlineVariant = Color(0xFFC5AB74),
    error = Color(0xFF8E2A1C), onError = Color(0xFFF6EDD6),
    inverseSurface = Color(0xFF3A2E22), inverseOnSurface = Color(0xFFF3E9D2), inversePrimary = Color(0xFFE0B98A),
)

private val Garamond = FontFamily(
    Font(R.font.eb_garamond_regular, FontWeight.Normal),
    Font(R.font.eb_garamond_semibold, FontWeight.SemiBold),
    Font(R.font.eb_garamond_semibold, FontWeight.Bold),
    Font(R.font.eb_garamond_italic, FontWeight.Normal, FontStyle.Italic),
)

/** Alcarin Tengwar (OFL) for Elven inscriptions. */
val TengwarFamily = FontFamily(Font(R.font.alcarin_tengwar, FontWeight.Normal))

/** Optional ornament picture font from the private catalog; null if the catalog brings none. */
val LocalOrnamentFont = androidx.compose.runtime.staticCompositionLocalOf<FontFamily?> { null }

/** Uncial Antiqua (OFL): uncial letters with full German character set, the default title font. */
private val Uncial = FontFamily(Font(R.font.uncial_antiqua, FontWeight.Normal), Font(R.font.uncial_antiqua, FontWeight.Bold))

/** Titles and buttons in [display], running text in EB Garamond. */
private fun typography(display: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.disp() = copy(fontFamily = display, fontWeight = FontWeight.Normal)
    fun TextStyle.body(size: Int? = null) = copy(fontFamily = Garamond, fontSize = size?.sp ?: fontSize, lineHeight = (size ?: fontSize.value.toInt()).let { (it * 1.35).sp })
    return base.copy(
        displayLarge = base.displayLarge.disp(), displayMedium = base.displayMedium.disp(), displaySmall = base.displaySmall.disp(),
        headlineLarge = base.headlineLarge.disp(), headlineMedium = base.headlineMedium.disp(), headlineSmall = base.headlineSmall.disp(),
        titleLarge = base.titleLarge.disp(), titleMedium = base.titleMedium.disp(), titleSmall = base.titleSmall.disp(),
        labelLarge = base.labelLarge.disp(), labelMedium = base.labelMedium.body(13), labelSmall = base.labelSmall.body(12),
        bodyLarge = base.bodyLarge.body(18), bodyMedium = base.bodyMedium.body(16), bodySmall = base.bodySmall.body(14),
    )
}

private val Corners = Shapes(
    extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(3.dp), medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp), extraLarge = RoundedCornerShape(8.dp),
)

/**
 * App theme. [titleFont] is an optional font file from the user's private catalog
 * (e.g. a Middle-earth style font); without it Uncial Antiqua is used for titles and buttons.
 */
@Composable
fun WorldbuilderTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    titleFont: File? = null,
    ornamentFont: File? = null,
    content: @Composable () -> Unit,
) {
    val ornaments = remember(ornamentFont?.path, ornamentFont?.lastModified()) {
        ornamentFont?.takeIf { it.isFile }?.let { f -> runCatching { FontFamily(Font(f)) }.getOrNull() }
    }
    val display = remember(titleFont?.path, titleFont?.lastModified()) {
        titleFont?.takeIf { it.isFile }?.let { f -> runCatching { FontFamily(Font(f)) }.getOrNull() } ?: Uncial
    }
    val type = remember(display) { typography(display) }
    androidx.compose.runtime.CompositionLocalProvider(LocalOrnamentFont provides ornaments) {
        MaterialTheme(colorScheme = Parchment, typography = type, shapes = Corners, content = content)
    }
}

/**
 * A picture from the ornament font: "band" (inscription band), "ring", "swords" (two swords
 * pointing at each other). Draws nothing if the catalog brings no ornament font.
 */
@Composable
fun Ornament(kind: String, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier, size: Int = 34) {
    val font = LocalOrnamentFont.current ?: return
    val glyphs = when (kind) { "band" -> "u"; "ring" -> "a"; "swords" -> "w x"; else -> return }
    androidx.compose.material3.Text(glyphs, modifier = modifier, fontFamily = font, fontSize = size.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
