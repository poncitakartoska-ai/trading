package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MoexDarkColorScheme = darkColorScheme(
    primary = SignalLong,
    onPrimary = Color(0xFF00210E),
    primaryContainer = SignalLongDim,
    onPrimaryContainer = SignalLong,
    secondary = TerminalCyan,
    onSecondary = Color(0xFF001E2C),
    tertiary = SignalWait,
    onTertiary = Color(0xFF261600),
    error = SignalShort,
    onError = Color.White,
    background = TerminalBg,
    onBackground = TerminalTextPrimary,
    surface = TerminalSurface,
    onSurface = TerminalTextPrimary,
    surfaceVariant = TerminalSurfaceElevated,
    onSurfaceVariant = TerminalTextSecondary,
    outline = TerminalBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MoexDarkColorScheme,
        typography = Typography,
        content = content
    )
}
