package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AppPalette(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val signalLong: Color,
    val signalLongDim: Color,
    val signalShort: Color,
    val signalShortDim: Color,
    val signalWait: Color,
    val signalWaitDim: Color,
    val accentCyan: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color
) {
    val longColor: Color get() = signalLong
    val longDim: Color get() = signalLongDim
    val shortColor: Color get() = signalShort
    val shortDim: Color get() = signalShortDim
    val waitColor: Color get() = signalWait
    val waitDim: Color get() = signalWaitDim
    val cyan: Color get() = accentCyan
    val cyanDim: Color get() = Color(0xFF1C1C1C)

    // Unified aliases for PRO components
    val bgPrimary: Color get() = bg
    val bgCard: Color get() = surface
    val bgElevated: Color get() = surfaceElevated
    val borderCard: Color get() = border
    val longGreen: Color get() = signalLong
    val longGreenDim: Color get() = signalLongDim
    val shortRed: Color get() = signalShort
    val shortRedDim: Color get() = signalShortDim
    val amberWait: Color get() = signalWait
    val amberWaitDim: Color get() = signalWaitDim
    val cyanAccent: Color get() = accentCyan
}

/**
 * Pure Institutional Noir Monochrome (Black & White) Palette.
 * Engineered for Samsung AMOLED / S25 displays with pure #000000 pitch black,
 * stark #FFFFFF primary accents, and precision grayscale hierarchy.
 */
val SoftObsidianDarkPalette = AppPalette(
    isDark = true,
    bg = Color(0xFF000000),
    surface = Color(0xFF0B0B0B),
    surfaceElevated = Color(0xFF141414),
    border = Color(0xFF2A2A2A),
    signalLong = Color(0xFFFFFFFF),
    signalLongDim = Color(0xFF1F1F1F),
    signalShort = Color(0xFF9E9E9E),
    signalShortDim = Color(0xFF141414),
    signalWait = Color(0xFF707070),
    signalWaitDim = Color(0xFF101010),
    accentCyan = Color(0xFFEDEDED),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA6A6A6),
    textMuted = Color(0xFF686868)
)

// Keep alias pointing to Monochrome Dark Palette so the entire application is 100% monochrome everywhere
val AlpineDaylightPalette = SoftObsidianDarkPalette

val LocalAppPalette = staticCompositionLocalOf { SoftObsidianDarkPalette }

val TerminalBg: Color
    @Composable get() = LocalAppPalette.current.bg

val TerminalSurface: Color
    @Composable get() = LocalAppPalette.current.surface

val TerminalSurfaceElevated: Color
    @Composable get() = LocalAppPalette.current.surfaceElevated

val TerminalBorder: Color
    @Composable get() = LocalAppPalette.current.border

val SignalLong: Color
    @Composable get() = LocalAppPalette.current.signalLong

val SignalLongDim: Color
    @Composable get() = LocalAppPalette.current.signalLongDim

val SignalShort: Color
    @Composable get() = LocalAppPalette.current.signalShort

val SignalShortDim: Color
    @Composable get() = LocalAppPalette.current.signalShortDim

val SignalWait: Color
    @Composable get() = LocalAppPalette.current.signalWait

val SignalWaitDim: Color
    @Composable get() = LocalAppPalette.current.signalWaitDim

val TerminalCyan: Color
    @Composable get() = LocalAppPalette.current.accentCyan

val TerminalTextPrimary: Color
    @Composable get() = LocalAppPalette.current.textPrimary

val TerminalTextSecondary: Color
    @Composable get() = LocalAppPalette.current.textSecondary

val TerminalTextMuted: Color
    @Composable get() = LocalAppPalette.current.textMuted
