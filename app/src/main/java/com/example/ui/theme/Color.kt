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
    val cyanDim: Color get() = if (isDark) Color(0xFF0C2D48) else Color(0xFFE0F2FE)
}

val SoftObsidianDarkPalette = AppPalette(
    isDark = true,
    bg = Color(0xFF0E1420),
    surface = Color(0xFF161F30),
    surfaceElevated = Color(0xFF1E293B),
    border = Color(0xFF2B3A52),
    signalLong = Color(0xFF10B981),
    signalLongDim = Color(0xFF0F2E28),
    signalShort = Color(0xFFF43F5E),
    signalShortDim = Color(0xFF361726),
    signalWait = Color(0xFFF59E0B),
    signalWaitDim = Color(0xFF332612),
    accentCyan = Color(0xFF38BDF8),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B)
)

val AlpineDaylightPalette = AppPalette(
    isDark = false,
    bg = Color(0xFFF3F6FB),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEAF0F8),
    border = Color(0xFFD5E0EE),
    signalLong = Color(0xFF059669),
    signalLongDim = Color(0xFFD1FAE5),
    signalShort = Color(0xFFE11D48),
    signalShortDim = Color(0xFFFFE4E6),
    signalWait = Color(0xFFD97706),
    signalWaitDim = Color(0xFFFEF3C7),
    accentCyan = Color(0xFF0284C7),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF64748B)
)

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
