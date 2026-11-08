package com.example.offlinewalkietalkie.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Liquid Glass Foundations
val SlateBlue = Color(0xFF587690)
val DeepInk = Color(0xFF0B1020)
val MistLight = Color(0xFFEAF3F7)
val PearlLight = Color(0xFFF6F7FB)

// Luminous Accents
val ElectricAqua = Color(0xFF3BE7D0)
val MintGreen = Color(0xFF51E5B6)
val BrightCyan = Color(0xFF39C9F2)
val WarmAmber = Color(0xFFFFB45C)
val RoseActive = Color(0xFFFF5E77)

// Glass Surfaces
val GlassBackground = Color(0xFF172438) // ~8% White
val GlassBackgroundHover = Color(0x1FFFFFFF) // ~12% White
val GlassBorder = Color(0x2EFFFFFF) // ~18% White
val GlassSubtleBackground = Color(0x0DFFFFFF) // ~5% White

// High-contrast outdoor mode colors
val OutdoorBackground = Color(0xFF0F172A)
val OutdoorCardBackground = Color(0xFF1E293B)
val OutdoorCardBorder = Color(0xFF475569)

// Gradients
val AtmosphericDarkBrush = Brush.radialGradient(
    colors = listOf(Color(0xFF18283B), Color(0xFF111C2D), DeepInk),
    radius = 1800f
)

val PttReadyBrush = Brush.radialGradient(
    colors = listOf(MintGreen, BrightCyan, Color(0xFF14729C), Color(0xFF0D4661)),
    radius = 350f
)

val PttTalkingBrush = Brush.radialGradient(
    colors = listOf(Color(0xFFFF7E90), RoseActive, Color(0xFF9C1432)),
    radius = 350f
)
