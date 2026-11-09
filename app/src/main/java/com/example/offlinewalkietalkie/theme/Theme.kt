package com.example.offlinewalkietalkie.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalOutdoorMode = compositionLocalOf { false }

// Apple Dark Mode Color Scheme
private val AppleDarkColorScheme = darkColorScheme(
    primary = AppleSystemBlue,
    onPrimary = Color.White,
    primaryContainer = AppleCardSurfaceSecondary,
    onPrimaryContainer = AppleTextPrimary,
    secondary = AppleSystemGreen,
    onSecondary = Color.Black,
    secondaryContainer = AppleCardSurfaceTertiary,
    onSecondaryContainer = AppleTextPrimary,
    tertiary = AppleSystemTeal,
    onTertiary = Color.Black,
    background = AppleBackground,
    onBackground = AppleTextPrimary,
    surface = AppleCardSurface,
    onSurface = AppleTextPrimary,
    surfaceVariant = AppleCardSurfaceSecondary,
    onSurfaceVariant = AppleTextSecondary,
    error = AppleSystemRed,
    onError = Color.White
)

// Apple Outdoor High-Contrast Mode
private val AppleOutdoorColorScheme = darkColorScheme(
    primary = AppleSystemBlue,
    onPrimary = Color.Black,
    primaryContainer = OutdoorCardBackground,
    onPrimaryContainer = Color.White,
    secondary = AppleSystemGreen,
    onSecondary = Color.Black,
    tertiary = AppleSystemOrange,
    onTertiary = Color.Black,
    background = OutdoorBackground,
    onBackground = Color.White,
    surface = OutdoorCardBackground,
    onSurface = Color.White,
    surfaceVariant = OutdoorCardBorder,
    onSurfaceVariant = Color.White,
    error = AppleSystemRed,
    onError = Color.White
)

@Composable
fun OfflineWalkieTalkieTheme(
    outdoorMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (outdoorMode) AppleOutdoorColorScheme else AppleDarkColorScheme

    CompositionLocalProvider(LocalOutdoorMode provides outdoorMode) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
