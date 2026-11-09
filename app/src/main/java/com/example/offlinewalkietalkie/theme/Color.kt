package com.example.offlinewalkietalkie.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ============================================================================
// Apple Human Interface Guidelines - System Primitives & Semantic Tokens
// ============================================================================

// Apple iOS Dark Mode System Accents
val AppleSystemBlue = Color(0xFF0A84FF)
val AppleSystemGreen = Color(0xFF30D158)
val AppleSystemIndigo = Color(0xFF5E5CE6)
val AppleSystemOrange = Color(0xFFFF9F0A)
val AppleSystemPink = Color(0xFFFF375F)
val AppleSystemPurple = Color(0xFFBF5AF2)
val AppleSystemRed = Color(0xFFFF453A)
val AppleSystemTeal = Color(0xFF64D2FF)
val AppleSystemYellow = Color(0xFFFFD60A)

// Apple Surfaces & Materials (OLED True Black & Grouped Materials)
val AppleBackground = Color(0xFF000000)
val AppleBackgroundElevated = Color(0xFF07090E)
val AppleGroupedBackground = Color(0xFF0F1219)
val AppleCardSurface = Color(0xFF161A24)
val AppleCardSurfaceSecondary = Color(0xFF202636)
val AppleCardSurfaceTertiary = Color(0xFF2A3246)
val AppleGlassFrosted = Color(0xD9161A24) // 85% opacity frosted glass
val AppleGlassBorder = Color(0x1FFFFFFF) // Hairline 12% white
val AppleGlassBorderHighlight = Color(0x38FFFFFF) // 22% white highlight

// Apple SF Typography Neutrals
val AppleTextPrimary = Color(0xFFFFFFFF)
val AppleTextSecondary = Color(0x99FFFFFF) // 60% opacity
val AppleTextTertiary = Color(0x4DFFFFFF) // 30% opacity
val AppleTextQuaternary = Color(0x29FFFFFF) // 16% opacity

// ============================================================================
// Backward-Compatibility Aliases (Mapped to Apple System Tokens)
// ============================================================================
val ElectricAqua = AppleSystemTeal
val MintGreen = AppleSystemGreen
val BrightCyan = AppleSystemBlue
val WarmAmber = AppleSystemOrange
val RoseActive = AppleSystemRed

val DeepInk = AppleBackgroundElevated
val SlateBlue = AppleCardSurfaceSecondary
val MistLight = AppleTextPrimary
val PearlLight = AppleTextSecondary

val GlassBackground = AppleCardSurface
val GlassBackgroundHover = AppleCardSurfaceSecondary
val GlassBorder = AppleGlassBorder
val GlassSubtleBackground = Color(0x0FFFFFFF)

// Outdoor High-Contrast Mode
val OutdoorBackground = AppleBackground
val OutdoorCardBackground = Color(0xFF141720)
val OutdoorCardBorder = Color(0xFF3E475E)

// Atmospheric Ambient Brushes
val AtmosphericDarkBrush = Brush.radialGradient(
    colors = listOf(
        Color(0xFF121724), // Subtle deep blue-grey ambient top
        Color(0xFF070A10), // Transition
        AppleBackground    // Pure OLED black baseline
    ),
    radius = 1800f
)

// Apple Studio PTT Acoustic Dial Gradients
val PttReadyBrush = Brush.radialGradient(
    colors = listOf(
        Color(0xFF1D3557),
        Color(0xFF132238),
        Color(0xFF0A1322)
    ),
    radius = 350f
)

val PttTalkingBrush = Brush.radialGradient(
    colors = listOf(
        Color(0xFFFF3B30),
        Color(0xFFC71D23),
        Color(0xFF4A0A0E)
    ),
    radius = 350f
)
