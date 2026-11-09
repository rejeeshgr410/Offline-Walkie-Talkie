package com.example.offlinewalkietalkie.ui.components

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.offlinewalkietalkie.theme.*

/**
 * Apple-inspired Frosted Glass Inset Card with continuous squircle corners,
 * subtle specular highlights, hairline border, and optional tactile 0.96 press bounce.
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
    var isPressed by remember { mutableStateOf(false) }

    val pressScale by animateFloatAsState(
        targetValue = if (interactive && isPressed) 0.96f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "apple_card_press"
    )

    val surfaceBg = if (isOutdoorMode) {
        OutdoorCardBackground
    } else {
        AppleCardSurface
    }

    val borderStroke = BorderStroke(
        width = if (isOutdoorMode) 1.5.dp else 0.75.dp,
        color = if (isOutdoorMode) OutdoorCardBorder else AppleGlassBorder
    )

    Box(
        modifier = modifier
            .scale(pressScale)
            .clip(shape)
            .background(surfaceBg)
            .border(borderStroke, shape = shape)
            .then(
                if (onClick != null) {
                    Modifier.pointerInput(interactive) {
                        detectTapGestures(
                            onPress = {
                                if (interactive) isPressed = true
                                tryAwaitRelease()
                                if (interactive) isPressed = false
                            },
                            onTap = { onClick() }
                        )
                    }
                } else Modifier
            )
    ) {
        // Subtle Apple specular lighting gradient at the top edge
        if (!isOutdoorMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = 120f
                        )
                    )
            )
        }
        content()
    }
}
