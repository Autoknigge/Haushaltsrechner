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

// Kachelfarben: wie in Solarprognose (Heute und Morgen) und Orange-Farbschema
val TileToday = Color(0xFFE35336)
val TileTomorrow = Color(0xFFD47E30)
val O1 = Color(0xFFD24E01)
val O2 = Color(0xFFDC6601)
val O3 = Color(0xFFE27602)
val O4 = Color(0xFFE88504)
val O5 = Color(0xFFEC9006)
val O6 = Color(0xFFEE9F27)
val O7 = Color(0xFFF1B04C)
val O8 = Color(0xFFF5C77E)
val O9 = Color(0xFFF9DDB1)
val CardGreen = Color(0xFF84B067)
val CardSand = Color(0xFFE0CD95)
val CardTeal = Color(0xFF00674F)
val DarkInk = Color(0xFF0A130E)
val DarkMuted = Color(0xFF1F2A24)
val WarmMuted = Color(0xFFFFE8D6)
val IncomeGreen = Color(0xFF0F5A32)
val BadRed = Color(0xFF9B1C1C)

val LocalInk = androidx.compose.runtime.compositionLocalOf { Ink }
val LocalMuted = androidx.compose.runtime.compositionLocalOf { Muted }

/** Deutlicher Farbverlauf: helle Karten (dunkle Schrift) werden nach unten heller, dunkle Karten (weiße Schrift) nach unten dunkler. */
fun tileBrush(bg: Color, darkInk: Boolean): Brush =
    if (darkInk) Brush.linearGradient(listOf(lerp(bg, Color.White, 0.45f), bg))
    else Brush.linearGradient(listOf(lerp(bg, Color.White, 0.10f), lerp(bg, Color.Black, 0.55f)))

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
