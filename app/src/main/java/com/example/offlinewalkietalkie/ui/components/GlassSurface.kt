package com.example.offlinewalkietalkie.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.offlinewalkietalkie.theme.*

/**
 * Modern Light Bento Inset Card with continuous squircle corners,
 * soft ambient shadow, hairline border, and tactile press bounce.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    isOutdoorMode: Boolean = false,
    interactive: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by if (interactive && onClick != null) {
        interactionSource.collectIsPressedAsState()
    } else {
        remember { mutableStateOf(false) }
    }

    val pressScale by animateFloatAsState(
        targetValue = if (interactive && isPressed) 0.97f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "light_card_press"
    )

    val surfaceBg = if (isOutdoorMode) {
        OutdoorCardBackground
    } else {
        AppleCardSurface
    }

    val borderStroke = BorderStroke(
        width = if (isOutdoorMode) 1.dp else 0.75.dp,
        color = if (isOutdoorMode) OutdoorCardBorder else AppleGlassBorder
    )

    Box(
        modifier = modifier
            .scale(pressScale)
            .shadow(
                elevation = 2.dp,
                shape = shape,
                spotColor = Color(0x0C000000),
                ambientColor = Color(0x04000000)
            )
            .clip(shape)
            .background(surfaceBg)
            .border(borderStroke, shape = shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = onClick
                    )
                } else Modifier
            )
    ) {
        content()
    }
}
