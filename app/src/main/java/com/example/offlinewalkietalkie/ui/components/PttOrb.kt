package com.example.offlinewalkietalkie.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinewalkietalkie.R
import com.example.offlinewalkietalkie.protocol.FloorState
import com.example.offlinewalkietalkie.theme.*

/**
 * Apple Design Award-grade PTT Acoustic Dial.
 * Features tactile 0.96 scale-on-press animation, continuous concentric rings,
 * ambient breathing pulse halo, and crisp SF-style status typography.
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

    // Tactile 0.96 press bounce as mandated by better-ui
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "apple_ptt_press_bounce"
    )

    // Ambient breathing aura
    val infiniteTransition = rememberInfiniteTransition(label = "apple_ptt_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isTalking) 1.06f else if (isRequesting) 1.03f else 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "apple_pulse_scale"
    )

    val haloColor = when {
        isTalking -> AppleSystemRed.copy(alpha = 0.45f)
        isHeldByOther -> AppleSystemGreen.copy(alpha = 0.45f)
        isRequesting -> AppleSystemOrange.copy(alpha = 0.45f)
        else -> AppleSystemBlue.copy(alpha = 0.22f)
    }

    val stateText = when (floorState) {
        is FloorState.HeldByMe, is FloorState.Talking, is FloorState.Granted -> "Transmitting"
        is FloorState.Requesting -> "Connecting…"
        is FloorState.HeldByOther -> "${floorState.peerName} Speaking"
        is FloorState.Denied -> "Channel Busy"
        is FloorState.Releasing -> "Ending…"
        is FloorState.Idle -> "Hold to Speak"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(196.dp)
            .semantics {
                contentDescription = "Push to Talk button. Status: $stateText. Hold down to speak."
            }
    ) {
        // Outer breathing halo ring
        Box(
            modifier = Modifier
                .size(192.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .border(
                    width = if (isTalking || isHeldByOther) 1.5.dp else 0.75.dp,
                    color = haloColor,
                    shape = CircleShape
                )
        )

        // Middle acoustic bezel ring
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(AppleCardSurfaceSecondary.copy(alpha = 0.5f))
                .border(0.5.dp, AppleGlassBorder, CircleShape)
        )

        // Core tactile 168dp interactive dial
        val dialBrush = if (isOutdoorMode) {
            if (isTalking) Brush.linearGradient(listOf(AppleSystemRed, AppleSystemRed))
            else Brush.linearGradient(listOf(AppleCardSurface, AppleCardSurface))
        } else {
            if (isTalking) PttTalkingBrush else PttReadyBrush
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(168.dp)
                .scale(pressScale)
                .clip(CircleShape)
                .background(dialBrush)
                .border(
                    width = 1.dp,
                    color = if (isTalking) AppleSystemRed.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.15f),
                    shape = CircleShape
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (!heldByOther) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isPressed = true
                                onPressStart()
                                tryAwaitRelease()
                                isPressed = false
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPressEnd()
                            }
                        }
                    )
                }
        ) {
            // Subtle specular glass lens highlight on top half
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = 160f
                        )
                    )
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                // Tactile frosted icon capsule
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isTalking) AppleSystemRed.copy(alpha = 0.35f)
                            else if (isHeldByOther) AppleSystemGreen.copy(alpha = 0.35f)
                            else Color.White.copy(alpha = 0.12f)
                        )
                        .border(
                            0.75.dp,
                            if (isTalking) AppleSystemRed.copy(alpha = 0.6f)
                            else if (isHeldByOther) AppleSystemGreen.copy(alpha = 0.6f)
                            else Color.White.copy(alpha = 0.2f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isHeldByOther) Icons.Default.VolumeUp else Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (isTalking) Color.White else if (isHeldByOther) AppleSystemGreen else AppleSystemTeal,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Clean Apple Headline & Subheadline
                Text(
                    text = stateText,
                    color = AppleTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = (-0.2).sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (heldByOther) "Channel Active" else "PTT Ready",
                    color = AppleTextTertiary,
                    fontWeight = FontWeight.Normal,
                    fontSize = 10.sp,
                    letterSpacing = 0.4.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
