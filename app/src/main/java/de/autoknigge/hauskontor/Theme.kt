package de.autoknigge.hauskontor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Markenfarben der autoknigge-Apps: Tannengrün mit Gold.
val Forest = Color(0xFF0B2A21)
val Panel = Color(0xFF123A2E)
val Panel2 = Color(0xFF1B4C3C)
val Gold = Color(0xFFE3B341)
val GoldSoft = Color(0xFFF3D27A)
val Ink = Color(0xFFEAF2EE)
val Muted = Color(0xFFA4BDB2)
val Line = Color(0xFF2B5848)
val Red = Color(0xFFE35336)
val Green = Color(0xFF84B067)

fun cardBrush(start: Color, darken: Float = 0.35f): Brush =
    Brush.linearGradient(listOf(start, lerp(start, Color.Black, darken)))

@Composable
fun HauskontorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold, onPrimary = Forest,
            secondary = GoldSoft, onSecondary = Forest,
            background = Forest, onBackground = Ink,
            surface = Panel, onSurface = Ink,
            surfaceVariant = Panel2, onSurfaceVariant = Muted,
            secondaryContainer = Panel2, onSecondaryContainer = Ink,
            surfaceContainerLowest = Forest, surfaceContainerLow = Panel, surfaceContainer = Panel,
            surfaceContainerHigh = Panel2, surfaceContainerHighest = Panel2,
            outline = Line, outlineVariant = Line,
            error = Color(0xFFFFB4A8), onError = Forest
        ),
        content = content
    )
}
