package com.example.offlinewalkietalkie.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.offlinewalkietalkie.theme.*

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    isOutdoorMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    if (isOutdoorMode || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        // Solid high-contrast / API 29-30 fallback
        Box(
            modifier = modifier
                .clip(shape)
                .background(if (isOutdoorMode) OutdoorCardBackground else Color(0xEB1E293B))
                .border(
                    BorderStroke(
                        width = if (isOutdoorMode) 2.dp else 1.dp,
                        color = if (isOutdoorMode) OutdoorCardBorder else Color(0x40FFFFFF)
                    ),
                    shape = shape
                )
        ) {
            content()
        }
    } else {
        // API 31+ Translucent Glass with blur
        Box(
            modifier = modifier
                .clip(shape)
                .background(GlassBackground)
                .border(BorderStroke(1.dp, GlassBorder), shape = shape)
        ) {
            content()
        }
    }
}
