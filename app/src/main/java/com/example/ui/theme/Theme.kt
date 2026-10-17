package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.example.data.AppThemeMode

private val MoexDarkColorScheme = darkColorScheme(
    primary = SoftObsidianDarkPalette.signalLong,
    onPrimary = SoftObsidianDarkPalette.bg,
    primaryContainer = SoftObsidianDarkPalette.signalLongDim,
    onPrimaryContainer = SoftObsidianDarkPalette.signalLong,
    secondary = SoftObsidianDarkPalette.accentCyan,
    onSecondary = SoftObsidianDarkPalette.bg,
    tertiary = SoftObsidianDarkPalette.signalWait,
    error = SoftObsidianDarkPalette.signalShort,
    background = SoftObsidianDarkPalette.bg,
    onBackground = SoftObsidianDarkPalette.textPrimary,
    surface = SoftObsidianDarkPalette.surface,
    onSurface = SoftObsidianDarkPalette.textPrimary,
    surfaceVariant = SoftObsidianDarkPalette.surfaceElevated,
    onSurfaceVariant = SoftObsidianDarkPalette.textSecondary,
    outline = SoftObsidianDarkPalette.border
)

private val MoexLightColorScheme = lightColorScheme(
    primary = AlpineDaylightPalette.signalLong,
    onPrimary = AlpineDaylightPalette.surface,
    primaryContainer = AlpineDaylightPalette.signalLongDim,
    onPrimaryContainer = AlpineDaylightPalette.signalLong,
    secondary = AlpineDaylightPalette.accentCyan,
    onSecondary = AlpineDaylightPalette.surface,
    tertiary = AlpineDaylightPalette.signalWait,
    error = AlpineDaylightPalette.signalShort,
    background = AlpineDaylightPalette.bg,
    onBackground = AlpineDaylightPalette.textPrimary,
    surface = AlpineDaylightPalette.surface,
    onSurface = AlpineDaylightPalette.textPrimary,
    surfaceVariant = AlpineDaylightPalette.surfaceElevated,
    onSurfaceVariant = AlpineDaylightPalette.textSecondary,
    outline = AlpineDaylightPalette.border
)

@Composable
fun MoexPulseTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }
    val palette = if (useDark) SoftObsidianDarkPalette else AlpineDaylightPalette
    val colorScheme = if (useDark) MoexDarkColorScheme else MoexLightColorScheme

    CompositionLocalProvider(LocalAppPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    MoexPulseTheme(themeMode = themeMode, content = content)
}
