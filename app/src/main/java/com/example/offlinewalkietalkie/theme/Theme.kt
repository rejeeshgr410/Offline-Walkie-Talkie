package com.example.offlinewalkietalkie.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalOutdoorMode = compositionLocalOf { false }

// TalkLoop Modern Light Color Scheme (Default)
private val TalkLoopLightColorScheme = lightColorScheme(
    primary = AppleSystemRed,
    onPrimary = Color.White,
    primaryContainer = AppleCardSurfaceTertiary,
    onPrimaryContainer = AppleSystemRed,
    secondary = AppleSystemGreen,
    onSecondary = Color.White,
    secondaryContainer = AppleCardSurfaceSecondary,
    onSecondaryContainer = AppleTextPrimary,
    tertiary = AppleSystemOrange,
    onTertiary = Color.White,
    background = AppleBackground,
    onBackground = AppleTextPrimary,
    surface = AppleCardSurface,
    onSurface = AppleTextPrimary,
    surfaceVariant = AppleCardSurfaceSecondary,
    onSurfaceVariant = AppleTextSecondary,
    outline = AppleGlassBorder,
    outlineVariant = AppleGlassBorderHighlight,
    error = AppleSystemRed,
    onError = Color.White
)

// Apple Outdoor High-Contrast Mode
private val AppleOutdoorColorScheme = lightColorScheme(
    primary = AppleSystemRed,
    onPrimary = Color.White,
    primaryContainer = OutdoorCardBackground,
    onPrimaryContainer = Color.Black,
    secondary = AppleSystemGreen,
    onSecondary = Color.White,
    tertiary = AppleSystemOrange,
    onTertiary = Color.White,
    background = OutdoorBackground,
    onBackground = Color.Black,
    surface = OutdoorCardBackground,
    onSurface = Color.Black,
    surfaceVariant = OutdoorCardBorder,
    onSurfaceVariant = Color.Black,
    error = AppleSystemRed,
    onError = Color.White
)

@Composable
fun OfflineWalkieTalkieTheme(
    outdoorMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (outdoorMode) AppleOutdoorColorScheme else TalkLoopLightColorScheme

    CompositionLocalProvider(LocalOutdoorMode provides outdoorMode) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
