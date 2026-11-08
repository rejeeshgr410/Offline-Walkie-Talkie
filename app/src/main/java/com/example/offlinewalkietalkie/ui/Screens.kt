package com.example.offlinewalkietalkie.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import com.example.offlinewalkietalkie.R
import com.example.offlinewalkietalkie.channel.TextDeliveryStatus
import com.example.offlinewalkietalkie.protocol.FloorState
import com.example.offlinewalkietalkie.theme.*
import com.example.offlinewalkietalkie.transport.TransportType
import com.example.offlinewalkietalkie.ui.components.GlassSurface
import com.example.offlinewalkietalkie.ui.components.PttOrb
import com.example.offlinewalkietalkie.util.CapabilityManager
import com.example.offlinewalkietalkie.util.PermissionManager

@Composable
fun PermissionScreen(onPermissionsGranted: () -> Unit) {
    val context = LocalContext.current
    var hasAudio by remember { mutableStateOf(PermissionManager.isAudioPermissionGranted(context)) }
    var hasRadio by remember { mutableStateOf(PermissionManager.isRadioPermissionGranted(context)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasAudio = PermissionManager.isAudioPermissionGranted(context)
        hasRadio = PermissionManager.isRadioPermissionGranted(context)
    }

    // Only advance once the radio permissions are in too. Advancing on the microphone alone
    // drops the user into an app whose discovery silently fails with MISSING_PERMISSION.
    LaunchedEffect(hasAudio, hasRadio) {
        if (hasAudio && hasRadio) {
            onPermissionsGranted()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(BrightCyan, MintGreen))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = DeepInk,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = stringResource(R.string.app_descriptor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ElectricAqua.copy(alpha = 0.8f)
                )
            }

            // Cards for permissions
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PermissionItemCard(
                    title = stringResource(R.string.microphone_permission),
                    description = stringResource(R.string.microphone_desc),
                    icon = Icons.Default.Mic,
                    isGranted = hasAudio
                )
                PermissionItemCard(
                    title = stringResource(R.string.nearby_permission),
                    description = stringResource(R.string.nearby_desc),
                    icon = Icons.Default.Bluetooth,
                    isGranted = hasRadio
                )
                PermissionItemCard(
                    title = stringResource(R.string.notifications_permission),
                    description = stringResource(R.string.notifications_desc),
                    icon = Icons.Default.Notifications,
                    isGranted = true
                )
            }

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        launcher.launch(PermissionManager.getRequiredPermissions().toTypedArray())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricAqua,
                        contentColor = DeepInk
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.grant_permissions),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                // Continue button ensures app is NEVER stuck
                OutlinedButton(
                    onClick = onPermissionsGranted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text(text = stringResource(R.string.continue_to_app))
                }

                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.open_settings),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionItemCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean
) {
    GlassSurface(
        cornerRadius = 18.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) MintGreen.copy(alpha = 0.2f) else Color(0x22FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) MintGreen else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = description,
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = MintGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: WalkieTalkieViewModel,
    onNavigateToNearby: () -> Unit,
    onNavigateToPairing: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToCreateChannel: () -> Unit,
    onNavigateToJoinChannel: () -> Unit,
    onNavigateToRadar: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToDiscoverMap: () -> Unit = {}
) {
    val peers by viewModel.peers.collectAsState()
    val floorState by viewModel.floorState.collectAsState()
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    val isRelayModeActive by viewModel.isRelayModeActive.collectAsState()
    val currentChannelName by viewModel.currentChannelName.collectAsState()
    val speakerphoneEnabled by viewModel.speakerphoneEnabled.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isInSession by viewModel.isInSession.collectAsState()

    val securedPeerCount by viewModel.securedPeerCount.collectAsState()
    val transportType by viewModel.transportType.collectAsState()
    val connectedPeers = peers.filter { it.isConnected }

    var showSosModal by remember { mutableStateOf(false) }
    val incomingAlerts by viewModel.incomingAlerts.collectAsState()
    val isAlarmPlaying by viewModel.isAlarmPlaying.collectAsState()
    val isBroadcastingSos by viewModel.isBroadcastingSos.collectAsState()
    val peerTelemetry by viewModel.peerTelemetry.collectAsState()
    val localBatteryPercent by viewModel.localBatteryPercent.collectAsState()

    if (showSosModal) {
        SosBroadcastModal(
            onDismiss = { showSosModal = false },
            onStartSos = { msg -> viewModel.startSos(msg) }
        )
    }

    val transportTypeName = when (transportType) {
        com.example.offlinewalkietalkie.transport.TransportType.BLE_GATT -> "BLE Long-Range (Coded PHY)"
        com.example.offlinewalkietalkie.transport.TransportType.NEARBY_CONNECTIONS -> "Nearby Connections"
        com.example.offlinewalkietalkie.transport.TransportType.BLE_L2CAP -> "BLE L2CAP"
        com.example.offlinewalkietalkie.transport.TransportType.WIFI_AWARE -> "Wi-Fi Aware"
        com.example.offlinewalkietalkie.transport.TransportType.WIFI_DIRECT -> "Wi-Fi Direct"
        else -> "Bluetooth"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isOutdoorMode) OutdoorBackground else Color.Transparent)
            .then(if (!isOutdoorMode) Modifier.background(AtmosphericDarkBrush) else Modifier)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // 1. Top Bar & Lock-up
                TopHeaderBar(
                    connectedCount = connectedPeers.size,
                    isOutdoorMode = isOutdoorMode,
                    isBroadcastingSos = isBroadcastingSos,
                    onOpenSos = {
                        if (isBroadcastingSos) {
                            viewModel.stopSos()
                        } else {
                            showSosModal = true
                        }
                    },
                    onOpenRadar = onNavigateToRadar,
                    onOpenHistory = onNavigateToHistory
                )

                // SOS Emergency Alert Banner
                SosAlertBanner(
                    alerts = incomingAlerts,
                    isAlarmPlaying = isAlarmPlaying,
                    onSilenceAlarm = { viewModel.silenceAlarm() },
                    onDismissAlert = { id -> viewModel.dismissSosAlert(id) },
                    onNavigateToRadar = onNavigateToRadar
                )
            }

            // 2. Main Content (Active Session vs Idle)
            if (isInSession) {
                ActiveSessionContent(
                    channelName = currentChannelName,
                    connectedPeers = connectedPeers,
                    floorState = floorState,
                    isOutdoorMode = isOutdoorMode,
                    speakerphoneEnabled = speakerphoneEnabled,
                    isMuted = isMuted,
                    isRelayModeActive = isRelayModeActive,
                    securedPeerCount = securedPeerCount,
                    transportTypeName = transportTypeName,
                    peerTelemetry = peerTelemetry,
                    localBatteryPercent = localBatteryPercent,
                    onToggleSpeaker = { viewModel.toggleAudioRoute() },
                    onToggleMute = { viewModel.toggleMute() },
                    onToggleRelay = { viewModel.toggleRelayMode() },
                    onPttStart = { viewModel.requestPtt() },
                    onPttEnd = { viewModel.releasePtt() },
                    onReplayLast = { viewModel.replayLastMessage() },
                    onNavigateToRadar = onNavigateToRadar,
                    onNavigateToDiscoverMap = onNavigateToDiscoverMap,
                    onNavigateToNearby = onNavigateToNearby,
                    onNavigateToPairing = onNavigateToPairing,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToContacts = onNavigateToContacts,
                    onLeaveSession = { viewModel.leaveGroup() }
                )
            } else {
                IdleDashboardContent(
                    isOutdoorMode = isOutdoorMode,
                    onCreateChannel = { onNavigateToCreateChannel() },
                    onJoinChannel = { onNavigateToJoinChannel() },
                    onOpenMap = onNavigateToDiscoverMap,
                    onScanNearby = {
                        viewModel.startNearby()
                        onNavigateToNearby()
                    }
                )
            }

            // 3. 4-Tab Bottom Navigation Bar
            BottomNavigationBar(
                activeTab = 0,
                isOutdoorMode = isOutdoorMode,
                onSelectTab = { index ->
                    when (index) {
                        0 -> {} // Already on Home
                        1 -> onNavigateToMessages()
                        2 -> onNavigateToContacts()
                        3 -> onNavigateToSettings()
                    }
                }
            )
        }
    }
}

