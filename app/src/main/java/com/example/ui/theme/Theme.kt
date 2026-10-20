package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
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

val MoexShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun MoexPulseTheme(
    @Suppress("UNUSED_PARAMETER") themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAppPalette provides SoftObsidianDarkPalette) {
        MaterialTheme(
            colorScheme = MoexDarkColorScheme,
            typography = Typography,
            shapes = MoexShapes,
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
