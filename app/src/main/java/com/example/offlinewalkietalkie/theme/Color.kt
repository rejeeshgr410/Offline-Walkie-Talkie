package com.example.offlinewalkietalkie.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ============================================================================
// TalkLoop Modern Light Design System - Semantic Tokens & Primitives
// ============================================================================

// Brand Primary Red & Vibrant Accents (Matches TalkLoop Reference Image)
val BrandRed = Color(0xFFE60023)
val BrandRedDark = Color(0xFFD6001C)
val BrandRedLight = Color(0xFFFF2A3A)
val BrandPinkBadge = Color(0xFFFFEFF1)
val BrandPinkBadgeSelected = Color(0xFFFFE5E8)

// Modern Light System Accents
val AppleSystemBlue = Color(0xFF2563EB)
val AppleSystemGreen = Color(0xFF00C853)
val AppleSystemIndigo = Color(0xFF4F46E5)
val AppleSystemOrange = Color(0xFFFF9500)
val AppleSystemPink = Color(0xFFFF2D55)
val AppleSystemPurple = Color(0xFF9333EA)
val AppleSystemRed = Color(0xFFE60023)
val AppleSystemTeal = Color(0xFF0284C7)
val AppleSystemYellow = Color(0xFFEAB308)

// Modern Clean Light Canvas & Card Surfaces
val AppleBackground = Color(0xFFF8F9FA)
val AppleBackgroundElevated = Color(0xFFFFFFFF)
val AppleGroupedBackground = Color(0xFFF1F3F5)
val AppleCardSurface = Color(0xFFFFFFFF)
val AppleCardSurfaceSecondary = Color(0xFFF3F4F6)
val AppleCardSurfaceTertiary = Color(0xFFFFEFF1)
val AppleGlassFrosted = Color(0xF5FFFFFF)
val AppleGlassBorder = Color(0xFFE5E7EB)
val AppleGlassBorderHighlight = Color(0xFFD1D5DB)

// High-Contrast Light Typography Neutrals
val AppleTextPrimary = Color(0xFF111827)
val AppleTextSecondary = Color(0xFF6B7280)
val AppleTextTertiary = Color(0xFF9CA3AF)
val AppleTextQuaternary = Color(0xFFE5E7EB)

// Outdoor High-Contrast Mode (Clean light contrast)
val OutdoorBackground = Color(0xFFFFFFFF)
val OutdoorCardBackground = Color(0xFFFFFFFF)
val OutdoorCardBorder = Color(0xFF111827)

// Backward-Compatibility Aliases
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
val GlassSubtleBackground = Color(0x0A000000)

// Atmospheric Ambient Brushes
val AtmosphericDarkBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFF8F9FA),
        Color(0xFFF1F3F5),
        Color(0xFFE9ECEF)
    )
)

// PTT Acoustic Hero Gradients (Matches reference vibrant red hero button)
val PttReadyBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFF3344),
        Color(0xFFD6001C)
    )
)

val PttTalkingBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFF1E27),
        Color(0xFFB30014)
    )
)
