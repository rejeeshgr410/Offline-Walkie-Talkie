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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinewalkietalkie.R
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
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = AppleTextPrimary) }
                    Column {
                        Text(stringResource(R.string.discover_map), style = AppleTitle2)
                        Text(stringResource(R.string.discover_map_subtitle), style = AppleFootnote, color = AppleTextSecondary)
                    }
                }
                IconButton(onClick = { viewModel.refreshLocation() }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.refresh_gps), tint = AppleSystemBlue)
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
                        Text(stringResource(R.string.current_gps_fix), style = AppleCaption2)
                        val hasLoc = currentLocation != null
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (hasLoc) AppleSystemGreen else AppleSystemOrange)
                            )
                            Text(
                                text = if (hasLoc) stringResource(R.string.locked_provider, currentLocation?.provider?.uppercase() ?: "GPS") else stringResource(R.string.acquiring_satellites),
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
                            // Keep coordinate order stable inside an RTL interface.
                            text = "\u2066$latStr, $lonStr\u2069",
                            style = AppleTitle2.copy(fontSize = 18.sp),
                            color = AppleTextPrimary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (loc.hasAccuracy()) {
                                Text(stringResource(R.string.gps_accuracy, "%.1f".format(loc.accuracy)), style = AppleMonospacedDigits.copy(fontSize = 11.sp, color = AppleTextSecondary))
                            }
                            if (loc.hasAltitude()) {
                                Text(stringResource(R.string.gps_altitude, loc.altitude.toInt()), style = AppleMonospacedDigits.copy(fontSize = 11.sp, color = AppleTextSecondary))
                            }
                            Text(stringResource(R.string.gps_heading, heading.toInt()), style = AppleMonospacedDigits.copy(fontSize = 11.sp, color = AppleTextSecondary))
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.waiting_gps),
                            style = AppleHeadline,
                            color = AppleTextSecondary
                        )
                        Text(
                            text = stringResource(R.string.gps_offline_note),
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
                text = stringResource(R.string.map_legend),
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
                            text = stringResource(R.string.no_location_peers),
                            style = AppleHeadline,
                            color = AppleTextSecondary
                        )
                        Text(
                            text = stringResource(R.string.location_peer_note),
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
                            if (d >= 1000.0) stringResource(R.string.distance_km_away, "%.1f".format(d / 1000.0)) else stringResource(R.string.distance_m_away, d.toInt())
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
                                    text = if (age < 120_000L) stringResource(R.string.location_live, age / 1000) else stringResource(R.string.location_updated, age / 60_000),
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
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = AppleTextPrimary) }
                Column {
                    Text(stringResource(R.string.offline_vector_maps), style = AppleTitle2)
                    Text(stringResource(R.string.offline_vector_maps_subtitle), style = AppleFootnote, color = AppleTextSecondary)
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
                        stringResource(R.string.road_vectors),
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
                        stringResource(R.string.mbtiles_packages),
                        style = AppleSubheadline,
                        color = if (selectedTab == 1) AppleTextPrimary else AppleTextSecondary,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (selectedTab == 0) {
                var catalogQuery by remember { mutableStateOf("") }
                // TAB 0: ROAD VECTORS (OSM OVERPASS)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                    item {
                        OutlinedTextField(value = catalogQuery, onValueChange = { catalogQuery = it },
                            label = { Text("Search area catalogue") }, modifier = Modifier.fillMaxWidth())
                        if (catalogQuery.isNotBlank()) {
                            com.example.offlinewalkietalkie.data.MapAreaCatalog.search(catalogQuery).forEach { area ->
                                TextButton(onClick = {
                                    latitude = area.latitude.toString()
                                    longitude = area.longitude.toString()
                                    name = area.name
                                    catalogQuery = ""
                                }) { Text(area.name) }
                            }
                        }
                        TextButton(onClick = { viewModel.resumeOfflineMapDownload() }) {
                            Text("Resume last area download")
                        }
                    }
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
                                    Text(stringResource(R.string.storage_quota_cache), style = AppleCaption2)
                                    if (areas.isNotEmpty()) {
                                        TextButton(
                                            onClick = { viewModel.clearAllOfflineMaps() },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(stringResource(R.string.purge_all_packs), color = AppleSystemRed, style = AppleCaption1, fontWeight = FontWeight.SemiBold)
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
                                    Text(stringResource(R.string.kb_used, usedKb), style = AppleMonospacedDigits.copy(fontSize = 11.sp, color = AppleTextSecondary))
                                    Text(stringResource(R.string.mb_storage_limit, quotaMb), style = AppleMonospacedDigits.copy(fontSize = 11.sp, color = AppleTextTertiary))
                                }
                            }
                        }
                    }
                    if (selectedArea != null) item {
                        GlassSurface(cornerRadius = 20.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.vector_road_geometry, selectedArea!!.name), style = AppleHeadline)
                                    TextButton(onClick = viewModel::closeOfflineMap) { Text(stringResource(R.string.close), color = AppleSystemBlue, fontWeight = FontWeight.Bold) }
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
                                Text(stringResource(R.string.offline_rendering_note), style = AppleCaption1, color = AppleTextTertiary, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                    item {
                        GlassSurface(cornerRadius = 20.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(stringResource(R.string.download_vector_area), style = AppleCaption2)
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
                                    label = { Text(stringResource(R.string.area_name)) },
                                    colors = fieldColors,
                                    shape = fieldShape,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = latitude,
                                        onValueChange = { latitude = it },
                                        label = { Text(stringResource(R.string.latitude)) },
                                        colors = fieldColors,
                                        shape = fieldShape,
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = longitude,
                                        onValueChange = { longitude = it },
                                        label = { Text(stringResource(R.string.longitude)) },
                                        colors = fieldColors,
                                        shape = fieldShape,
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                                OutlinedTextField(
                                    value = radius,
                                    onValueChange = { radius = it },
                                    label = { Text(stringResource(R.string.radius_km)) },
                                    colors = fieldColors,
                                    shape = fieldShape,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(stringResource(R.string.vector_download_note), style = AppleFootnote, color = AppleTextTertiary)
                                if (progress != null) {
                                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = AppleSystemBlue)
                                    Text(stringResource(R.string.downloading_kb, (progress!! / 1024).coerceAtLeast(1)), style = AppleMonospacedDigits, color = AppleTextPrimary)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { viewModel.cancelOfflineMapDownload(); isPaused = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                                            Icon(Icons.Default.Pause, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.pause))
                                        }
                                        OutlinedButton(onClick = { viewModel.cancelOfflineMapDownload(); isPaused = false }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text(stringResource(R.string.cancel)) }
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
                                        Spacer(Modifier.width(8.dp)); Text(if (isPaused) stringResource(R.string.resume_download) else stringResource(R.string.download_area_pack), fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (!validCoordinates) Text(stringResource(R.string.invalid_coordinates), color = AppleSystemRed, style = AppleFootnote)
                                if (!validRadius) Text(stringResource(R.string.invalid_radius), color = AppleSystemRed, style = AppleFootnote)
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
                            Text(stringResource(R.string.saved_vector_packs, areas.size), style = AppleCaption2)
                            TextButton(onClick = { viewModel.restoreBundledMapPacks() }) {
                                Text(stringResource(R.string.unpack_starter_packs), style = AppleSubheadline, color = AppleSystemBlue, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    items(areas, key = OfflineMapArea::id) { area ->
                        GlassSurface(cornerRadius = 16.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(area.name, style = AppleHeadline)
                                    Text(stringResource(R.string.map_pack_summary, area.roadCount, area.radiusKm.toString(), (area.file.length() / 1024).coerceAtLeast(1)), style = AppleFootnote, color = AppleTextSecondary)
                                }
                                Button(
                                    onClick = { viewModel.openOfflineMap(area) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppleCardSurfaceSecondary, contentColor = AppleSystemBlue),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                ) { Text(stringResource(R.string.open), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                IconButton(onClick = { viewModel.deleteOfflineMap(area) }) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete_named, area.name), tint = AppleTextTertiary) }
                            }
                        }
                    }
                    if (areas.isEmpty()) item { Text(stringResource(R.string.no_saved_map_areas), style = AppleFootnote, color = AppleTextTertiary, modifier = Modifier.padding(8.dp)) }
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
                                    Text(stringResource(R.string.mbtiles_storage_quota), style = AppleCaption2)
                                    if (mbTilesPackages.isNotEmpty()) {
                                        TextButton(
                                            onClick = { viewModel.clearAllMbTiles() },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(stringResource(R.string.purge_all_packs), color = AppleSystemRed, style = AppleCaption1, fontWeight = FontWeight.SemiBold)
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
                                    Text(stringResource(R.string.mb_used, usedMb), style = AppleMonospacedDigits.copy(fontSize = 11.sp, color = AppleTextSecondary))
                                    Text(stringResource(R.string.mb_max_quota, quotaMb), style = AppleMonospacedDigits.copy(fontSize = 11.sp, color = AppleTextTertiary))
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
                                            Text(stringResource(R.string.mbtiles_format, selectedMbTiles!!.metadata.format.uppercase(), selectedMbTiles!!.metadata.tileCount), style = AppleFootnote, color = AppleTextSecondary)
                                        }
                                        TextButton(onClick = { viewModel.selectMbTiles(null) }) {
                                            Text(stringResource(R.string.close), color = AppleSystemBlue, fontWeight = FontWeight.Bold)
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
                                                contentDescription = stringResource(R.string.mbtiles_preview),
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
                                                    stringResource(R.string.tile_coordinates, tilePreview!!.zoom, tilePreview!!.column, tilePreview!!.row),
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
                                                stringResource(R.string.vector_mbtiles_ready),
                                                style = AppleFootnote,
                                                color = AppleSystemTeal,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }

                                    // Bounds & Attribution
                                    selectedMbTiles!!.metadata.bounds?.let { b ->
                                        Text(stringResource(R.string.geographic_bounds, b), style = AppleCaption1, color = AppleTextTertiary)
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
                                Text(stringResource(R.string.import_unpack_packs), style = AppleCaption2)
                                Text(
                                    stringResource(R.string.mbtiles_import_description),
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
                                        Text(stringResource(R.string.import_mbtiles), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.restoreBundledMbTiles() },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).height(44.dp)
                                    ) {
                                        Icon(Icons.Default.Layers, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.unpack_starter), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                            Text(stringResource(R.string.installed_mbtiles_packs, mbTilesPackages.size), style = AppleCaption2)
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
                                    Text(stringResource(R.string.inspect), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                IconButton(onClick = { viewModel.deleteMbTiles(pack) }) {
                                    Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete_named, pack.metadata.name), tint = AppleTextTertiary)
                                }
                            }
                        }
                    }

                    if (mbTilesPackages.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.no_mbtiles_packages),
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
