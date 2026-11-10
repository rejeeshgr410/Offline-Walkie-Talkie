package com.example.offlinewalkietalkie.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinewalkietalkie.protocol.PeerLocation
import com.example.offlinewalkietalkie.radar.RadarMath
import com.example.offlinewalkietalkie.theme.*
import com.example.offlinewalkietalkie.ui.components.GlassSurface
import kotlin.math.cos
import kotlin.math.sin

/**
 * Apple Precision Finding / Compass-inspired Offline Directional Radar.
 */
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
                title = { Text("Directional Radar", style = AppleTitle2) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppleTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppleBackground,
                    titleContentColor = AppleTextPrimary,
                    navigationIconContentColor = AppleTextPrimary
                )
            )
        },
        containerColor = AppleBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Target telemetry badge (Apple Find My style)
            GlassSurface(
                cornerRadius = 22.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (targetPeer != null) targetPeer.peerName else "Scanning for Peers…",
                                style = AppleTitle2,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    targetPeer == null -> "Waiting for shared GPS beacons"
                                    locationIsFresh -> "Live • Updated ${peerLocationAgeMs!! / 1000}s ago"
                                    else -> "Cached • Updated ${peerLocationAgeMs!! / 60_000}m ago"
                                },
                                style = AppleFootnote,
                                color = if (locationIsFresh) AppleSystemGreen else AppleSystemOrange
                            )
                        }

                        if (distanceMeters != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = RadarMath.formatDistance(distanceMeters),
                                    style = AppleLargeTitle.copy(
                                        color = AppleSystemTeal,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    fontSize = 24.sp
                                )
                                if (targetBearing != null) {
                                    Text(
                                        text = "${targetBearing.toInt()}° ${RadarMath.cardinalDirection(targetBearing)}",
                                        style = AppleCaption1,
                                        color = AppleTextSecondary
                                    )
                                }
                            }
                        }
                    }

                    if (targetPeer != null && targetPeer.latitude != 0.0 && targetPeer.longitude != 0.0) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                val lat = targetPeer.latitude
                                val lon = targetPeer.longitude
                                val peerNameEncoded = Uri.encode(targetPeer.peerName)
                                val navIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lon")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon($peerNameEncoded)")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(navIntent) }.recoverCatching { context.startActivity(geoIntent) }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppleCardSurfaceSecondary,
                                contentColor = AppleTextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, tint = AppleSystemBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open in Maps Navigation", style = AppleHeadline, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Apple Compass / Radar Scope
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.width / 2f - 16f

                    // Subtle range rings
                    val ringFractions = floatArrayOf(0.33f, 0.66f, 1.0f)
                    for (fraction in ringFractions) {
                        drawCircle(
                            color = AppleGlassBorder,
                            radius = maxRadius * fraction,
                            center = center,
                            style = Stroke(width = 1f)
                        )
                    }

                    // Crosshairs
                    drawLine(
                        color = AppleGlassBorder,
                        start = Offset(center.x, center.y - maxRadius),
                        end = Offset(center.x, center.y + maxRadius),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = AppleGlassBorder,
                        start = Offset(center.x - maxRadius, center.y),
                        end = Offset(center.x + maxRadius, center.y),
                        strokeWidth = 1f
                    )

                    // North indicator
                    val northAngleRad = Math.toRadians((-heading - 90f).toDouble())
                    val northX = center.x + (maxRadius + 6f) * cos(northAngleRad).toFloat()
                    val northY = center.y + (maxRadius + 6f) * sin(northAngleRad).toFloat()
                    drawCircle(color = AppleSystemRed, radius = 3.5f, center = Offset(northX, northY))

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

                                val blipColor = if (peer.peerId == targetPeer?.peerId) AppleSystemTeal else AppleSystemBlue
                                drawCircle(color = blipColor, radius = 5.5f, center = Offset(blipX, blipY))
                            }
                        }
                    }

                    // Target bearing needle
                    if (targetBearing != null) {
                        rotate(degrees = relativeAngle, pivot = center) {
                            val arrowPath = Path().apply {
                                moveTo(center.x, center.y - maxRadius * 0.88f)
                                lineTo(center.x - 12f, center.y - maxRadius * 0.70f)
                                lineTo(center.x, center.y - maxRadius * 0.76f)
                                lineTo(center.x + 12f, center.y - maxRadius * 0.70f)
                                close()
                            }
                            drawPath(arrowPath, color = AppleSystemBlue)
                        }
                    }

                    // Self central dot
                    drawCircle(color = AppleSystemRed, radius = 5f, center = center)
                    drawCircle(color = AppleSystemBlue.copy(alpha = 0.4f), radius = 9f, center = center, style = Stroke(1.5f))
                }

                // Compass heading readout
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 40.dp)
                ) {
                    Text(
                        text = "${heading.toInt()}°",
                        style = AppleMonospacedDigits.copy(fontSize = 12.sp, color = AppleTextSecondary)
                    )
                    Text(
                        text = RadarMath.cardinalDirection(heading),
                        style = AppleCaption2.copy(fontSize = 10.sp, color = AppleTextTertiary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Peer selector chips
            Text(
                text = "TRACKED PEERS",
                style = AppleCaption2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 8.dp)
            )

            val allTargets = peerLocations.values.toList()
            if (allTargets.isEmpty()) {
                Text(
                    text = "No peers broadcasting GPS yet. Nearby devices appear automatically.",
                    style = AppleFootnote,
                    color = AppleTextTertiary,
                    modifier = Modifier.padding(vertical = 4.dp)
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
                            label = { Text(peer.peerName, style = AppleSubheadline) },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppleSystemBlue,
                                selectedLabelColor = Color.White,
                                containerColor = AppleCardSurface,
                                labelColor = AppleTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) AppleSystemBlue else AppleGlassBorder
                            )
                        )
                    }
                }
            }
        }
    }
}
