package com.example.offlinewalkietalkie.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ============================================================================
// Apple SF Pro Typographic System
// ============================================================================

val AppleLargeTitle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 38.sp,
    letterSpacing = (-0.6).sp,
    color = AppleTextPrimary
)

val AppleTitle1 = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    lineHeight = 32.sp,
    letterSpacing = (-0.4).sp,
    color = AppleTextPrimary
)

val AppleTitle2 = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.3).sp,
    color = AppleTextPrimary
)

val AppleTitle3 = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 19.sp,
    lineHeight = 24.sp,
    letterSpacing = (-0.2).sp,
    color = AppleTextPrimary
)

val AppleHeadline = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    letterSpacing = (-0.1).sp,
    color = AppleTextPrimary
)

val AppleBody = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 21.sp,
    letterSpacing = 0.sp,
    color = AppleTextPrimary
)

val AppleCallout = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp,
    color = AppleTextSecondary
)

val AppleSubheadline = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp,
    color = AppleTextSecondary
)

val AppleFootnote = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.sp,
    color = AppleTextTertiary
)

val AppleCaption1 = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    letterSpacing = 0.1.sp,
    color = AppleTextSecondary
)

// Section overlines / small-caps categories
val AppleCaption2 = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.8.sp, // Letter-spacing for uppercase overlines
    color = AppleTextTertiary
)

// Tabular numbers for telemetry, frequencies, timers, battery
val AppleMonospacedDigits = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.sp,
    fontFeatureSettings = "tnum"
)

// Material 3 Typography Bridge
val Typography = Typography(
    headlineLarge = AppleLargeTitle,
    headlineMedium = AppleTitle1,
    headlineSmall = AppleTitle2,
    titleLarge = AppleTitle3,
    titleMedium = AppleHeadline,
    titleSmall = AppleCallout,
    bodyLarge = AppleBody,
    bodyMedium = AppleCallout,
    bodySmall = AppleSubheadline,
    labelLarge = AppleHeadline,
    labelMedium = AppleFootnote,
    labelSmall = AppleCaption2
)
