package com.example.offlinewalkietalkie.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinewalkietalkie.data.OfflineMapArea
import com.example.offlinewalkietalkie.theme.AtmosphericDarkBrush
import com.example.offlinewalkietalkie.ui.components.GlassSurface

@Composable
fun DiscoverMapScreen(viewModel: WalkieTalkieViewModel, onBack: () -> Unit) {
    val locations by viewModel.peerLocations.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    Box(Modifier.fillMaxSize().background(AtmosphericDarkBrush).safeDrawingPadding().imePadding().padding(16.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Column {
                    Text("Discover map", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Shared locations from nearby peers", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            GlassSurface(cornerRadius = 20.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth().height(300.dp)) {
                Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                    val grid = Color(0x3348C9D8)
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
                        drawCircle(if (now - peer.timestamp < 120_000L) Color(0xFF4DD0E1) else Color(0xFFFFB74D), 8f, Offset(x, y))
                    }
                    currentLocation?.let { loc ->
                        val x = (size.width * (loc.longitude - minLon) / (maxLon - minLon)).toFloat().coerceIn(16f, size.width - 16f)
                        val y = (size.height * (maxLat - loc.latitude) / (maxLat - minLat)).toFloat().coerceIn(16f, size.height - 16f)
                        drawCircle(Color.White, 7f, Offset(x, y))
                    }
                }
            }
            Text("Schematic coordinate grid · cyan is recent, amber is stale, white is you", color = Color(0xFFCBD5E1), fontSize = 10.sp, modifier = Modifier.padding(8.dp))
            if (locations.isEmpty()) {
                Text("No peers have shared a location yet. Connect and enable location sharing to see them here.", color = Color(0xFFCBD5E1), modifier = Modifier.padding(12.dp))
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(locations.values.toList(), key = { it.peerId }) { peer ->
                    val age = (now - peer.timestamp).coerceAtLeast(0L)
                    GlassSurface(cornerRadius = 14.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(peer.peerName, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("${"%.5f".format(peer.latitude)}, ${"%.5f".format(peer.longitude)}", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                            }
                            Text(if (age < 120_000L) "Live · ${age / 1000}s" else "Updated ${age / 60_000}m ago", color = if (age < 120_000L) Color(0xFF81C784) else Color(0xFFFFCC80), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OfflineMapsScreen(viewModel: WalkieTalkieViewModel, onBack: () -> Unit) {
    val areas by viewModel.offlineMapAreas.collectAsState()
    val progress by viewModel.offlineMapProgressBytes.collectAsState()
    val error by viewModel.offlineMapError.collectAsState()
    val selectedArea by viewModel.selectedOfflineMap.collectAsState()
    val roadLines by viewModel.selectedOfflineMapRoads.collectAsState()
    var name by rememberSaveable { mutableStateOf("My area") }
    val loc by viewModel.currentLocation.collectAsState()
    var latitude by rememberSaveable { mutableStateOf(loc?.latitude?.toString() ?: "25.2048") }
    var longitude by rememberSaveable { mutableStateOf(loc?.longitude?.toString() ?: "55.2708") }
    var radius by rememberSaveable { mutableStateOf("1.0") }
    var isPaused by rememberSaveable { mutableStateOf(false) }
    val validCoordinates = latitude.toDoubleOrNull()?.let { it.isFinite() && it in -85.0..85.0 } == true && longitude.toDoubleOrNull()?.let { it.isFinite() && it in -180.0..180.0 } == true
    val validRadius = radius.toDoubleOrNull()?.let { it.isFinite() && it in 0.2..2.0 } == true
    Box(Modifier.fillMaxSize().background(AtmosphericDarkBrush).safeDrawingPadding().imePadding().padding(16.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Column {
                    Text("Offline maps", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Download a small OpenStreetMap road area", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                if (selectedArea != null) item {
                    GlassSurface(cornerRadius = 18.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${selectedArea!!.name} · offline", color = Color.White, fontWeight = FontWeight.Bold)
                                TextButton(onClick = viewModel::closeOfflineMap) { Text("Close") }
                            }
                            Canvas(Modifier.fillMaxWidth().height(280.dp).background(Color(0xFF101A20))) {
                                val points = roadLines.flatten()
                                if (points.isNotEmpty()) {
                                    val minLat = points.minOf { it.latitude }; val maxLat = points.maxOf { it.latitude }
                                    val minLon = points.minOf { it.longitude }; val maxLon = points.maxOf { it.longitude }
                                    val latSpan = (maxLat - minLat).coerceAtLeast(0.00001)
                                    val lonSpan = (maxLon - minLon).coerceAtLeast(0.00001)
                                    roadLines.forEach { line ->
                                        for (i in 0 until line.lastIndex) {
                                            fun project(point: com.example.offlinewalkietalkie.data.OfflineMapPoint) = Offset(
                                                8f + (size.width - 16f) * ((point.longitude - minLon) / lonSpan).toFloat(),
                                                8f + (size.height - 16f) * ((maxLat - point.latitude) / latSpan).toFloat()
                                            )
                                            drawLine(Color(0xFF7CB7C5), project(line[i]), project(line[i + 1]), strokeWidth = 2f)
                                        }
                                    }
                                }
                            }
                            Text("Offline road geometry · © OpenStreetMap contributors", color = Color(0xFFCBD5E1), fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                item {
                    GlassSurface(cornerRadius = 18.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(name, { name = it }, label = { Text("Area name") }, singleLine = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(latitude, { latitude = it }, label = { Text("Latitude") }, modifier = Modifier.weight(1f), singleLine = true)
                                OutlinedTextField(longitude, { longitude = it }, label = { Text("Longitude") }, modifier = Modifier.weight(1f), singleLine = true)
                            }
                            OutlinedTextField(radius, { radius = it }, label = { Text("Radius (0.2–2 km)") }, singleLine = true)
                            Text("Downloads road geometry from OpenStreetMap. Attribution: © OpenStreetMap contributors. Requires internet; public service limits apply.", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                            if (progress != null) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text("Downloading · ${(progress!! / 1024).coerceAtLeast(1)} KB received", color = Color.White, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { viewModel.cancelOfflineMapDownload(); isPaused = true }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Pause, null); Spacer(Modifier.width(6.dp)); Text("Pause")
                                    }
                                    OutlinedButton(onClick = { viewModel.cancelOfflineMapDownload(); isPaused = false }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                                }
                            } else {
                                Button(enabled = validCoordinates && validRadius && name.isNotBlank(), onClick = {
                                    val lat = latitude.toDoubleOrNull(); val lon = longitude.toDoubleOrNull(); val km = radius.toDoubleOrNull()
                                    if (lat != null && lon != null && km != null) {
                                        viewModel.downloadOfflineMap(name, lat, lon, km)
                                        isPaused = false
                                    }
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Download, null)
                                    Spacer(Modifier.width(8.dp)); Text(if (isPaused) "Resume download" else "Download area")
                                }
                                if (isPaused) Text("Resume restarts the request because this public service does not support resumable downloads.", color = Color(0xFFFFCC80), fontSize = 11.sp)
                            }
                            if (!validCoordinates) Text("Enter latitude from -85 to 85 and longitude from -180 to 180.", color = MaterialTheme.colorScheme.error)
                            if (!validRadius) Text("Choose a radius from 0.2 to 2 km.", color = MaterialTheme.colorScheme.error)
                            if (error != null) Text(error!!, color = Color(0xFFFF8A80), fontSize = 12.sp)
                        }
                    }
                }
                item { Text("Saved areas (${areas.size})", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
                items(areas, key = OfflineMapArea::id) { area ->
                    GlassSurface(cornerRadius = 16.dp, isOutdoorMode = false, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(area.name, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("${area.roadCount} roads · ${area.radiusKm} km radius · ${(area.file.length() / 1024).coerceAtLeast(1)} KB", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                            }
                            TextButton(onClick = { viewModel.openOfflineMap(area) }) { Text("Open") }
                            IconButton(onClick = { viewModel.deleteOfflineMap(area) }) { Icon(Icons.Default.Delete, "Delete ${area.name}", tint = Color(0xFFFF8A80)) }
                        }
                    }
                }
                if (areas.isEmpty()) item { Text("No saved map areas yet.", color = Color(0xFF94A3B8), modifier = Modifier.padding(8.dp)) }
            }
        }
    }
}
