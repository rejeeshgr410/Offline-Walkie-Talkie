package com.example.offlinewalkietalkie.ui

import com.example.offlinewalkietalkie.theme.*

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinewalkietalkie.protocol.PeerLocation
import com.example.offlinewalkietalkie.radar.RadarMath
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    viewModel: WalkieTalkieViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val myLocation by viewModel.currentLocation.collectAsState()
    val heading by viewModel.deviceHeading.collectAsState()
    val peerLocations by viewModel.peerLocations.collectAsState()
    val selectedPeerId by viewModel.selectedRadarPeerId.collectAsState()
    val sosAlerts by viewModel.incomingAlerts.collectAsState()

    // Determine target: either selected peer or first SOS alert or first peer
    val targetPeer = if (selectedPeerId != null) {
        peerLocations[selectedPeerId]
    } else if (sosAlerts.isNotEmpty()) {
        val sos = sosAlerts.first()
        PeerLocation(
            peerId = sos.senderId,
            peerName = "🚨 ${sos.senderName}",
            latitude = sos.latitude,
            longitude = sos.longitude,
            altitude = sos.altitude,
            batteryPercent = sos.batteryPercent,
            timestamp = sos.timestamp
        )
    } else {
        peerLocations.values.firstOrNull()
    }

    val distanceMeters = if (myLocation != null && targetPeer != null && targetPeer.latitude != 0.0) {
        RadarMath.haversineDistance(
            myLocation!!.latitude, myLocation!!.longitude,
            targetPeer.latitude, targetPeer.longitude
        )
    } else null

    val targetBearing = if (myLocation != null && targetPeer != null && targetPeer.latitude != 0.0) {
        RadarMath.initialBearing(
            myLocation!!.latitude, myLocation!!.longitude,
            targetPeer.latitude, targetPeer.longitude
        )
    } else null

    val relativeAngle = if (targetBearing != null) {
        RadarMath.relativeBearing(heading, targetBearing)
    } else 0f

    val peerLocationAgeMs = targetPeer?.let { (System.currentTimeMillis() - it.timestamp).coerceAtLeast(0L) }
    val locationIsFresh = peerLocationAgeMs != null && peerLocationAgeMs < 120_000L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Directional Radar Compass") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F141C),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F141C)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Target telemetry badge
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2633)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (targetPeer != null) targetPeer.peerName else "Scanning for Peers...",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            targetPeer == null -> "Waiting for a shared location"
                            locationIsFresh -> "Location updated ${peerLocationAgeMs!! / 1000}s ago"
                            else -> "Last shared location · ${peerLocationAgeMs!! / 60_000}m ago"
                        },
                        color = if (locationIsFresh) Color(0xFF81C784) else Color(0xFFFFCC80),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (distanceMeters != null) "Distance: ${RadarMath.formatDistance(distanceMeters)}" else "Acquiring GPS fix...",
                            color = Color(0xFF00E5FF),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (targetBearing != null) {
                            Text(
                                text = "Bearing: ${targetBearing.toInt()}° (${RadarMath.cardinalDirection(targetBearing)})",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            if (targetPeer != null && targetPeer.latitude != 0.0 && targetPeer.longitude != 0.0) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val lat = targetPeer.latitude
                        val lon = targetPeer.longitude
                        val peerNameEncoded = Uri.encode(targetPeer.peerName)
                        val navIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lon")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon($peerNameEncoded)")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(navIntent) }.recoverCatching { context.startActivity(geoIntent) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, tint = BrightCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Navigate to ${targetPeer.peerName}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Radar Scope Canvas
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.width / 2f - 16f

                    // Range Rings
                    val ringFractions = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f)
                    for (fraction in ringFractions) {
                        drawCircle(
                            color = Color(0xFF00E5FF).copy(alpha = 0.25f),
                            radius = maxRadius * fraction,
                            center = center,
                            style = Stroke(width = 1.5f)
                        )
                    }

                    // Crosshairs
                    drawLine(
                        color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                        start = Offset(center.x, center.y - maxRadius),
                        end = Offset(center.x, center.y + maxRadius),
                        strokeWidth = 1.5f
                    )
                    drawLine(
                        color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                        start = Offset(center.x - maxRadius, center.y),
                        end = Offset(center.x + maxRadius, center.y),
                        strokeWidth = 1.5f
                    )

                    // North indicator (relative to device heading)
                    val northAngleRad = Math.toRadians((-heading - 90f).toDouble())
                    val northX = center.x + (maxRadius + 8f) * cos(northAngleRad).toFloat()
                    val northY = center.y + (maxRadius + 8f) * sin(northAngleRad).toFloat()
                    drawCircle(color = Color.Red, radius = 4f, center = Offset(northX, northY))

                    // Draw peer blips
                    if (myLocation != null) {
                        peerLocations.values.forEach { peer ->
                            if (peer.latitude != 0.0) {
                                val d = RadarMath.haversineDistance(
                                    myLocation!!.latitude, myLocation!!.longitude,
                                    peer.latitude, peer.longitude
                                )
                                val b = RadarMath.initialBearing(
                                    myLocation!!.latitude, myLocation!!.longitude,
                                    peer.latitude, peer.longitude
                                )
                                val rel = RadarMath.relativeBearing(heading, b)
                                val angleRad = Math.toRadians((rel - 90f).toDouble())
                                val distFraction = (d / 2000.0).coerceIn(0.1, 1.0).toFloat()
                                val blipX = center.x + maxRadius * distFraction * cos(angleRad).toFloat()
                                val blipY = center.y + maxRadius * distFraction * sin(angleRad).toFloat()

                                val blipColor = if (peer.peerId == targetPeer?.peerId) Color.Yellow else Color(0xFF00E5FF)
                                drawCircle(color = blipColor, radius = 6f, center = Offset(blipX, blipY))
                            }
                        }
                    }

                    // Pointer needle to target peer
                    if (targetBearing != null) {
                        rotate(degrees = relativeAngle, pivot = center) {
                            val arrowPath = Path().apply {
                                moveTo(center.x, center.y - maxRadius * 0.85f)
                                lineTo(center.x - 14f, center.y - maxRadius * 0.65f)
                                lineTo(center.x, center.y - maxRadius * 0.72f)
                                lineTo(center.x + 14f, center.y - maxRadius * 0.65f)
                                close()
                            }
                            drawPath(arrowPath, color = Color(0xFF00E5FF))
                        }
                    }

                    // Center self dot
                    drawCircle(color = Color.White, radius = 6f, center = center)
                }

                // Compass heading text in center
                Text(
                    text = "${heading.toInt()}°\n${RadarMath.cardinalDirection(heading)}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 44.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Peer selector chips
            Text("Select Peer to Track:", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))

            val allTargets = peerLocations.values.toList()
            if (allTargets.isEmpty()) {
                Text(
                    "No peers broadcasting GPS yet.\nEnsure other devices have TalkLoop open.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(allTargets) { peer ->
                        val isSelected = peer.peerId == targetPeer?.peerId
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectRadarPeer(peer.peerId) },
                            label = { Text(peer.peerName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00E5FF),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF1E2633),
                                labelColor = Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}