@Composable
private fun TopHeaderBar(
    connectedCount: Int,
    isOutdoorMode: Boolean,
    isBroadcastingSos: Boolean = false,
    onOpenSos: () -> Unit = {},
    onOpenRadar: () -> Unit = {},
    onOpenHistory: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Waveform Brand Mark & Titles
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(BrightCyan, MintGreen))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = DeepInk,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.app_descriptor),
                    fontSize = 10.sp,
                    color = ElectricAqua.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Action Buttons: Radar, History, SOS
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onOpenRadar,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Radar",
                    tint = BrightCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onOpenHistory,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "History",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Button(
                onClick = onOpenSos,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBroadcastingSos) Color(0xFFFFEB3B) else Color(0xFFD32F2F)
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = if (isBroadcastingSos) "SOS ON" else "SOS",
                    color = if (isBroadcastingSos) Color.Black else Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ActiveSessionContent(
    channelName: String,
    connectedPeers: List<com.example.offlinewalkietalkie.transport.Peer>,
    floorState: FloorState,
    isOutdoorMode: Boolean,
    speakerphoneEnabled: Boolean,
    isMuted: Boolean,
    isRelayModeActive: Boolean,
    securedPeerCount: Int,
    transportTypeName: String,
    peerTelemetry: Map<String, com.example.offlinewalkietalkie.protocol.PeerTelemetry> = emptyMap(),
    localBatteryPercent: Int = 100,
    onToggleSpeaker: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleRelay: () -> Unit,
    onPttStart: () -> Unit,
    onPttEnd: () -> Unit,
    onReplayLast: () -> Unit = {},
    onNavigateToRadar: () -> Unit = {},
    onNavigateToDiscoverMap: () -> Unit = {},
    onNavigateToNearby: () -> Unit,
    onNavigateToPairing: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onLeaveSession: () -> Unit
) {
    var showMuteConfirmation by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showMuteConfirmation) {
            AlertDialog(
                onDismissRequest = { showMuteConfirmation = false },
                title = { Text("Mute live audio?") },
                text = {
                    Text("You won't hear peers in real time while muted. Incoming transmissions will remain available in voice history for playback.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showMuteConfirmation = false
                        onToggleMute()
                    }) { Text("Mute") }
                },
                dismissButton = {
                    TextButton(onClick = { showMuteConfirmation = false }) { Text("Cancel") }
                }
            )
        }
        // Current Channel Glass Card
        GlassSurface(
            cornerRadius = 26.dp,
            isOutdoorMode = isOutdoorMode,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BrightCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = BrightCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = channelName,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(ElectricAqua)
                                )
                            }
                            Text(
                                text = "${connectedPeers.size + 1} members · ${stringResource(R.string.good_quality)}",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Horizontal Participant Avatar Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // You (Active Speaker)
                    val isYouSpeaking = floorState is FloorState.HeldByMe || floorState is FloorState.Talking || floorState is FloorState.Granted
                    AvatarBadge(
                        name = "You",
                        initials = "ME",
                        isSpeaking = isYouSpeaking,
                        isOnline = true,
                        isRelay = false,
                        batteryPercent = localBatteryPercent
                    )

                    // Connected peers
                    connectedPeers.forEach { peer ->
                        val isThisPeerSpeaking = floorState is FloorState.HeldByOther && floorState.peerId == peer.id
                        val peerBatt = peerTelemetry[peer.id]?.batteryPercent
                        AvatarBadge(
                            name = peer.name.take(6),
                            initials = peer.name.take(2).uppercase(),
                            isSpeaking = isThisPeerSpeaking,
                            isOnline = peer.isConnected,
                            isRelay = false,
                            batteryPercent = peerBatt
                        )
                    }
                }
            }
        }

        // Central Luminous PTT Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Match the dedicated receive mute control exposed by the reference intercom.
            Button(
                onClick = {
                    if (isMuted) onToggleMute() else showMuteConfirmation = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMuted) Color(0xFFFFB74D) else Color(0xFF1E293B),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(width = 58.dp, height = 42.dp)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (isMuted) "Unmute live audio" else "Mute live audio",
                    modifier = Modifier.size(21.dp)
                )
            }
            // Speaker Route Shortcut
            GlassSurface(
                cornerRadius = 16.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onToggleSpeaker() }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = if (speakerphoneEnabled) Icons.Default.VolumeUp else Icons.Default.Headset,
                        contentDescription = if (speakerphoneEnabled) stringResource(R.string.speaker) else stringResource(R.string.earpiece),
                        tint = if (speakerphoneEnabled) ElectricAqua else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 176dp Luminous PTT Orb
            PttOrb(
                floorState = floorState,
                isOutdoorMode = isOutdoorMode,
                onPressStart = onPttStart,
                onPressEnd = onPttEnd
            )

            // Roster / Contacts Shortcut
            GlassSurface(
                cornerRadius = 16.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onNavigateToContacts() }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = stringResource(R.string.roster),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Connection Glass Strip
        GlassSurface(
            cornerRadius = 18.dp,
            isOutdoorMode = isOutdoorMode,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 5 Quality Signal Bars
                    QualitySignalBars()
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${stringResource(R.string.good_quality)} · ${connectedPeers.size} ${if (connectedPeers.size == 1) "peer" else "peers"}",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Text(
                            text = transportTypeName,
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 2x2 Quick-Action Glass Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionCard(
                title = stringResource(R.string.nearby_devices),
                subtitle = stringResource(R.string.searching_devices),
                icon = Icons.Default.WifiTethering,
                iconTint = BrightCyan,
                isOutdoorMode = isOutdoorMode,
                onClick = onNavigateToNearby,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                title = stringResource(R.string.mesh_relay),
                subtitle = if (isRelayModeActive) stringResource(R.string.mesh_active) else "Inactive",
                icon = Icons.Default.Hub,
                iconTint = if (isRelayModeActive) MintGreen else Color.Gray,
                isOutdoorMode = isOutdoorMode,
                onClick = onToggleRelay,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionCard(
                title = stringResource(R.string.security),
                subtitle = if (securedPeerCount > 0) "Encrypted ($securedPeerCount)" else "Not Secured",
                icon = Icons.Default.Security,
                iconTint = if (securedPeerCount > 0) Color(0xFF818CF8) else Color.Gray,
                isOutdoorMode = isOutdoorMode,
                onClick = onNavigateToPairing,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                title = stringResource(R.string.settings),
                subtitle = if (speakerphoneEnabled) "Speaker On" else "Earpiece",
                icon = Icons.Default.Settings,
                iconTint = WarmAmber,
                isOutdoorMode = isOutdoorMode,
                onClick = onNavigateToSettings,
                modifier = Modifier.weight(1f)
            )
        }

        // Replay Last & Radar Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onReplayLast,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
            ) {
                Icon(
                    imageVector = Icons.Default.Replay,
                    contentDescription = null,
                    tint = BrightCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Replay Last", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }

            Button(
                onClick = onNavigateToRadar,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    tint = MintGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Radar View", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }

        OutlinedButton(onClick = onNavigateToDiscoverMap, modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Icon(Icons.Default.Map, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Discover map")
        }

        // Leave Session button
        OutlinedButton(
            onClick = onLeaveSession,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
        ) {
            Icon(
                imageVector = Icons.Default.ExitToApp,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Leave Session", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun IdleDashboardContent(
    isOutdoorMode: Boolean,
    onCreateChannel: () -> Unit,
    onJoinChannel: () -> Unit,
    onOpenMap: () -> Unit,
    onScanNearby: () -> Unit
) {
    GlassSurface(
        cornerRadius = 28.dp,
        isOutdoorMode = isOutdoorMode,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(BrightCyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WifiFind,
                    contentDescription = null,
                    tint = BrightCyan,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.no_active_session),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.no_active_session_desc),
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            // One way in. Creating versus joining is decided on the next screen, where
            // the nearby groups are actually visible.
            Button(
                onClick = onCreateChannel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricAqua,
                    contentColor = DeepInk
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.connect_devices),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onScanNearby,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrightCyan)
            ) {
                Text(text = stringResource(R.string.scan_nearby))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenMap, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Icon(Icons.Default.Map, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Discover map")
            }
        }
    }
}

@Composable
private fun AvatarBadge(
    name: String,
    initials: String,
    isSpeaking: Boolean,
    isOnline: Boolean,
    isRelay: Boolean,
    batteryPercent: Int? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(2.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Pulse ring if speaking
            if (isSpeaking) {
                val infiniteTransition = rememberInfiniteTransition(label = "avatar_pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.25f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .border(2.dp, ElectricAqua, CircleShape)
                )
            }

            // Avatar bubble
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isRelay) Color(0xFF3B2F1E) else Color(0xFF1E293B))
                    .border(
                        1.5.dp,
                        if (isRelay) WarmAmber else if (isSpeaking) ElectricAqua else Color(0x33FFFFFF),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isRelay) WarmAmber else Color.White
                )
            }

            // Status badge indicator
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(if (isRelay) WarmAmber else if (isOnline) MintGreen else Color.Gray)
                    .border(1.5.dp, DeepInk, CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            fontSize = 10.sp,
            color = if (isSpeaking) ElectricAqua else Color(0xFFCBD5E1),
            fontWeight = if (isSpeaking) FontWeight.Bold else FontWeight.Normal
        )
        if (batteryPercent != null && batteryPercent in 0..100) {
            val battColor = when {
                batteryPercent > 50 -> MintGreen
                batteryPercent > 20 -> Color.Yellow
                else -> Color(0xFFEF5350)
            }
            Text(
                text = "🔋$batteryPercent%",
                fontSize = 9.sp,
                color = battColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun QualitySignalBars() {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.height(16.dp)
    ) {
        Box(modifier = Modifier.width(3.dp).height(6.dp).clip(RoundedCornerShape(1.dp)).background(ElectricAqua))
        Box(modifier = Modifier.width(3.dp).height(9.dp).clip(RoundedCornerShape(1.dp)).background(ElectricAqua))
        Box(modifier = Modifier.width(3.dp).height(12.dp).clip(RoundedCornerShape(1.dp)).background(ElectricAqua))
        Box(modifier = Modifier.width(3.dp).height(15.dp).clip(RoundedCornerShape(1.dp)).background(ElectricAqua))
        Box(modifier = Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = 0.2f)))
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    isOutdoorMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        cornerRadius = 18.dp,
        isOutdoorMode = isOutdoorMode,
        modifier = modifier
            .height(68.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun BottomNavigationBar(
    activeTab: Int,
    isOutdoorMode: Boolean,
    onSelectTab: (Int) -> Unit
) {
    GlassSurface(
        cornerRadius = 24.dp,
        isOutdoorMode = isOutdoorMode,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs: List<Pair<String, ImageVector>> = listOf(
                Pair(stringResource(R.string.home), Icons.Default.Home),
                Pair(stringResource(R.string.messages), Icons.Default.ChatBubbleOutline),
                Pair(stringResource(R.string.contacts), Icons.Default.Contacts),
                Pair(stringResource(R.string.more), Icons.Default.MoreHoriz)
            )

            tabs.forEachIndexed { index, pair ->
                val isSelected = activeTab == index
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectTab(index) }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = pair.second,
                        contentDescription = pair.first,
                        tint = if (isSelected) ElectricAqua else Color(0xFF94A3B8),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = pair.first,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) ElectricAqua else Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@Composable
fun NearbyDevicesScreen(
    viewModel: WalkieTalkieViewModel,
    onNavigateToPairing: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val peers by viewModel.peers.collectAsState(initial = emptyList())
    val pendingRequest by viewModel.pendingRequest.collectAsState()
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    val isAdvertising by viewModel.isAdvertising.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val transportError by viewModel.transportError.collectAsState()
    val transportType by viewModel.transportType.collectAsState()
    val peerTelemetry by viewModel.peerTelemetry.collectAsState()

    val wifiManager = remember {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
    }
    var isWifiEnabled by remember { mutableStateOf(wifiManager.isWifiEnabled) }
    var showFallbackBanner by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.startNearby()
        while (true) {
            isWifiEnabled = wifiManager.isWifiEnabled
            kotlinx.coroutines.delay(2000)
        }
    }

    LaunchedEffect(isWifiEnabled, peers) {
        if (!isWifiEnabled && peers.isEmpty()) {
            kotlinx.coroutines.delay(15_000)
            if (!isWifiEnabled && peers.isEmpty()) {
                showFallbackBanner = true
            }
        } else {
            showFallbackBanner = false
        }
    }

    LaunchedEffect(pendingRequest) {
        if (pendingRequest != null) {
            onNavigateToPairing()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.nearby_devices),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isWifiEnabled) {
                GlassSurface(
                    cornerRadius = 12.dp,
                    isOutdoorMode = isOutdoorMode,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = Color(0xFFFFB45C),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Wi-Fi radio is off",
                                color = Color(0xFFFFB45C),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Turn on Wi-Fi for faster discovery. No internet needed.",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        TextButton(onClick = {
                            context.startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                        }) {
                            Text("Open", color = ElectricAqua, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (showFallbackBanner && transportType == TransportType.NEARBY_CONNECTIONS) {
                GlassSurface(
                    cornerRadius = 12.dp,
                    isOutdoorMode = isOutdoorMode,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.BluetoothSearching,
                            contentDescription = null,
                            tint = ElectricAqua,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "No devices found",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Try Bluetooth-only mode?",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        TextButton(onClick = {
                            viewModel.switchToBleTransport()
                            showFallbackBanner = false
                        }) {
                            Text("Switch", color = ElectricAqua, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            GlassSurface(
                cornerRadius = 16.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Visible as \"${viewModel.localDeviceName}\"",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append(if (isAdvertising) "Discoverable" else "Not discoverable")
                            append(" · ")
                            append(if (isDiscovering) "Scanning" else "Not scanning")
                            if (transportType != TransportType.NEARBY_CONNECTIONS) {
                                append(" · ")
                                append(transportType.name.replace("_", " "))
                            }
                        },
                        color = if (isAdvertising && isDiscovering) MintGreen else Color(0xFFFFB45C),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Open this screen on both phones.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                    if (transportError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = transportError!!,
                            color = Color(0xFFFF5E77),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (peers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ElectricAqua)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.searching_devices),
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(peers) { peer ->
                        GlassSurface(
                            cornerRadius = 16.dp,
                            isOutdoorMode = isOutdoorMode,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(ElectricAqua.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = peer.name.take(2).uppercase(),
                                            color = ElectricAqua,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = peer.name,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                            val batt = peerTelemetry[peer.id]?.batteryPercent
                                            if (batt != null && batt in 0..100) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                val battColor = when {
                                                    batt > 50 -> MintGreen
                                                    batt > 20 -> Color.Yellow
                                                    else -> Color(0xFFEF5350)
                                                }
                                                Text(
                                                    text = "🔋$batt%",
                                                    fontSize = 10.sp,
                                                    color = battColor,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Text(
                                            text = "ID: ${peer.id.take(8)}",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                if (peer.isConnected) {
                                    Text(
                                        text = stringResource(R.string.connected),
                                        color = MintGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Button(
                                        onClick = { viewModel.connectToPeer(peer.id) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElectricAqua,
                                            contentColor = DeepInk
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.connect),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PairingScreen(
    viewModel: WalkieTalkieViewModel,
    onPaired: () -> Unit,
    onBack: () -> Unit
) {
    // State, not an event stream: this screen is mounted in response to the request, so a
    // replay-less flow would hand it nothing and leave it waiting forever.
    val request by viewModel.pendingRequest.collectAsState()
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    val isInSession by viewModel.isInSession.collectAsState()
    var accepted by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    // Accepting is only half a handshake; the link is live when the peer accepts too.
    // Guarded so a recomposition cannot pop a second entry off the back stack.
    LaunchedEffect(isInSession) {
        if (isInSession && !dismissed) {
            dismissed = true
            onPaired()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.verify_identity),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            GlassSurface(
                cornerRadius = 24.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (request == null) {
                        CircularProgressIndicator(color = ElectricAqua)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (accepted) {
                                "Confirmed. Waiting for the other phone to confirm the same code…"
                            } else {
                                stringResource(R.string.waiting_request)
                            },
                            color = Color(0xFFCBD5E1),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.pairing_with, request!!.peerName),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = request!!.authCode,
                            color = ElectricAqua,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 36.sp,
                            letterSpacing = 4.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Compare the 6 digits with your partner's screen. If they match, tap Confirm.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    // Navigation happens when the link actually comes up,
                                    // not on the local tap alone.
                                    accepted = true
                                    viewModel.acceptConnection(request!!.peerId)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricAqua,
                                    contentColor = DeepInk
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.confirm_match),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.rejectConnection(request!!.peerId)
                                    onBack()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseActive)
                            ) {
                                Text(text = stringResource(R.string.reject))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: WalkieTalkieViewModel,
    onOpenOfflineMaps: () -> Unit,
    onBack: () -> Unit
) {
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    val speakerphoneEnabled by viewModel.speakerphoneEnabled.collectAsState()
    val disturbanceMode by viewModel.disturbanceMode.collectAsState()
    val allowIncomingRequests by viewModel.allowIncomingRequests.collectAsState()
    var loopbackEnabled by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassSurface(cornerRadius = 20.dp, isOutdoorMode = isOutdoorMode, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Intercom controls", color = Color.White, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Allow incoming requests", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(if (allowIncomingRequests) "New devices wait for your approval" else "Incoming requests are rejected", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                        }
                        Switch(checked = allowIncomingRequests, onCheckedChange = viewModel::setAllowIncomingRequests)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Disturbance mode", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Keep SOS alerts visible but silence their alarm", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                        }
                        Switch(checked = disturbanceMode, onCheckedChange = viewModel::setDisturbanceMode)
                    }
                    OutlinedButton(onClick = { viewModel.markTextMessagesRead() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Mark received messages read")
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = { onOpenOfflineMaps() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Map, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Offline maps")
            }
            Spacer(modifier = Modifier.height(16.dp))

            GlassSurface(
                cornerRadius = 20.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Outdoor mode toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.outdoor_mode),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "High-contrast opaque surfaces for sunlight",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isOutdoorMode,
                            onCheckedChange = { viewModel.toggleOutdoorMode() }
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0x22FFFFFF))

                    // Audio Loopback Test
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.test_audio_loopback),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Hear your own microphone locally",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = loopbackEnabled,
                            onCheckedChange = {
                                loopbackEnabled = it
                                viewModel.setLoopbackMode(it)
                            }
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0x22FFFFFF))

                    // Speakerphone vs Earpiece
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Audio Routing",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (speakerphoneEnabled) "Speakerphone active" else "Earpiece active",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = speakerphoneEnabled,
                            onCheckedChange = { viewModel.toggleAudioRoute() }
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0x22FFFFFF))

                    // Floating PTT Bubble (SYSTEM_ALERT_WINDOW)
                    val isFloatingActive by viewModel.isFloatingPttActive.collectAsState()
                    val context = LocalContext.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Floating PTT Orb",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Movable talk button over Maps & camera",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isFloatingActive,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    val success = viewModel.startFloatingPtt()
                                    if (!success) {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    }
                                } else {
                                    viewModel.stopFloatingPtt()
                                }
                            }
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0x22FFFFFF))

                    // Pocket Mode: Hardware Volume Key PTT
                    val isVolumePtt by viewModel.isVolumeKeyPttEnabled.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pocket Volume Key PTT",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Hold Volume Down while TalkLoop is on screen",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isVolumePtt,
                            onCheckedChange = { viewModel.setVolumeKeyPttEnabled(it) }
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0x22FFFFFF))

                    // Bluetooth SCO Headset
                    val isScoActive by viewModel.isBluetoothScoActive.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bluetooth SCO Headset",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Route microphone and voice to Bluetooth headset",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isScoActive,
                            onCheckedChange = { viewModel.toggleBluetoothSco() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactsScreen(
    viewModel: WalkieTalkieViewModel,
    onBack: () -> Unit
) {
    val peers by viewModel.peers.collectAsState()
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    val peerTelemetry by viewModel.peerTelemetry.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.contacts),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassSurface(
                cornerRadius = 20.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Trusted Off-Grid Contacts",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Verified peers with stored cryptographic keys. Connections reconnect automatically.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (peers.isEmpty()) {
                        Text(
                            text = "No saved contacts yet. Pair with nearby devices to verify keys.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    } else {
                        peers.forEach { peer ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = peer.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val batt = peerTelemetry[peer.id]?.batteryPercent
                                    if (batt != null && batt in 0..100) {
                                        val battColor = when {
                                            batt > 50 -> MintGreen
                                            batt > 20 -> Color.Yellow
                                            else -> Color(0xFFEF5350)
                                        }
                                        Text(
                                            text = "🔋$batt%",
                                            fontSize = 11.sp,
                                            color = battColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Verified",
                                        tint = MintGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Verified", color = MintGreen, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessagesScreen(
    viewModel: WalkieTalkieViewModel,
    onBack: () -> Unit
) {
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    val messages by viewModel.textMessages.collectAsState()
    val isInSession by viewModel.isInSession.collectAsState()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.messages),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!isInSession) {
                GlassSurface(
                    cornerRadius = 16.dp,
                    isOutdoorMode = isOutdoorMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "No active session. Connect to a peer to exchange encrypted off-grid messages.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            } else {
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = BrightCyan,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No messages yet.",
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Send a message to your connected peer.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages, key = { it.messageId }) { msg ->
                            val isMine = msg.senderMemberId == viewModel.sessionIdForMessages
                            var actionsExpanded by remember(msg.messageId) { mutableStateOf(false) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                            ) {
                                GlassSurface(
                                    cornerRadius = 16.dp,
                                    isOutdoorMode = isOutdoorMode,
                                    modifier = Modifier.fillMaxWidth(0.75f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = if (isMine) "You" else msg.senderName,
                                            color = if (isMine) BrightCyan else ElectricAqua,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = msg.content,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = when (msg.status) {
                                                TextDeliveryStatus.SENDING -> "Sending…"
                                                TextDeliveryStatus.DELIVERED -> "Delivered"
                                                TextDeliveryStatus.FAILED -> "Failed"
                                            } + if (!isMine && !msg.isRead) " · Unread" else "",
                                            color = if (msg.status == TextDeliveryStatus.FAILED)
                                                RoseActive else Color(0xFF94A3B8),
                                            fontSize = 10.sp
                                        )
                                        Box(Modifier.align(Alignment.End)) {
                                            TextButton(onClick = { actionsExpanded = true }) { Text("Actions", fontSize = 10.sp) }
                                            DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                                                if (!isMine && !msg.isRead) DropdownMenuItem(
                                                    text = { Text("Mark read") },
                                                    onClick = { actionsExpanded = false; viewModel.markTextMessageRead(msg.messageId) }
                                                )
                                                if (isMine && msg.status == TextDeliveryStatus.FAILED) DropdownMenuItem(
                                                    text = { Text("Resend") },
                                                    onClick = { actionsExpanded = false; viewModel.resendTextMessage(msg.messageId) }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Delete") },
                                                    onClick = { actionsExpanded = false; viewModel.deleteTextMessage(msg.messageId) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricAqua,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF16233B),
                            unfocusedContainerColor = Color(0xFF16233B),
                            cursorColor = BrightCyan
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (draft.isNotBlank()) {
                                viewModel.sendTextMessage(draft.trim())
                                draft = ""
                            }
                        },
                        enabled = draft.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricAqua,
                            contentColor = DeepInk
                        ),
                        shape = CircleShape,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = DeepInk)
                    }
                }
            }
        }
    }
}

@Composable
fun CreateChannelScreen(
    viewModel: WalkieTalkieViewModel,
    onNavigateToNearby: () -> Unit,
    onBack: () -> Unit
) {
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    var channelName by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = "Create Channel",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            GlassSurface(
                cornerRadius = 16.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Channel Name",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = channelName,
                        onValueChange = { channelName = it },
                        placeholder = { Text("e.g. Field Ops Alpha") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = ElectricAqua,
                            unfocusedBorderColor = Color(0xFF475569),
                            cursorColor = ElectricAqua,
                            focusedPlaceholderColor = Color(0xFF64748B),
                            unfocusedPlaceholderColor = Color(0xFF64748B)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You'll host this channel. Other devices can join by scanning nearby or entering a code.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val name = channelName.ifBlank { "Channel ${System.currentTimeMillis() % 1000}" }
                    viewModel.createChannel(name)
                    viewModel.startNearby()
                    onNavigateToNearby()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricAqua,
                    contentColor = DeepInk
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create & Start Advertising", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun JoinChannelScreen(
    viewModel: WalkieTalkieViewModel,
    onNavigateToNearby: () -> Unit,
    onBack: () -> Unit
) {
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    var shortCode by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = "Join Channel",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            GlassSurface(
                cornerRadius = 16.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Enter Short Code",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = shortCode,
                        onValueChange = { shortCode = it.uppercase().take(6) },
                        placeholder = { Text("6-character code") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = ElectricAqua,
                            unfocusedBorderColor = Color(0xFF475569),
                            cursorColor = ElectricAqua,
                            focusedPlaceholderColor = Color(0xFF64748B),
                            unfocusedPlaceholderColor = Color(0xFF64748B)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ask the host for the 6-character channel code, or scan nearby to find open channels.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.setChannelName("Channel $shortCode")
                    viewModel.startNearby()
                    onNavigateToNearby()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricAqua,
                    contentColor = DeepInk
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Join with Code", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    viewModel.startNearby()
                    onNavigateToNearby()
                },
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricAqua),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = ElectricAqua, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Nearby Instead", color = ElectricAqua, fontWeight = FontWeight.Bold)
            }
        }
    }
}
