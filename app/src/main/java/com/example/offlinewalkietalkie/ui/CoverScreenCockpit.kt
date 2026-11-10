package com.example.offlinewalkietalkie.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinewalkietalkie.protocol.FloorState

/**
 * Compact Outer Cover-Screen Cockpit.
 *
 * Modeled after Vivo's FlipReceiveActivity (Outer Screen Experience for Flip / Foldables):
 * - Designed specifically for compact displays (< 450dp width or height) such as the
 *   outer cover screens of clamshell flip devices (Vivo X Flip, Z Flip, Razr).
 * - High-contrast tactical Apple-grade cockpit with minimal visual noise.
 * - Single-touch push-to-talk orb, emergency SOS trigger, speaker toggle, and link telemetry.
 */
@Composable
fun CoverScreenCockpit(
    viewModel: WalkieTalkieViewModel,
    modifier: Modifier = Modifier,
    onExpandToFull: (() -> Unit)? = null
) {
    val floorState by viewModel.floorState.collectAsState()
    val isTransmitting = floorState is FloorState.Talking || floorState is FloorState.HeldByMe || floorState is FloorState.Granted
    val isReceiving = floorState is FloorState.HeldByOther
    val speakerEnabled by viewModel.speakerphoneEnabled.collectAsState()

    // Pulsing aura animation for transmission/reception
    val infiniteTransition = rememberInfiniteTransition(label = "CoverPttAura")
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isTransmitting || isReceiving) 1.15f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CoverAuraScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10))
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Status badge & Replay / Speaker toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tactical Status Pill
                val statusText = when {
                    isTransmitting -> "TX • LIVE"
                    isReceiving -> "RX • INCOMING"
                    else -> "READY"
                }
                val statusColor = when {
                    isTransmitting -> Color(0xFF00E5FF)
                    isReceiving -> Color(0xFF00E676)
                    else -> Color(0xFF78909C)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Controls row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Instant Replay Button
                    IconButton(
                        onClick = { viewModel.replayLastMessage() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = "Instant Replay",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Speaker toggle
                    IconButton(
                        onClick = { viewModel.toggleAudioRoute() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (speakerEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Speaker",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Central PTT Touch Orb
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(140.dp)
                    .scale(auraScale)
            ) {
                val orbBorder = when {
                    isTransmitting -> Color(0xFF00E5FF)
                    isReceiving -> Color(0xFF00E676)
                    else -> Color.White.copy(alpha = 0.2f)
                }

                val orbGradient = when {
                    isTransmitting -> Brush.radialGradient(listOf(Color(0xFF00B0FF), Color(0xFF0059B2)))
                    isReceiving -> Brush.radialGradient(listOf(Color(0xFF00E676), Color(0xFF1B5E20)))
                    else -> Brush.radialGradient(listOf(Color(0xFF263238), Color(0xFF102027)))
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(orbGradient)
                        .border(2.dp, orbBorder, CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    viewModel.requestPtt()
                                    tryAwaitRelease()
                                    viewModel.releasePtt()
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "PTT Mic",
                            tint = if (isTransmitting || isReceiving) Color.White else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isTransmitting) "TALKING" else if (isReceiving) "LISTENING" else "HOLD TO TALK",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Bottom row: Emergency SOS quick trigger & expand
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Emergency SOS Button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFF1744).copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF1744).copy(alpha = 0.5f)),
                    modifier = Modifier.clickable {
                        viewModel.startSos("SOS Distress from Cover Screen")
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "SOS",
                            tint = Color(0xFFFF1744),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SOS",
                            color = Color(0xFFFF1744),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                if (onExpandToFull != null) {
                    Text(
                        text = "OPEN FULL APP ↗",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { onExpandToFull() }
                    )
                }
            }
        }
    }
}
