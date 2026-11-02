package com.notiz.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notiz.app.resources.*
import org.jetbrains.compose.resources.Font

/**
 * "Neighbourly" — the Notiz look (see the design canvas): deep teal for trust and actions,
 * marigold for "due soon" and pinned items, white cards on a soft grey-green ground.
 * Light only for now; every colour below was picked to pass 4.5:1 contrast for text.
 */
object Palette {
    val teal = Color(0xFF0E6B57)
    val tealDark = Color(0xFF0A5446)
    val tealMid = Color(0xFF1C7C67)
    val tealOnDark = Color(0xFFCFE8DF)
    val tealSoft = Color(0xFFE3F1EC)

    val marigold = Color(0xFFF4A62A)
    val onMarigold = Color(0xFF2B1A00)
    val amberText = Color(0xFF8A5300)
    val amberSoft = Color(0xFFFFF1DC)

    val blue = Color(0xFF2B59C3)
    val blueText = Color(0xFF1F4FA8)
    val blueSoft = Color(0xFFE7EEFB)
    val purple = Color(0xFF6B3FB5)
    val purpleSoft = Color(0xFFF1ECFA)
    val red = Color(0xFFB42318)
    val redSoft = Color(0xFFFDECEA)
    val orange = Color(0xFFE8590C) // unread dots and count badges

    val ink = Color(0xFF14201C)
    val inkSoft = Color(0xFF3C4A45)
    val muted = Color(0xFF5A6B65)
    val line = Color(0xFFE2E8E5)
    val lineSoft = Color(0xFFEEF2F0)
    val ground = Color(0xFFF4F6F5)
    val track = Color(0xFFE6ECE9)
    val photo = Color(0xFFDCE7E2)
    val chip = Color(0xFFEFF3F1) // soft filled buttons and poll options
}

private val LightColors = lightColorScheme(
    primary = Palette.teal,
    onPrimary = Color.White,
    primaryContainer = Palette.tealSoft,
    onPrimaryContainer = Palette.tealDark,
    secondary = Palette.marigold,
    onSecondary = Palette.onMarigold,
    secondaryContainer = Palette.amberSoft,
    onSecondaryContainer = Palette.amberText,
    tertiary = Palette.blue,
    background = Palette.ground,
    onBackground = Palette.ink,
    surface = Color.White,
    onSurface = Palette.ink,
    surfaceVariant = Palette.track,
    onSurfaceVariant = Palette.muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Palette.track,
    outline = Color(0xFFB9C5C0),
    outlineVariant = Palette.line,
    error = Palette.red,
    errorContainer = Palette.redSoft,
    onErrorContainer = Palette.red,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp), // text fields
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Status colours used for dues and complaints chips (text colour; the chip tints it). */
object StatusColors {
    val success = Palette.teal
    val warning = Palette.amberText
    val info = Palette.blueText
}

/**
 * Inter (bundled in composeResources/font, SIL Open Font License). Tamil text falls back to the
 * phone's Tamil font automatically.
 */
@Composable
private fun appFontFamily() = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
    Font(Res.font.inter_extrabold, FontWeight.ExtraBold),
)

@Composable
private fun appTypography(): Typography {
    val f = appFontFamily()
    fun style(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0) =
        TextStyle(fontFamily = f, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = spacing.sp)
    return Typography(
        displaySmall = style(36, FontWeight.ExtraBold, 42, -1.0),
        headlineLarge = style(32, FontWeight.ExtraBold, 38, -0.8),
        headlineMedium = style(24, FontWeight.ExtraBold, 30, -0.5),
        headlineSmall = style(22, FontWeight.ExtraBold, 28, -0.3),
        titleLarge = style(19, FontWeight.Bold, 24),
        titleMedium = style(16, FontWeight.Bold, 22),
        titleSmall = style(14, FontWeight.Bold, 20),
        bodyLarge = style(16, FontWeight.Normal, 23),
        bodyMedium = style(14, FontWeight.Normal, 21),
        bodySmall = style(13, FontWeight.Normal, 18),
        labelLarge = style(15, FontWeight.Bold, 20),
        labelMedium = style(13, FontWeight.SemiBold, 18),
        labelSmall = style(11, FontWeight.SemiBold, 14),
    )
}

@Composable
fun NotizTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        shapes = AppShapes,
        typography = appTypography(),
        content = content,
    )
}
