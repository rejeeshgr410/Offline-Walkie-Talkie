package com.example.offlinewalkietalkie.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
 * Apple Maps-inspired Discover Map Screen for shared peer locations.
 */
@Composable
fun DiscoverMapScreen(viewModel: WalkieTalkieViewModel, onBack: () -> Unit) {
    val locations by viewModel.peerLocations.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    Box(Modifier.fillMaxSize().background(AppleBackground).safeDrawingPadding().imePadding().padding(16.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppleTextPrimary) }
                Column {
                    Text("Discover Map", style = AppleTitle2)
                    Text("Mesh-shared coordinates from nearby peers", style = AppleFootnote, color = AppleTextSecondary)
                }
            }
            Spacer(Modifier.height(14.dp))
            GlassSurface(cornerRadius = 22.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth().height(300.dp)) {
                Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                    val grid = Color(0x1A0A84FF)
                    for (i in 1..5) {
                        val x = size.width * i / 6f; val y = size.height * i / 6f
                        drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
                        drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
                    }
                    val points = locations.values.filterNot { it.latitude == 0.0 && it.longitude == 0.0 }
                    val latitudes = points.map { it.latitude } + listOfNotNull(currentLocation?.latitude)
                    val longitudes = points.map { it.longitude } + listOfNotNull(currentLocation?.longitude)
                    val minLat = (latitudes.minOrNull() ?: 0.0) - 0.002
                    val maxLat = (latitudes.maxOrNull() ?: 0.0) + 0.002
                    val minLon = (longitudes.minOrNull() ?: 0.0) - 0.002
                    val maxLon = (longitudes.maxOrNull() ?: 0.0) + 0.002
                    points.forEach { peer ->
                        if (peer.latitude == 0.0 && peer.longitude == 0.0) return@forEach
                        val x = (size.width * (peer.longitude - minLon) / (maxLon - minLon)).toFloat().coerceIn(16f, size.width - 16f)
                        val y = (size.height * (maxLat - peer.latitude) / (maxLat - minLat)).toFloat().coerceIn(16f, size.height - 16f)
                        drawCircle(if (now - peer.timestamp < 120_000L) AppleSystemTeal else AppleSystemOrange, 8f, Offset(x, y))
                    }
                    currentLocation?.let { loc ->
                        val x = (size.width * (loc.longitude - minLon) / (maxLon - minLon)).toFloat().coerceIn(16f, size.width - 16f)
                        val y = (size.height * (maxLat - loc.latitude) / (maxLat - minLat)).toFloat().coerceIn(16f, size.height - 16f)
                        drawCircle(Color(0xFFE60023), 7f, Offset(x, y))
                        drawCircle(Color(0xFFE60023).copy(alpha = 0.35f), 13f, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                    }
                }
            }
            Text("Schematic coordinate grid • Cyan is recent, amber is stale, red is you", style = AppleCaption1, color = AppleTextTertiary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
            Spacer(Modifier.height(8.dp))
            if (locations.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No peers have shared a location yet.\nEnable GPS and connect to nearby devices to see them here.", style = AppleFootnote, color = AppleTextTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(locations.values.toList(), key = { it.peerId }) { peer ->
                    val age = (now - peer.timestamp).coerceAtLeast(0L)
                    GlassSurface(cornerRadius = 16.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(peer.peerName, style = AppleHeadline)
                                Text("${"%.5f".format(peer.latitude)}, ${"%.5f".format(peer.longitude)}", style = AppleMonospacedDigits.copy(fontSize = 11.sp, color = AppleTextSecondary))
                            }
                            Text(if (age < 120_000L) "Live • ${age / 1000}s" else "Updated ${age / 60_000}m ago", style = AppleCaption1, color = if (age < 120_000L) AppleSystemGreen else AppleSystemOrange)
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
