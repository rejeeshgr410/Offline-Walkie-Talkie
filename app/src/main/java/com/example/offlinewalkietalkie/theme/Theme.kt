package com.example.offlinewalkietalkie.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalOutdoorMode = compositionLocalOf { false }

private val LiquidGlassDarkColorScheme = darkColorScheme(
    primary = ElectricAqua,
    onPrimary = DeepInk,
    primaryContainer = SlateBlue,
    onPrimaryContainer = MistLight,
    secondary = MintGreen,
    onSecondary = DeepInk,
    secondaryContainer = Color(0xFF14729C),
    onSecondaryContainer = MistLight,
    tertiary = BrightCyan,
    onTertiary = DeepInk,
    background = DeepInk,
    onBackground = MistLight,
    surface = Color(0xFF121B2B),
    onSurface = MistLight,
    surfaceVariant = Color(0xFF1A273B),
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = RoseActive,
    onError = Color.White
)

private val OutdoorHighContrastColorScheme = darkColorScheme(
    primary = ElectricAqua,
    onPrimary = Color.Black,
    primaryContainer = OutdoorCardBackground,
    onPrimaryContainer = Color.White,
    secondary = MintGreen,
    onSecondary = Color.Black,
    tertiary = WarmAmber,
    onTertiary = Color.Black,
    background = OutdoorBackground,
    onBackground = Color.White,
    surface = OutdoorCardBackground,
    onSurface = Color.White,
    surfaceVariant = OutdoorCardBorder,
    onSurfaceVariant = Color.White,
    error = RoseActive,
    onError = Color.White
)

@Composable
fun OfflineWalkieTalkieTheme(
    outdoorMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (outdoorMode) OutdoorHighContrastColorScheme else LiquidGlassDarkColorScheme

    CompositionLocalProvider(LocalOutdoorMode provides outdoorMode) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
