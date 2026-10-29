package com.notiz.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Deep teal + warm marigold: calm and trustworthy, with an Indian accent colour.
private val Teal = Color(0xFF0F5C57)
private val TealLight = Color(0xFF7FD3C9)
private val Marigold = Color(0xFFE39B1B)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEDE8),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Marigold,
    onSecondary = Color(0xFF2A1800),
    secondaryContainer = Color(0xFFFFE2B5),
    onSecondaryContainer = Color(0xFF2A1800),
    background = Color(0xFFF7F8F6),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6ECEA),
    onSurfaceVariant = Color(0xFF45514F),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF0B4A46),
    onPrimaryContainer = Color(0xFFCDEDE8),
    secondary = Color(0xFFFFC56B),
    onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF5E4100),
    onSecondaryContainer = Color(0xFFFFE2B5),
    background = Color(0xFF101413),
    surface = Color(0xFF161B1A),
    surfaceVariant = Color(0xFF2B3331),
    onSurfaceVariant = Color(0xFFBEC9C6),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

/** Status colours used for dues and complaints chips. */
object StatusColors {
    val success = Color(0xFF2E7D32)
    val warning = Color(0xFFB26A00)
    val info = Color(0xFF1565C0)
}

@Composable
fun NotizTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}
