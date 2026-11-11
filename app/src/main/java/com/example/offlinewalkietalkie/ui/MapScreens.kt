package com.example.offlinewalkietalkie.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinewalkietalkie.data.OfflineMapArea
import com.example.offlinewalkietalkie.data.mbtiles.MbTilesPackage
import com.example.offlinewalkietalkie.theme.*
import com.example.offlinewalkietalkie.ui.components.GlassSurface
import kotlinx.coroutines.delay

/**
 * Apple Maps & Find My-inspired Discover Map Screen for shared peer locations and offline GPS telemetry.
 */
@Composable
fun DiscoverMapScreen(viewModel: WalkieTalkieViewModel, onBack: () -> Unit) {
    val locations by viewModel.peerLocations.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val heading by viewModel.deviceHeading.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        viewModel.refreshLocation()
        while (true) {
            delay(1500)
            now = System.currentTimeMillis()
            viewModel.refreshLocation()
        }
    }

    val infinitePulse = rememberInfiniteTransition(label = "gps_beacon_pulse")
    val pulseRadius by infinitePulse.animateFloat(
        initialValue = 10f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_radius"
    )
    val pulseAlpha by infinitePulse.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Box(Modifier.fillMaxSize().background(AppleBackground).safeDrawingPadding().imePadding().padding(16.dp)) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppleTextPrimary) }
                    Column {
                        Text("Discover Map", style = AppleTitle2)
                        Text("Offline GPS & Mesh Location Telemetry", style = AppleFootnote, color = AppleTextSecondary)
                    }
                }
                IconButton(onClick = { viewModel.refreshLocation() }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Refresh GPS", tint = AppleSystemBlue)
                }
            }

            Spacer(Modifier.height(10.dp))

            // 1. Hero "My Location & GPS Status" Card
            GlassSurface(cornerRadius = 20.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("MY CURRENT GPS FIX", style = AppleCaption2)
                        val hasLoc = currentLocation != null
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (hasLoc) AppleSystemGreen else AppleSystemOrange)
                            )
                            Text(
                                text = if (hasLoc) "Locked • ${currentLocation?.provider?.uppercase() ?: "GPS"}" else "Acquiring Satellites…",
                                style = AppleCaption1,
                                color = if (hasLoc) AppleSystemGreen else AppleSystemOrange,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    val loc = currentLocation
                    if (loc != null) {
                        val latStr = "%.6f° %s".format(kotlin.math.abs(loc.latitude), if (loc.latitude >= 0) "N" else "S")
                        val lonStr = "%.6f° %s".format(kotlin.math.abs(loc.longitude), if (loc.longitude >= 0) "E" else "W")
                        Text(
                            text = "$latStr, $lonStr",
                            style = AppleTitle2.copy(fontSize = 18.sp),
                            color = AppleTextPrimary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (loc.hasAccuracy()) {
                                Text("Accuracy: ±${"%.1f".format(loc.accuracy)}m", style = AppleMonospacedDigits.copy(fontSize = 11.sp, color = AppleTextSecondary))
                            }
                            if (loc.hasAltitude()) {
                                Text("Alt: ${loc.altitude.toInt()}m", style = AppleMonospacedDigits.copy(fontSize = 11.sp, color = AppleTextSecondary))
                            }
                            Text("Heading: ${heading.toInt()}°", style = AppleMonospacedDigits.copy(fontSize = 11.sp, color = AppleTextSecondary))
                        }
                    } else {
                        Text(
                            text = "Waiting for GPS satellite fix…",
                            style = AppleHeadline,
                            color = AppleTextSecondary
                        )
                        Text(
                            text = "Native satellite tracking active. Works 100% offline with zero internet.",
                            style = AppleCaption1,
                            color = AppleTextTertiary
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // 2. Interactive Schematic Tactical Map Canvas
            GlassSurface(cornerRadius = 22.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth().height(260.dp)) {
                Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                    val canvasW = size.width
                    val canvasH = size.height
                    val centerPt = Offset(canvasW / 2f, canvasH / 2f)

                    // Concentric Tactical Distance Rings
                    val ringRadii = listOf(canvasW * 0.15f, canvasW * 0.28f, canvasW * 0.42f)
                    val ringColor = Color(0x180A84FF)
                    ringRadii.forEach { r ->
                        drawCircle(ringColor, radius = r, center = centerPt, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
                    }

                    // Tactical Crosshairs
                    drawLine(ringColor, Offset(centerPt.x, 0f), Offset(centerPt.x, canvasH), strokeWidth = 1f)
                    drawLine(ringColor, Offset(0f, centerPt.y), Offset(canvasW, centerPt.y), strokeWidth = 1f)

                    val myLoc = currentLocation
                    val validPeers = locations.values.filter { it.latitude != 0.0 && it.longitude != 0.0 }

                    if (myLoc != null) {
                        // Draw User Pulsing Beacon
                        drawCircle(AppleSystemBlue.copy(alpha = pulseAlpha), radius = pulseRadius, center = centerPt)
                        drawCircle(AppleSystemBlue, radius = 7f, center = centerPt)
                        drawCircle(Color.White, radius = 3.5f, center = centerPt)

                        // Draw Heading Directional Pointer Line
                        val headingRad = Math.toRadians((heading - 90f).toDouble())
                        val ptrLen = 32f
                        val ptrX = centerPt.x + ptrLen * kotlin.math.cos(headingRad).toFloat()
                        val ptrY = centerPt.y + ptrLen * kotlin.math.sin(headingRad).toFloat()
                        drawLine(AppleSystemBlue, centerPt, Offset(ptrX, ptrY), strokeWidth = 2.5f)

                        // Draw Peer Location Blips relative to User
                        validPeers.forEach { peer ->
                            val distMeters = com.example.offlinewalkietalkie.radar.RadarMath.haversineDistance(
                                myLoc.latitude, myLoc.longitude,
                                peer.latitude, peer.longitude
                            )
                            val bearing = com.example.offlinewalkietalkie.radar.RadarMath.initialBearing(
                                myLoc.latitude, myLoc.longitude,
                                peer.latitude, peer.longitude
                            )
                            val relAngle = com.example.offlinewalkietalkie.radar.RadarMath.relativeBearing(heading, bearing)
                            val rad = Math.toRadians((relAngle - 90f).toDouble())

                            // Scale: 2000 meters maps to outer ring radius
                            val maxVisualRange = 2000.0
                            val scaleFraction = (distMeters / maxVisualRange).coerceIn(0.12, 0.95).toFloat()
                            val blipRadius = ringRadii.last() * scaleFraction
                            val blipX = (centerPt.x + blipRadius * kotlin.math.cos(rad).toFloat()).coerceIn(16f, canvasW - 16f)
                            val blipY = (centerPt.y + blipRadius * kotlin.math.sin(rad).toFloat()).coerceIn(16f, canvasH - 16f)

                            val isFresh = (now - peer.timestamp) < 120_000L
                            val dotColor = if (isFresh) AppleSystemTeal else AppleSystemOrange
                            drawCircle(dotColor.copy(alpha = 0.3f), radius = 12f, center = Offset(blipX, blipY))
                            drawCircle(dotColor, radius = 6f, center = Offset(blipX, blipY))
                            drawCircle(Color.White, radius = 2.5f, center = Offset(blipX, blipY))
                        }
                    } else {
                        // User GPS not yet locked: draw scanning radar sweep indicator
                        drawCircle(AppleSystemOrange.copy(alpha = 0.25f), radius = 22f, center = centerPt)
                        drawCircle(AppleSystemOrange, radius = 6f, center = centerPt)
                    }
                }
            }

            Text(
                text = "Concentric radar ranges: 250m • 500m • 1km • Blue is you, Cyan is active mesh peers",
                style = AppleCaption1,
                color = AppleTextTertiary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )

            Spacer(Modifier.height(4.dp))

            // 3. Discovered Peers List
            if (locations.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "No peers sharing location yet",
                            style = AppleHeadline,
                            color = AppleTextSecondary
                        )
                        Text(
                            text = "When connected via Bluetooth walkie-talkie,\npeers automatically appear on this tactical map.",
                            style = AppleFootnote,
                            color = AppleTextTertiary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(locations.values.toList(), key = { it.peerId }) { peer ->
                        val age = (now - peer.timestamp).coerceAtLeast(0L)
                        val myLoc = currentLocation
                        val distStr = if (myLoc != null && peer.latitude != 0.0) {
                            val d = com.example.offlinewalkietalkie.radar.RadarMath.haversineDistance(
                                myLoc.latitude, myLoc.longitude,
                                peer.latitude, peer.longitude
                            )
                            if (d >= 1000.0) "%.1f km away".format(d / 1000.0) else "${d.toInt()} m away"
                        } else null

                        GlassSurface(cornerRadius = 16.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(14.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(peer.peerName, style = AppleHeadline)
                                    Text(
                                        text = "${"%.5f".format(peer.latitude)}°, ${"%.5f".format(peer.longitude)}°",
                                        style = AppleMonospacedDigits.copy(fontSize = 11.sp, color = AppleTextSecondary)
                                    )
                                    if (distStr != null) {
                                        Text(distStr, style = AppleCaption1, color = AppleSystemBlue, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Text(
                                    text = if (age < 120_000L) "Live • ${age / 1000}s" else "Updated ${age / 60_000}m ago",
                                    style = AppleCaption1,
                                    color = if (age < 120_000L) AppleSystemGreen else AppleSystemOrange,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Apple-inspired Offline Vector Map & MBTiles Screen.
 */
@Composable
fun OfflineMapsScreen(viewModel: WalkieTalkieViewModel, onBack: () -> Unit) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Road Vectors state
    val areas by viewModel.offlineMapAreas.collectAsState()
    val progress by viewModel.offlineMapProgressBytes.collectAsState()
    val error by viewModel.offlineMapError.collectAsState()
    val selectedArea by viewModel.selectedOfflineMap.collectAsState()
    val roadLines by viewModel.selectedOfflineMapRoads.collectAsState()
    val storageUsed by viewModel.offlineMapStorageUsed.collectAsState()
    val storageQuota = viewModel.offlineMapStorageQuota
    var name by rememberSaveable { mutableStateOf("My Area") }
    val loc by viewModel.currentLocation.collectAsState()
    var latitude by rememberSaveable { mutableStateOf(loc?.latitude?.toString() ?: "25.2048") }
    var longitude by rememberSaveable { mutableStateOf(loc?.longitude?.toString() ?: "55.2708") }
    var radius by rememberSaveable { mutableStateOf("1.0") }
    var isPaused by rememberSaveable { mutableStateOf(false) }
    val validCoordinates = latitude.toDoubleOrNull()?.let { it.isFinite() && it in -85.0..85.0 } == true && longitude.toDoubleOrNull()?.let { it.isFinite() && it in -180.0..180.0 } == true
    val validRadius = radius.toDoubleOrNull()?.let { it.isFinite() && it in 0.2..2.0 } == true

    // MBTiles state
    val mbTilesPackages by viewModel.mbTilesPackages.collectAsState()
    val selectedMbTiles by viewModel.selectedMbTilesPackage.collectAsState()
    val tilePreview by viewModel.selectedTilePreview.collectAsState()
    val mbTilesUsed by viewModel.mbTilesStorageUsed.collectAsState()
    val mbTilesQuota = viewModel.mbTilesStorageQuota

    val mbtilesFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importMbTiles(uri)
        }
    }

    Box(Modifier.fillMaxSize().background(AppleBackground).safeDrawingPadding().imePadding().padding(16.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppleTextPrimary) }
                Column {
                    Text("Offline Vector Maps", style = AppleTitle2)
                    Text("Pre-bundled OSM road networks & SQLite MBTiles", style = AppleFootnote, color = AppleTextSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Apple-style Pill Segmented Tab Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppleCardSurfaceSecondary)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selectedTab == 0) AppleCardSurfaceTertiary else Color.Transparent)
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Road Vectors (OSM)",
                        style = AppleSubheadline,
                        color = if (selectedTab == 0) AppleTextPrimary else AppleTextSecondary,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selectedTab == 1) AppleCardSurfaceTertiary else Color.Transparent)
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "MBTiles Packages",
                        style = AppleSubheadline,
                        color = if (selectedTab == 1) AppleTextPrimary else AppleTextSecondary,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (selectedTab == 0) {
                // TAB 0: ROAD VECTORS (OSM OVERPASS)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                    item {
                        val usedKb = (storageUsed / 1024).coerceAtLeast(1)
                        val quotaMb = (storageQuota / (1024 * 1024)).toInt()
                        val progressRatio = (storageUsed.toFloat() / storageQuota.toFloat()).coerceIn(0f, 1f)
                        GlassSurface(cornerRadius = 20.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("STORAGE QUOTA & CACHE", style = AppleCaption2)
                                    if (areas.isNotEmpty()) {
                                        TextButton(
                                            onClick = { viewModel.clearAllOfflineMaps() },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Purge All Packs", color = AppleSystemRed, style = AppleCaption1, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { progressRatio },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = if (progressRatio > 0.85f) AppleSystemOrange else AppleSystemBlue,
                                    trackColor = AppleCardSurfaceSecondary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("$usedKb KB used", style = AppleMonospacedDigits.copy(fontSize = 11.sp, color = AppleTextSecondary))
                                    Text("$quotaMb MB storage limit", style = AppleMonospacedDigits.copy(fontSize = 11.sp, color = AppleTextTertiary))
                                }
                            }
                        }
                    }
                    if (selectedArea != null) item {
                        GlassSurface(cornerRadius = 20.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("${selectedArea!!.name} • Vector Road Geometry", style = AppleHeadline)
                                    TextButton(onClick = viewModel::closeOfflineMap) { Text("Close", color = AppleSystemBlue, fontWeight = FontWeight.Bold) }
                                }
                                Spacer(Modifier.height(8.dp))
                                Canvas(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0C1018))) {
                                    val points = roadLines.flatten()
                                    if (points.isNotEmpty()) {
                                        val minLat = points.minOf { it.latitude }; val maxLat = points.maxOf { it.latitude }
                                        val minLon = points.minOf { it.longitude }; val maxLon = points.maxOf { it.longitude }
                                        val latSpan = (maxLat - minLat).coerceAtLeast(0.00001)
                                        val lonSpan = (maxLon - minLon).coerceAtLeast(0.00001)
                                        roadLines.forEach { line ->
                                            for (i in 0 until line.lastIndex) {
                                                fun project(point: com.example.offlinewalkietalkie.data.OfflineMapPoint) = Offset(
                                                    12f + (size.width - 24f) * ((point.longitude - minLon) / lonSpan).toFloat(),
                                                    12f + (size.height - 24f) * ((maxLat - point.latitude) / latSpan).toFloat()
                                                )
                                                drawLine(AppleSystemTeal, project(line[i]), project(line[i + 1]), strokeWidth = 2.5f)
                                            }
                                        }
                                    }
                                }
                                Text("Rendered entirely offline with zero network connectivity • © OpenStreetMap contributors", style = AppleCaption1, color = AppleTextTertiary, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                    item {
                        GlassSurface(cornerRadius = 20.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("DOWNLOAD VECTOR AREA", style = AppleCaption2)
                                val fieldColors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = AppleTextPrimary,
                                    unfocusedTextColor = AppleTextPrimary,
                                    focusedBorderColor = AppleSystemRed,
                                    unfocusedBorderColor = AppleGlassBorder,
                                    focusedContainerColor = AppleCardSurfaceSecondary,
                                    unfocusedContainerColor = AppleCardSurfaceSecondary,
                                    cursorColor = AppleSystemRed,
                                    focusedLabelColor = AppleSystemRed,
                                    unfocusedLabelColor = AppleTextSecondary
                                )
                                val fieldShape = RoundedCornerShape(12.dp)

                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text("Area Name") },
                                    colors = fieldColors,
                                    shape = fieldShape,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = latitude,
                                        onValueChange = { latitude = it },
                                        label = { Text("Latitude") },
                                        colors = fieldColors,
                                        shape = fieldShape,
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = longitude,
                                        onValueChange = { longitude = it },
                                        label = { Text("Longitude") },
                                        colors = fieldColors,
                                        shape = fieldShape,
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                                OutlinedTextField(
                                    value = radius,
                                    onValueChange = { radius = it },
                                    label = { Text("Radius (0.2 - 2.0 km)") },
                                    colors = fieldColors,
                                    shape = fieldShape,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("Downloads vector geometries from OpenStreetMap. Public Overpass limits apply.", style = AppleFootnote, color = AppleTextTertiary)
                                if (progress != null) {
                                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = AppleSystemBlue)
                                    Text("Downloading • ${(progress!! / 1024).coerceAtLeast(1)} KB received", style = AppleMonospacedDigits, color = AppleTextPrimary)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { viewModel.cancelOfflineMapDownload(); isPaused = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                                            Icon(Icons.Default.Pause, null); Spacer(Modifier.width(6.dp)); Text("Pause")
                                        }
                                        OutlinedButton(onClick = { viewModel.cancelOfflineMapDownload(); isPaused = false }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                                    }
                                } else {
                                    Button(
                                        enabled = validCoordinates && validRadius && name.isNotBlank(),
                                        onClick = {
                                            val lat = latitude.toDoubleOrNull(); val lon = longitude.toDoubleOrNull(); val km = radius.toDoubleOrNull()
                                            if (lat != null && lon != null && km != null) {
                                                viewModel.downloadOfflineMap(name, lat, lon, km)
                                                isPaused = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AppleSystemBlue, contentColor = Color.White),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().height(46.dp)
                                    ) {
                                        Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Download, null)
                                        Spacer(Modifier.width(8.dp)); Text(if (isPaused) "Resume Download" else "Download Area Pack", fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (!validCoordinates) Text("Enter latitude (-85 to 85) and longitude (-180 to 180).", color = AppleSystemRed, style = AppleFootnote)
                                if (!validRadius) Text("Choose a radius between 0.2 and 2.0 km.", color = AppleSystemRed, style = AppleFootnote)
                                if (error != null) Text(error!!, color = AppleSystemRed, style = AppleFootnote)
                            }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SAVED VECTOR MAP PACKS (${areas.size})", style = AppleCaption2)
                            TextButton(onClick = { viewModel.restoreBundledMapPacks() }) {
                                Text("Unpack Starter Packs", style = AppleSubheadline, color = AppleSystemBlue, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    items(areas, key = OfflineMapArea::id) { area ->
                        GlassSurface(cornerRadius = 16.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(area.name, style = AppleHeadline)
                                    Text("${area.roadCount} roads • ${area.radiusKm} km • ${(area.file.length() / 1024).coerceAtLeast(1)} KB", style = AppleFootnote, color = AppleTextSecondary)
                                }
                                Button(
                                    onClick = { viewModel.openOfflineMap(area) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppleCardSurfaceSecondary, contentColor = AppleSystemBlue),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                ) { Text("Open", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                IconButton(onClick = { viewModel.deleteOfflineMap(area) }) { Icon(Icons.Default.DeleteOutline, "Delete ${area.name}", tint = AppleTextTertiary) }
                            }
                        }
                    }
                    if (areas.isEmpty()) item { Text("No saved map areas yet.", style = AppleFootnote, color = AppleTextTertiary, modifier = Modifier.padding(8.dp)) }
                }
            } else {
                // TAB 1: MBTILES TILE PACKS (SQLITE ARCHIVES)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                    item {
                        val usedMb = (mbTilesUsed / (1024 * 1024)).coerceAtLeast(0)
                        val quotaMb = (mbTilesQuota / (1024 * 1024)).toInt()
                        val progressRatio = (mbTilesUsed.toFloat() / mbTilesQuota.toFloat()).coerceIn(0f, 1f)
                        GlassSurface(cornerRadius = 20.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("MBTILES STORAGE & QUOTA", style = AppleCaption2)
                                    if (mbTilesPackages.isNotEmpty()) {
                                        TextButton(
                                            onClick = { viewModel.clearAllMbTiles() },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Purge All Packs", color = AppleSystemRed, style = AppleCaption1, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { progressRatio },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = if (progressRatio > 0.85f) AppleSystemOrange else AppleSystemTeal,
                                    trackColor = AppleCardSurfaceSecondary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("$usedMb MB used", style = AppleMonospacedDigits.copy(fontSize = 11.sp, color = AppleTextSecondary))
                                    Text("$quotaMb MB max quota", style = AppleMonospacedDigits.copy(fontSize = 11.sp, color = AppleTextTertiary))
                                }
                            }
                        }
                    }

                    // Selected Package Inspector & Tile Viewer
                    if (selectedMbTiles != null) {
                        item {
                            GlassSurface(cornerRadius = 20.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column {
                                            Text(selectedMbTiles!!.metadata.name, style = AppleHeadline)
                                            Text("${selectedMbTiles!!.metadata.format.uppercase()} Format • ${selectedMbTiles!!.metadata.tileCount} Tiles", style = AppleFootnote, color = AppleTextSecondary)
                                        }
                                        TextButton(onClick = { viewModel.selectMbTiles(null) }) {
                                            Text("Close", color = AppleSystemBlue, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Tile Canvas / Bitmap Display
                                    if (tilePreview?.bitmap != null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(240.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF0C1018)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                bitmap = tilePreview!!.bitmap!!.asImageBitmap(),
                                                contentDescription = "MBTiles Tile Preview",
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            // Tactical coordinate HUD
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(8.dp)
                                                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    "Tile (z=${tilePreview!!.zoom}, x=${tilePreview!!.column}, y=${tilePreview!!.row})",
                                                    style = AppleMonospacedDigits.copy(fontSize = 10.sp, color = AppleSystemTeal)
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(100.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF0C1018)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "Vector MBTiles Package (PBF Protobuf Layers)\nReady for MapLibre/Offline Vector Rendering",
                                                style = AppleFootnote,
                                                color = AppleSystemTeal,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }

                                    // Bounds & Attribution
                                    selectedMbTiles!!.metadata.bounds?.let { b ->
                                        Text("Geographic Bounds: $b", style = AppleCaption1, color = AppleTextTertiary)
                                    }
                                    selectedMbTiles!!.metadata.attribution?.let { a ->
                                        Text(a, style = AppleCaption1, color = AppleTextTertiary)
                                    }
                                }
                            }
                        }
                    }

                    // Import and Unpack Actions
                    item {
                        GlassSurface(cornerRadius = 20.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("IMPORT & UNPACK PACKS", style = AppleCaption2)
                                Text(
                                    "Import standard .mbtiles packages (Mapbox / OpenMapTiles SQLite) from device storage for complete offline topological and regional basemaps.",
                                    style = AppleFootnote,
                                    color = AppleTextSecondary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { mbtilesFilePicker.launch(arrayOf("*/*")) },
                                        colors = ButtonDefaults.buttonColors(containerColor = AppleSystemBlue, contentColor = Color.White),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).height(44.dp)
                                    ) {
                                        Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Import .mbtiles", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.restoreBundledMbTiles() },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).height(44.dp)
                                    ) {
                                        Icon(Icons.Default.Layers, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Unpack Starter", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Saved MBTiles Packages Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("INSTALLED MBTILES PACKS (${mbTilesPackages.size})", style = AppleCaption2)
                        }
                    }

                    // Saved MBTiles Items
                    items(mbTilesPackages, key = MbTilesPackage::id) { pack ->
                        GlassSurface(cornerRadius = 16.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(pack.metadata.name, style = AppleHeadline)
                                    Text(
                                        "${pack.metadata.format.uppercase()} • z${pack.metadata.minZoom}-z${pack.metadata.maxZoom} • ${pack.metadata.tileCount} tiles • ${(pack.file.length() / 1024).coerceAtLeast(1)} KB",
                                        style = AppleFootnote,
                                        color = AppleTextSecondary
                                    )
                                    pack.metadata.description?.let { desc ->
                                        Text(desc, style = AppleCaption1, color = AppleTextTertiary, maxLines = 1)
                                    }
                                }
                                Button(
                                    onClick = { viewModel.selectMbTiles(pack) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppleCardSurfaceSecondary, contentColor = AppleSystemBlue),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                ) {
                                    Text("Inspect", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                IconButton(onClick = { viewModel.deleteMbTiles(pack) }) {
                                    Icon(Icons.Default.DeleteOutline, "Delete ${pack.metadata.name}", tint = AppleTextTertiary)
                                }
                            }
                        }
                    }

                    if (mbTilesPackages.isEmpty()) {
                        item {
                            Text(
                                "No MBTiles packages installed yet. Tap 'Unpack Starter' or 'Import .mbtiles' to load an offline regional basemap.",
                                style = AppleFootnote,
                                color = AppleTextTertiary,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
