package com.example.offlinewalkietalkie.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import com.example.offlinewalkietalkie.protocol.SosAck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinewalkietalkie.protocol.SosBeacon
import com.example.offlinewalkietalkie.theme.*

/**
 * Apple Emergency SOS Alert Banner.
 * High-contrast, serious, clear hierarchy and immediate recovery actions.
 */
@Composable
fun SosAlertBanner(
    alerts: List<SosBeacon>,
    isAlarmPlaying: Boolean,
    onSilenceAlarm: () -> Unit,
    onDismissAlert: (String) -> Unit,
    onNavigateToRadar: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = alerts.isNotEmpty(), modifier = modifier) {
        val topAlert = alerts.firstOrNull() ?: return@AnimatedVisibility

        // Subtle emergency pulse
        val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
        val alertAlpha by infiniteTransition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "sos_alpha"
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = AppleSystemRed.copy(alpha = alertAlpha)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Distress Alert",
                            tint = AppleSystemRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DISTRESS: ${topAlert.senderName.uppercase()}",
                            color = Color.White,
                            style = AppleHeadline,
                            fontSize = 14.sp
                        )
                        Text(
                            text = topAlert.distressMessage,
                            color = Color.White.copy(alpha = 0.9f),
                            style = AppleBody,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val now = System.currentTimeMillis()
                val ageSec = ((now - topAlert.timestamp) / 1000).coerceAtLeast(0)
                val isGpsValid = !(topAlert.latitude == 0.0 && topAlert.longitude == 0.0)

                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text(
                        text = if (isGpsValid) "GPS: ${"%.4f".format(topAlert.latitude)}, ${"%.4f".format(topAlert.longitude)} • Alt: ${topAlert.altitude.toInt()}m"
                               else "⚠️ No GPS coordinates attached (approximate location unknown)",
                        color = if (isGpsValid) Color.White.copy(alpha = 0.9f) else AppleSystemYellow,
                        style = AppleCaption1,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (ageSec > 300) "⚠️ Transmitted ${ageSec / 60}m ago (Stale)" else "Live fix • ${ageSec}s ago • Hop TTL: ${topAlert.ttl}",
                        color = Color.White.copy(alpha = 0.75f),
                        style = AppleCaption2
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (topAlert.batteryPercent in 0..100) {
                        Text(
                            text = "Battery: ${topAlert.batteryPercent}%",
                            color = Color.White.copy(alpha = 0.8f),
                            style = AppleMonospacedDigits,
                            fontSize = 12.sp
                        )
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isAlarmPlaying) {
                            Button(
                                onClick = onSilenceAlarm,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Black.copy(alpha = 0.45f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.NotificationsOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Silence", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Button(
                            onClick = onNavigateToRadar,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = AppleSystemRed
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Track on Radar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Apple Emergency SOS Broadcasting Status Banner with confirmed mesh responder receipts.
 */
@Composable
fun SosBroadcastingStatusBanner(
    beacon: SosBeacon?,
    responders: List<SosAck>,
    onStopSos: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppleSystemRed),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Broadcasting Distress",
                        tint = AppleSystemRed,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EMERGENCY SOS ACTIVE",
                        color = Color.White,
                        style = AppleHeadline,
                        fontSize = 14.sp
                    )
                    Text(
                        text = beacon?.distressMessage ?: "Distress Beacon Broadcasting",
                        color = Color.White.copy(alpha = 0.9f),
                        style = AppleBody,
                        fontSize = 13.sp
                    )
                }
            }

            // Delivery Acknowledgment Receipts Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (responders.isNotEmpty()) Icons.Default.CheckCircle else Icons.Default.Radar,
                        contentDescription = null,
                        tint = if (responders.isNotEmpty()) AppleSystemGreen else AppleSystemYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (responders.isNotEmpty()) {
                                "Delivered to ${responders.size} responder(s):"
                            } else {
                                "Broadcasting on all offline mesh hops…"
                            },
                            color = Color.White,
                            style = AppleCaption1,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (responders.isNotEmpty()) {
                                responders.joinToString(", ") { it.responderName }
                            } else {
                                "Waiting for mesh peer acknowledgments"
                            },
                            color = Color.White.copy(alpha = 0.85f),
                            style = AppleCaption2
                        )
                    }
                }
            }

            Button(
                onClick = onStopSos,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = AppleSystemRed
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(38.dp)
            ) {
                Text("Stop SOS Broadcast", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

/**
 * Apple Emergency SOS Broadcast Confirmation Modal.
 */
@Composable
fun SosBroadcastModal(
    onDismiss: () -> Unit,
    onStartSos: (String) -> Unit
) {
    val presets = listOf(
        "Medical Emergency • Need Immediate Aid",
        "Injured / Fall • Cannot Walk",
        "Lost on Trail • Need Direction",
        "Vehicle Breakdown • Stranded"
    )
    var selectedPreset by remember { mutableStateOf(presets.first()) }
    var customText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppleCardSurface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppleSystemRed.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AppleSystemRed, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("Emergency SOS", style = AppleTitle3)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Distress beacons broadcast at high priority across all offline mesh channels, sounding alarms on nearby devices.",
                    style = AppleFootnote,
                    color = AppleTextSecondary
                )

                Text(
                    text = "SELECT DISTRESS NATURE",
                    style = AppleCaption2,
                    modifier = Modifier.padding(top = 8.dp)
                )

                presets.forEach { preset ->
                    val isSelected = (preset == selectedPreset && customText.isEmpty())
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) AppleCardSurfaceSecondary else Color.Transparent)
                            .clickable {
                                selectedPreset = preset
                                customText = ""
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                selectedPreset = preset
                                customText = ""
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = AppleSystemRed,
                                unselectedColor = AppleTextTertiary
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(preset, style = AppleBody, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    placeholder = { Text("Or enter custom location notes…", style = AppleFootnote) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppleTextPrimary,
                        unfocusedTextColor = AppleTextPrimary,
                        focusedBorderColor = AppleSystemRed,
                        unfocusedBorderColor = AppleGlassBorder,
                        focusedContainerColor = AppleCardSurfaceSecondary,
                        unfocusedContainerColor = AppleCardSurfaceSecondary,
                        cursorColor = AppleSystemRed
                    ),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val message = customText.trim().ifEmpty { selectedPreset }
                    onStartSos(message)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppleSystemRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text("Broadcast SOS Beacon", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", color = AppleTextSecondary, fontWeight = FontWeight.Medium)
            }
        }
    )
}
