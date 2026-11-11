package com.example.offlinewalkietalkie.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinewalkietalkie.protocol.FloorState
import com.example.offlinewalkietalkie.theme.*

/**
 * TalkLoop Hero PTT Acoustic Dial.
 * Matches reference design:
 * - Concentric outer red circular border ring
 * - Soft ambient red radiating glow
 * - Core vibrant red gradient button (Hold to Talk)
 * - Pure white microphone icon
 * - Dynamic animated sound wave bars
 */
@Composable
fun PttOrb(
    floorState: FloorState,
    isOutdoorMode: Boolean = false,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val isTalking = floorState is FloorState.HeldByMe || floorState is FloorState.Talking || floorState is FloorState.Granted
    val isRequesting = floorState is FloorState.Requesting
    val isHeldByOther = floorState is FloorState.HeldByOther
    val heldByOther by rememberUpdatedState(isHeldByOther)

    // Tactile press bounce
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "talkloop_ptt_bounce"
    )

    // Ambient breathing pulse for outer halo
    val infiniteTransition = rememberInfiniteTransition(label = "talkloop_ptt_halo")
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isTalking) 1.08f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_pulse"
    )

    // Dynamic wave animation when active
    val waveAnimPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveform_phase"
    )

    val stateText = when (floorState) {
        is FloorState.HeldByMe, is FloorState.Talking, is FloorState.Granted -> "Transmitting"
        is FloorState.Requesting -> "Connecting…"
        is FloorState.HeldByOther -> "${floorState.peerName} Speaking"
        is FloorState.Denied -> "Channel Busy"
        is FloorState.Releasing -> "Ending…"
        is FloorState.Idle -> "Hold to Talk"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(248.dp)
            .semantics {
                contentDescription = "Push to Talk button. Status: $stateText. Hold down to speak."
            }
    ) {
        // 1. Soft radiating ambient red halo
        Box(
            modifier = Modifier
                .size(244.dp)
                .scale(haloPulse)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF2A3A).copy(alpha = if (isTalking) 0.35f else 0.16f),
                            Color(0xFFFF2A3A).copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 2. Outer concentric white circle with thin crisp red line border
        Box(
            modifier = Modifier
                .size(228.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(
                    width = 2.dp,
                    color = Color(0xFFFF2A3A),
                    shape = CircleShape
                )
        )

        // 3. Central Hero Vibrant Red Interactive Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(184.dp)
                .scale(pressScale)
                .shadow(
                    elevation = if (isPressed) 2.dp else 8.dp,
                    shape = CircleShape,
                    spotColor = Color(0xFFD6001C).copy(alpha = 0.5f),
                    ambientColor = Color(0xFFD6001C).copy(alpha = 0.25f)
                )
                .clip(CircleShape)
                .background(
                    if (isHeldByOther) {
                        Brush.linearGradient(listOf(Color(0xFF00C853), Color(0xFF009624)))
                    } else if (isTalking) {
                        PttTalkingBrush
                    } else {
                        PttReadyBrush
                    }
                )
                .pointerInput(heldByOther) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (heldByOther) return@awaitEachGesture
                        down.consume()
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPressStart()

                        while (true) {
                            val event = awaitPointerEvent()
                            val anyPressed = event.changes.any { it.pressed }
                            if (!anyPressed) break
                        }
                        isPressed = false
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPressEnd()
                    }
                }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 14.dp)
            ) {
                // White Mic or Speaker Icon
                Icon(
                    imageVector = if (isHeldByOther) Icons.Default.VolumeUp else Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Bold White Text
                Text(
                    text = stateText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = (-0.2).sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Dynamic Audio Waveform Bars
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(18.dp)
                ) {
                    val baseHeights = listOf(4, 7, 11, 15, 18, 14, 17, 12, 8, 5, 3)
                    baseHeights.forEachIndexed { index, baseH ->
                        val animatedH = if (isTalking || isHeldByOther) {
                            val wave = kotlin.math.sin(waveAnimPhase + index * 0.6).toFloat()
                            (baseH * (0.6f + 0.5f * wave)).coerceIn(3f, 18f)
                        } else {
                            baseH.toFloat()
                        }
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(animatedH.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(Color.White.copy(alpha = 0.92f))
                        )
                    }
                }
            }
        }
    }
}
