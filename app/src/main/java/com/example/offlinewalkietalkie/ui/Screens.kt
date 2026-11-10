package com.example.offlinewalkietalkie.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.offlinewalkietalkie.R
import com.example.offlinewalkietalkie.channel.TextDeliveryStatus
import com.example.offlinewalkietalkie.protocol.FloorState
import com.example.offlinewalkietalkie.theme.*
import com.example.offlinewalkietalkie.transport.TransportType
import com.example.offlinewalkietalkie.voice.AudioRoute
import com.example.offlinewalkietalkie.voice.RogerBeepStyle
import com.example.offlinewalkietalkie.ui.components.GlassSurface
import com.example.offlinewalkietalkie.ui.components.PttOrb
import com.example.offlinewalkietalkie.util.PermissionManager

// ============================================================================
// 1. Permission Onboarding Screen (Apple Onboarding Style)
// ============================================================================

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

    LaunchedEffect(hasAudio, hasRadio) {
        if (hasAudio && hasRadio) {
            onPermissionsGranted()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleBackground)
            .safeDrawingPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Large Title & Icon Lockup
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 28.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(AppleSystemBlue, AppleSystemTeal)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = AppleLargeTitle,
                    color = AppleTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Offline Mesh Intercom • Zero Internet Required",
                    style = AppleCallout,
                    color = AppleTextSecondary
                )
            }

            // Cards for permissions
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PermissionItemCard(
                    title = stringResource(R.string.microphone_permission),
                    description = "Transmits low-latency real-time voice over Bluetooth mesh.",
                    icon = Icons.Default.Mic,
                    iconTint = AppleSystemBlue,
                    isGranted = hasAudio
                )
                PermissionItemCard(
                    title = stringResource(R.string.nearby_permission),
                    description = "Discovers nearby peer devices with zero Wi-Fi or cellular setup.",
                    icon = Icons.Default.Bluetooth,
                    iconTint = AppleSystemGreen,
                    isGranted = hasRadio
                )
                PermissionItemCard(
                    title = stringResource(R.string.notifications_permission),
                    description = "Alerts you to incoming PTT transmissions and Emergency SOS.",
                    icon = Icons.Default.Notifications,
                    iconTint = AppleSystemOrange,
                    isGranted = true
                )
            }

            // Action Buttons (Apple Verb-First)
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
                        containerColor = AppleSystemBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Continue",
                        style = AppleHeadline,
                        color = Color.White
                    )
                }

                OutlinedButton(
                    onClick = onPermissionsGranted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppleTextSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppleGlassBorder)
                ) {
                    Text(text = "Skip to App", style = AppleBody, fontSize = 14.sp)
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
                        text = "Open App Settings",
                        style = AppleFootnote,
                        color = AppleTextTertiary
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
    iconTint: Color,
    isGranted: Boolean
) {
    GlassSurface(
        cornerRadius = 18.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AppleHeadline,
                    fontSize = 15.sp
                )
                Text(
                    text = description,
                    style = AppleFootnote,
                    color = AppleTextSecondary
                )
            }
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = AppleSystemGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ============================================================================
// 2. Home Dashboard (Apple Control Center & Dynamic Audio Dial)
// ============================================================================

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
    val connectedPeers = peers.filter { it.isConnected && it.isAuthenticated }

    var showSosModal by remember { mutableStateOf(false) }
    val incomingAlerts by viewModel.incomingAlerts.collectAsState()
    val isAlarmPlaying by viewModel.isAlarmPlaying.collectAsState()
    val isBroadcastingSos by viewModel.isBroadcastingSos.collectAsState()
    val peerTelemetry by viewModel.peerTelemetry.collectAsState()
    val localBatteryPercent by viewModel.localBatteryPercent.collectAsState()
    val canInstantReplay by viewModel.canInstantReplay.collectAsState()
    val isInstantReplayPlaying by viewModel.isInstantReplayPlaying.collectAsState()
    val instantReplayDurationMs by viewModel.instantReplayDurationMs.collectAsState()

    if (showSosModal) {
        SosBroadcastModal(
            onDismiss = { showSosModal = false },
            onStartSos = { msg -> viewModel.startSos(msg) }
        )
    }

    val isSurvivalMode by viewModel.isSurvivalCodecMode.collectAsState()
    val transportTypeName = if (isSurvivalMode) {
        "Codec 2 • 1.6 kbps Survival Mode"
    } else {
        "AMR-NB • 7.4 kbps High Clarity"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isOutdoorMode) OutdoorBackground else AppleBackground)
            .then(if (!isOutdoorMode) Modifier.background(AtmosphericDarkBrush) else Modifier)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Column {
                // 1. Apple Navigation Bar Header
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

                // SOS Emergency Alert Banner (Incoming distress from other peers)
                SosAlertBanner(
                    alerts = incomingAlerts,
                    isAlarmPlaying = isAlarmPlaying,
                    onSilenceAlarm = { viewModel.silenceAlarm() },
                    onDismissAlert = { id -> viewModel.dismissSosAlert(id) },
                    onNavigateToRadar = onNavigateToRadar
                )

                // SOS Active Distress Broadcast Status Banner (When I am broadcasting)
                val mySosBeacon by viewModel.mySosBeacon.collectAsState()
                val acknowledgedResponders by viewModel.acknowledgedResponders.collectAsState()
                if (isBroadcastingSos) {
                    SosBroadcastingStatusBanner(
                        beacon = mySosBeacon,
                        responders = acknowledgedResponders,
                        onStopSos = { viewModel.stopSos() }
                    )
                }

                // Pending Pairing Request Banner (Apple Security Modal in Dashboard)
                val pendingRequest by viewModel.pendingRequest.collectAsState()
                if (pendingRequest != null) {
                    val req = pendingRequest!!
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AppleCardSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(1.dp, AppleSystemBlue.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Incoming Connection Request",
                                        style = AppleCaption1,
                                        color = AppleSystemBlue
                                    )
                                    Text(
                                        text = req.peerName,
                                        style = AppleHeadline,
                                        fontSize = 15.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AppleCardSurfaceSecondary)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = req.authCode,
                                        style = AppleHeadline.copy(color = AppleSystemBlue, letterSpacing = 2.sp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.acceptConnection(req.peerId) },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AppleSystemBlue, contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Confirm Match", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.rejectConnection(req.peerId) },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Reject", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Middle Body: Scrollable Cockpit Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                ActiveSessionContent(
                    channelName = if (connectedPeers.isNotEmpty()) currentChannelName else "Direct Walkie-Talkie",
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
                    canInstantReplay = canInstantReplay,
                    isInstantReplayPlaying = isInstantReplayPlaying,
                    instantReplayDurationMs = instantReplayDurationMs,
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
            }

            // 3. Floating iOS Tab Bar (Always Pinned)
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
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Large Title & Subtitle Status Pill
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "TalkLoop",
                    style = AppleLargeTitle,
                    fontSize = 26.sp,
                    letterSpacing = (-0.4).sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Live status dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (connectedCount > 0) AppleSystemGreen else AppleSystemBlue)
                )
            }
            Text(
                text = if (connectedCount > 0) "$connectedCount Connected • Ready" else "Auto-Discovery Active • Offline",
                style = AppleFootnote,
                color = AppleTextSecondary
            )
        }

        // Action Buttons: Radar, History, SOS
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Radar Icon Button
            IconButton(
                onClick = onOpenRadar,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(AppleCardSurfaceSecondary)
                    .border(0.75.dp, AppleGlassBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Directional Radar",
                    tint = AppleSystemBlue,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Voice History Button
            IconButton(
                onClick = onOpenHistory,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(AppleCardSurfaceSecondary)
                    .border(0.75.dp, AppleGlassBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Voice History",
                    tint = AppleTextPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Apple Emergency SOS Capsule
            Button(
                onClick = onOpenSos,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBroadcastingSos) AppleSystemYellow else AppleSystemRed,
                    contentColor = if (isBroadcastingSos) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(17.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(34.dp)
                    .border(
                        0.75.dp,
                        if (isBroadcastingSos) AppleSystemYellow else Color.White.copy(alpha = 0.25f),
                        RoundedCornerShape(17.dp)
                    )
            ) {
                Text(
                    text = if (isBroadcastingSos) "SOS ON" else "SOS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
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
    canInstantReplay: Boolean = false,
    isInstantReplayPlaying: Boolean = false,
    instantReplayDurationMs: Long = 0L,
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
                containerColor = AppleCardSurface,
                shape = RoundedCornerShape(20.dp),
                title = { Text("Mute Live Audio?", style = AppleTitle3) },
                text = {
                    Text(
                        "You won't hear peers in real time while muted. Incoming voice transmissions are still recorded in Voice History.",
                        style = AppleFootnote,
                        color = AppleTextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showMuteConfirmation = false
                            onToggleMute()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppleSystemOrange),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Mute Audio", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showMuteConfirmation = false }) {
                        Text("Cancel", color = AppleTextSecondary)
                    }
                }
            )
        }

        // Live Audio Muted Banner
        if (isMuted) {
            GlassSurface(
                cornerRadius = 16.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = null,
                            tint = AppleSystemOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Live Audio Muted",
                                style = AppleHeadline,
                                color = AppleSystemOrange,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Transmissions saved to Voice History",
                                style = AppleCaption1,
                                color = AppleTextSecondary
                            )
                        }
                    }
                    Button(
                        onClick = onToggleMute,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppleCardSurfaceSecondary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Unmute", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // 1. Channel Inset Card (Apple Health/Fitness Style)
        GlassSurface(
            cornerRadius = 22.dp,
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
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppleSystemBlue.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = AppleSystemBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (connectedPeers.isNotEmpty()) channelName else "Direct Walkie-Talkie",
                                    style = AppleHeadline
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (connectedPeers.isNotEmpty()) AppleSystemGreen else AppleSystemOrange)
                                )
                            }
                            Text(
                                text = if (connectedPeers.isNotEmpty())
                                    "${connectedPeers.size + 1} devices connected • BLE Mesh"
                                else
                                    "Ready to talk • Auto-Discovery active",
                                style = AppleFootnote,
                                color = AppleTextSecondary
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = AppleTextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Horizontal Participant Avatars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isYouSpeaking = floorState is FloorState.HeldByMe || floorState is FloorState.Talking || floorState is FloorState.Granted
                    AvatarBadge(
                        name = "You",
                        initials = "ME",
                        isSpeaking = isYouSpeaking,
                        isOnline = true,
                        isRelay = false,
                        batteryPercent = localBatteryPercent
                    )

                    connectedPeers.forEach { peer ->
                        val isThisPeerSpeaking = floorState is FloorState.HeldByOther && floorState.peerId == peer.id
                        val peerBatt = peerTelemetry[peer.id]?.batteryPercent
                        AvatarBadge(
                            name = peer.name.take(6),
                            initials = peer.name.take(2).uppercase(),
                            isSpeaking = isThisPeerSpeaking,
                            isOnline = peer.isConnected && peer.isAuthenticated,
                            isRelay = false,
                            batteryPercent = peerBatt
                        )
                    }
                }
            }
        }

        // 2. Central Apple Studio Acoustic Dial with Symmetrical Pods
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Quick Pods: Audio Routing & Live Mute
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GlassSurface(
                    cornerRadius = 14.dp,
                    isOutdoorMode = isOutdoorMode,
                    interactive = true,
                    onClick = onToggleSpeaker,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().clickable(onClick = onReplayLast)) {
                        Icon(
                            imageVector = if (speakerphoneEnabled) Icons.Default.VolumeUp else Icons.Default.Headset,
                            contentDescription = if (speakerphoneEnabled) "Speakerphone" else "Earpiece",
                            tint = if (speakerphoneEnabled) AppleSystemBlue else AppleTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                GlassSurface(
                    cornerRadius = 14.dp,
                    isOutdoorMode = isOutdoorMode,
                    interactive = true,
                    onClick = {
                        if (isMuted) onToggleMute() else showMuteConfirmation = true
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().clickable(onClick = onReplayLast)) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isMuted) "Unmute Audio" else "Mute Audio",
                            tint = if (isMuted) AppleSystemOrange else AppleTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Central Tactile Acoustic PTT Dial
            PttOrb(
                floorState = floorState,
                isOutdoorMode = isOutdoorMode,
                onPressStart = onPttStart,
                onPressEnd = onPttEnd
            )

            // Right Quick Pods: Contacts Roster & 1-Tap Replay Last
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GlassSurface(
                    cornerRadius = 14.dp,
                    isOutdoorMode = isOutdoorMode,
                    interactive = true,
                    onClick = onNavigateToContacts,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().clickable(onClick = onReplayLast)) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = "Contacts Roster",
                            tint = AppleTextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                GlassSurface(
                    cornerRadius = 14.dp,
                    isOutdoorMode = isOutdoorMode,
                    interactive = true,
                    onClick = onReplayLast,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().clickable(onClick = onReplayLast)) {
                        Icon(
                            imageVector = if (isInstantReplayPlaying) Icons.Default.Stop else Icons.Default.Replay,
                            contentDescription = if (isInstantReplayPlaying) "Stop Replay" else "10s Instant Replay",
                            tint = if (isInstantReplayPlaying) AppleSystemPink else if (canInstantReplay) AppleSystemTeal else AppleTextTertiary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }



        // 2b. 10-Second Instant Audio Replay Tactical Banner
        if (canInstantReplay) {
            GlassSurface(
                cornerRadius = 16.dp,
                isOutdoorMode = isOutdoorMode,
                interactive = true,
                onClick = onReplayLast,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (isInstantReplayPlaying) AppleSystemPink.copy(alpha = 0.2f) else AppleSystemTeal.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isInstantReplayPlaying) Icons.Default.Stop else Icons.Default.Replay,
                                contentDescription = null,
                                tint = if (isInstantReplayPlaying) AppleSystemPink else AppleSystemTeal,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isInstantReplayPlaying) "Playing Instant Replay..." else "10s Instant Replay Ready",
                                style = AppleHeadline,
                                fontSize = 13.sp,
                                color = if (isInstantReplayPlaying) AppleSystemPink else AppleSystemTeal
                            )
                            Text(
                                text = if (isInstantReplayPlaying) "Tap to cancel audio playback" else "Tap to review last ${minOf(10, (instantReplayDurationMs / 1000).coerceAtLeast(1))}s transmission",
                                style = AppleCaption1,
                                color = AppleTextSecondary
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isInstantReplayPlaying) AppleSystemPink else AppleSystemTeal)
                            .clickable(onClick = onReplayLast)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (isInstantReplayPlaying) "STOP" else "REPLAY",
                            style = AppleCaption1.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                    }
                }
            }
        }

        // 3. Telemetry & Codec Strip (Apple Control Center Pill)
        GlassSurface(
            cornerRadius = 16.dp,
            isOutdoorMode = isOutdoorMode,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QualitySignalBars()
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (connectedPeers.isNotEmpty())
                                "High Quality • ${connectedPeers.size} ${if (connectedPeers.size == 1) "Peer" else "Peers"}"
                            else
                                "Ready to Talk • Auto-Discovery",
                            style = AppleHeadline,
                            fontSize = 12.sp
                        )
                        Text(
                            text = transportTypeName,
                            style = AppleCaption1,
                            color = AppleTextSecondary
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = AppleTextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 4. Quick Action 2x2 Grid (Apple Inset Cards)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionCard(
                title = "Nearby Devices",
                subtitle = "Scan & Pair",
                icon = Icons.Default.WifiTethering,
                iconTint = AppleSystemBlue,
                isOutdoorMode = isOutdoorMode,
                onClick = onNavigateToNearby,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                title = "Mesh Relay",
                subtitle = if (isRelayModeActive) "Active" else "Standby",
                icon = Icons.Default.Hub,
                iconTint = if (isRelayModeActive) AppleSystemGreen else AppleTextTertiary,
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
                title = "Security",
                subtitle = if (securedPeerCount > 0) "Encrypted ($securedPeerCount)" else "Direct Link",
                icon = Icons.Default.Security,
                iconTint = if (securedPeerCount > 0) AppleSystemIndigo else AppleTextTertiary,
                isOutdoorMode = isOutdoorMode,
                onClick = onNavigateToPairing,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                title = "Settings",
                subtitle = if (speakerphoneEnabled) "Speaker On" else "Earpiece",
                icon = Icons.Default.Settings,
                iconTint = AppleSystemOrange,
                isOutdoorMode = isOutdoorMode,
                onClick = onNavigateToSettings,
                modifier = Modifier.weight(1f)
            )
        }

        // Secondary Action Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onReplayLast,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppleCardSurfaceSecondary)
            ) {
                Icon(Icons.Default.Replay, contentDescription = null, tint = AppleSystemTeal, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Replay Last", style = AppleHeadline, fontSize = 12.sp)
            }

            Button(
                onClick = onNavigateToRadar,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppleCardSurfaceSecondary)
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null, tint = AppleSystemBlue, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Radar View", style = AppleHeadline, fontSize = 12.sp)
            }
        }

        OutlinedButton(
            onClick = onNavigateToDiscoverMap,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(0.75.dp, AppleGlassBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
        ) {
            Icon(Icons.Default.Map, contentDescription = null, tint = AppleSystemBlue, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("Discover Offline Map", style = AppleHeadline, fontSize = 13.sp, color = AppleTextPrimary)
        }

        // Leave Session / Leave Channel Button
        OutlinedButton(
            onClick = onLeaveSession,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(0.75.dp, AppleSystemRed.copy(alpha = 0.4f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppleSystemRed)
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Leave Channel", style = AppleHeadline, fontSize = 13.sp)
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
        cornerRadius = 24.dp,
        isOutdoorMode = isOutdoorMode,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(AppleSystemBlue.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WifiFind,
                    contentDescription = null,
                    tint = AppleSystemBlue,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Active Channel",
                style = AppleTitle2
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Connect with nearby devices over Bluetooth LE mesh to start talking and exchanging encrypted offline messages.",
                style = AppleFootnote,
                color = AppleTextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onCreateChannel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppleSystemBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Connect Devices", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onScanNearby,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(0.75.dp, AppleGlassBorder)
            ) {
                Text(text = "Scan Nearby", color = AppleTextPrimary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenMap,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(0.75.dp, AppleGlassBorder)
            ) {
                Icon(Icons.Default.Map, contentDescription = null, tint = AppleSystemBlue)
                Spacer(Modifier.width(8.dp))
                Text("Discover Offline Map", color = AppleTextPrimary)
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
            // Speaking pulse ring
            if (isSpeaking) {
                val infiniteTransition = rememberInfiniteTransition(label = "avatar_pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.22f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .border(1.5.dp, AppleSystemGreen, CircleShape)
                )
            }

            // Avatar bubble
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isRelay) AppleCardSurfaceTertiary else AppleCardSurfaceSecondary)
                    .border(
                        1.dp,
                        if (isRelay) AppleSystemOrange else if (isSpeaking) AppleSystemGreen else AppleGlassBorder,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = AppleHeadline,
                    fontSize = 13.sp,
                    color = if (isRelay) AppleSystemOrange else Color.White
                )
            }

            // Status indicator dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(if (isRelay) AppleSystemOrange else if (isOnline) AppleSystemGreen else AppleTextTertiary)
                    .border(1.5.dp, AppleBackground, CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = AppleCaption1,
            color = if (isSpeaking) AppleSystemGreen else AppleTextSecondary,
            fontWeight = if (isSpeaking) FontWeight.Bold else FontWeight.Normal
        )
        if (batteryPercent != null && batteryPercent in 0..100) {
            val battColor = when {
                batteryPercent > 50 -> AppleSystemGreen
                batteryPercent > 20 -> AppleSystemOrange
                else -> AppleSystemRed
            }
            Text(
                text = "$batteryPercent%",
                style = AppleMonospacedDigits.copy(fontSize = 10.sp, color = battColor)
            )
        }
    }
}

@Composable
private fun QualitySignalBars() {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        modifier = Modifier.height(14.dp)
    ) {
        Box(modifier = Modifier.width(3.dp).height(5.dp).clip(RoundedCornerShape(1.dp)).background(AppleSystemBlue))
        Box(modifier = Modifier.width(3.dp).height(8.dp).clip(RoundedCornerShape(1.dp)).background(AppleSystemBlue))
        Box(modifier = Modifier.width(3.dp).height(11.dp).clip(RoundedCornerShape(1.dp)).background(AppleSystemBlue))
        Box(modifier = Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(1.dp)).background(AppleSystemBlue))
        Box(modifier = Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(1.dp)).background(AppleTextQuaternary))
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
        interactive = true,
        onClick = onClick,
        modifier = modifier.height(72.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.16f)),
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
                    style = AppleHeadline,
                    fontSize = 13.sp
                )
                Text(
                    text = subtitle,
                    style = AppleFootnote,
                    color = AppleTextSecondary,
                    fontSize = 11.sp
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
        cornerRadius = 28.dp,
        isOutdoorMode = isOutdoorMode,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs: List<Pair<String, ImageVector>> = listOf(
                Pair("Walkie", Icons.Default.Radio),
                Pair("Messages", Icons.Default.ChatBubbleOutline),
                Pair("Contacts", Icons.Default.PeopleOutline),
                Pair("Settings", Icons.Default.Settings)
            )

            tabs.forEachIndexed { index, pair ->
                val isSelected = activeTab == index
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectTab(index) }
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = pair.second,
                        contentDescription = pair.first,
                        tint = if (isSelected) AppleSystemBlue else AppleTextTertiary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = pair.first,
                        style = if (isSelected) AppleHeadline.copy(fontSize = 10.sp) else AppleFootnote.copy(fontSize = 10.sp),
                        color = if (isSelected) AppleSystemBlue else AppleTextTertiary
                    )
                }
            }
        }
    }
}

// ============================================================================
// 3. Nearby Devices Screen (Apple AirDrop / Find My Style)
// ============================================================================

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

    LaunchedEffect(pendingRequest) {
        if (pendingRequest != null) {
            onNavigateToPairing()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleBackground)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppleTextPrimary)
                }
                Column {
                    Text(
                        text = "Nearby Devices",
                        style = AppleTitle2
                    )
                    Text(
                        text = "AirDrop-style local Bluetooth discovery",
                        style = AppleFootnote,
                        color = AppleTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Discoverability Info Card
            GlassSurface(
                cornerRadius = 20.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Visible as \"${viewModel.localDeviceName}\"",
                        style = AppleHeadline
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append(if (isAdvertising) "Discoverable" else "Not Discoverable")
                            append(" • ")
                            append(if (isDiscovering) "Scanning" else "Idle")
                        },
                        style = AppleFootnote,
                        color = if (isAdvertising && isDiscovering) AppleSystemGreen else AppleSystemOrange
                    )
                    Text(
                        text = "Keep this screen open on both devices to establish an offline link.",
                        style = AppleCaption1,
                        color = AppleTextTertiary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (transportError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = transportError!!,
                            color = AppleSystemRed,
                            style = AppleFootnote
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "DISCOVERED PEERS (${peers.size})",
                style = AppleCaption2,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            if (peers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AppleSystemBlue, strokeWidth = 2.5.dp, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Searching for Nearby Devices…",
                            style = AppleHeadline,
                            color = AppleTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ensure TalkLoop is running on the other phone.",
                            style = AppleFootnote,
                            color = AppleTextTertiary
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(peers, key = { it.id }) { peer ->
                        GlassSurface(
                            cornerRadius = 16.dp,
                            isOutdoorMode = isOutdoorMode,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(AppleCardSurfaceSecondary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Smartphone,
                                        contentDescription = null,
                                        tint = AppleSystemBlue,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = peer.name,
                                        style = AppleHeadline
                                    )
                                    val statusText = when {
                                        peer.isConnected && peer.isAuthenticated -> if (peer.isDirect) "Connected • Direct Radio • Encrypted" else "Connected • Mesh Relay (${peer.hopCount} Hops)"
                                        peer.isConnected && !peer.isAuthenticated -> "Authenticating • Securing Link…"
                                        else -> "Nearby • Tap Connect"
                                    }
                                    val statusColor = when {
                                        peer.isConnected && peer.isAuthenticated -> AppleSystemGreen
                                        peer.isConnected && !peer.isAuthenticated -> AppleSystemOrange
                                        else -> AppleTextSecondary
                                    }
                                    Text(
                                        text = statusText,
                                        style = AppleFootnote,
                                        color = statusColor
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (peer.isConnected) {
                                            viewModel.disconnectPeer(peer.id)
                                        } else {
                                            viewModel.connectToPeer(peer.id)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (peer.isConnected) AppleCardSurfaceSecondary else AppleSystemBlue,
                                        contentColor = if (peer.isConnected) AppleSystemRed else Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = if (peer.isConnected) "Disconnect" else "Connect",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
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

// ============================================================================
// 4. Pairing & Verification Screen (Apple Security Modal)
// ============================================================================

@Composable
fun PairingScreen(
    viewModel: WalkieTalkieViewModel,
    onPaired: () -> Unit,
    onBack: () -> Unit
) {
    val request by viewModel.pendingRequest.collectAsState()
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    val isInSession by viewModel.isInSession.collectAsState()
    var accepted by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(isInSession) {
        if (isInSession && !dismissed) {
            dismissed = true
            onPaired()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleBackground)
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppleTextPrimary)
                }
                Text(
                    text = "Verify Identity",
                    style = AppleTitle2
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
                        CircularProgressIndicator(color = AppleSystemBlue, strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = if (accepted) {
                                "Confirmed. Waiting for the partner phone to confirm match…"
                            } else {
                                "Waiting for incoming connection request…"
                            },
                            style = AppleBody,
                            color = AppleTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "Pairing with ${request!!.peerName}",
                            style = AppleHeadline,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Giant Monospaced 6-digit Code (Apple Style)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(AppleCardSurfaceSecondary)
                                .border(0.75.dp, AppleGlassBorder, RoundedCornerShape(16.dp))
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = request!!.authCode,
                                style = AppleLargeTitle.copy(
                                    color = AppleSystemBlue,
                                    fontSize = 36.sp,
                                    letterSpacing = 6.sp,
                                    fontFeatureSettings = "tnum"
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Compare the 6 digits with your partner's screen. If they match, tap Confirm Match.",
                            style = AppleFootnote,
                            color = AppleTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    accepted = true
                                    viewModel.acceptConnection(request!!.peerId)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppleSystemBlue,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Confirm Match",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
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
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppleSystemRed),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AppleSystemRed.copy(alpha = 0.5f))
                            ) {
                                Text(text = "Reject", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ============================================================================
// 5. Settings Screen (Apple iOS Settings Grouped Lists)
// ============================================================================

@Composable
fun SettingsScreen(
    viewModel: WalkieTalkieViewModel,
    onOpenOfflineMaps: () -> Unit,
    onBack: () -> Unit
) {
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    val speakerphoneEnabled by viewModel.speakerphoneEnabled.collectAsState()
    val currentAudioRoute by viewModel.currentAudioRoute.collectAsState()
    val availableAudioRoutes by viewModel.availableAudioRoutes.collectAsState()
    var isBatteryExempt by remember { mutableStateOf(viewModel.isBatteryOptimizationIgnored()) }
    val disturbanceMode by viewModel.disturbanceMode.collectAsState()
    val allowIncomingRequests by viewModel.allowIncomingRequests.collectAsState()
    val loopbackEnabled by viewModel.isLoopbackMode.collectAsState()
    val rogerBeepStyle by viewModel.rogerBeepStyle.collectAsState()

    val isSurvivalMode by viewModel.isSurvivalCodecMode.collectAsState()
    val isFloatingActive by viewModel.isFloatingPttActive.collectAsState()
    val isVolumePtt by viewModel.isVolumeKeyPttEnabled.collectAsState()
    val isScoActive by viewModel.isBluetoothScoActive.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleBackground)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppleTextPrimary)
                }
                Text(
                    text = "Settings",
                    style = AppleLargeTitle,
                    fontSize = 28.sp
                )
            }

            // GROUP 1: AUDIO & RANGE
            Column {
                Text(
                    text = "AUDIO & CODEC",
                    style = AppleCaption2,
                    modifier = Modifier.padding(start = 14.dp, bottom = 6.dp)
                )
                GlassSurface(cornerRadius = 20.dp, isOutdoorMode = isOutdoorMode, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Survival Mode Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleSurvivalCodecMode() }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AppleSystemOrange.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Bolt, null, tint = AppleSystemOrange, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Survival Range Mode", style = AppleHeadline, fontSize = 14.sp)
                                        if (isSurvivalMode) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "1.6 kbps",
                                                style = AppleMonospacedDigits.copy(fontSize = 9.sp, color = Color.Black),
                                                modifier = Modifier
                                                    .background(AppleSystemOrange, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        if (isSurvivalMode) "Codec 2 active: 8-byte micro-frames for extreme RF penetration"
                                        else "AMR-NB active: Natural voice clarity. Switch for weak signals.",
                                        style = AppleFootnote,
                                        color = AppleTextSecondary
                                    )
                                }
                            }
                            Switch(
                                checked = isSurvivalMode,
                                onCheckedChange = { viewModel.setSurvivalCodecMode(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AppleSystemGreen
                                )
                            )
                        }

                        HorizontalDivider(color = AppleGlassBorder)

                        // Audio Output Route Selector
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AppleSystemBlue.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.VolumeUp, null, tint = AppleSystemBlue, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Audio Output Route", style = AppleHeadline, fontSize = 14.sp)
                                    Text("Active: " + currentAudioRoute.displayName, style = AppleFootnote, color = AppleSystemTeal)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableAudioRoutes.forEach { route ->
                                    val isSelected = currentAudioRoute == route
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) AppleSystemBlue else AppleCardSurfaceSecondary)
                                            .clickable { viewModel.setAudioRoute(route) }
                                            .padding(vertical = 6.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            when (route) {
                                                AudioRoute.SPEAKER -> "Speaker"
                                                AudioRoute.EARPIECE -> "Earpiece"
                                                AudioRoute.BLUETOOTH_SCO -> "BT Headset"
                                                AudioRoute.WIRED_HEADSET -> "Wired"
                                            },
                                            style = AppleCaption1,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else AppleTextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = AppleGlassBorder)


                        HorizontalDivider(color = AppleGlassBorder)

                        // Roger Beep & Tactical Chimes Selector
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AppleSystemTeal.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.NotificationsActive, null, tint = AppleSystemTeal, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("Roger Beep & PTT Chimes", style = AppleHeadline, fontSize = 14.sp)
                                        Text(rogerBeepStyle.displayName + " • " + rogerBeepStyle.description, style = AppleFootnote, color = AppleTextSecondary)
                                    }
                                }
                                if (rogerBeepStyle != RogerBeepStyle.NONE) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AppleSystemTeal.copy(alpha = 0.15f))
                                            .clickable { viewModel.previewRogerBeepStyle(rogerBeepStyle) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Preview Tone", tint = AppleSystemTeal, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Preview", style = AppleCaption1, fontWeight = FontWeight.Bold, color = AppleSystemTeal)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(RogerBeepStyle.MOTOROLA, RogerBeepStyle.QUINDAR, RogerBeepStyle.TACTICAL_CHIRP).forEach { style ->
                                        val isSelected = rogerBeepStyle == style
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) AppleSystemTeal else AppleCardSurfaceSecondary)
                                                .clickable { viewModel.setRogerBeepStyle(style) }
                                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                when (style) {
                                                    RogerBeepStyle.MOTOROLA -> "Motorola"
                                                    RogerBeepStyle.QUINDAR -> "Quindar"
                                                    RogerBeepStyle.TACTICAL_CHIRP -> "Tactical"
                                                    else -> style.displayName
                                                },
                                                style = AppleCaption1,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else AppleTextSecondary
                                            )
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(RogerBeepStyle.SQUELCH, RogerBeepStyle.NONE).forEach { style ->
                                        val isSelected = rogerBeepStyle == style
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) AppleSystemTeal else AppleCardSurfaceSecondary)
                                                .clickable { viewModel.setRogerBeepStyle(style) }
                                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                when (style) {
                                                    RogerBeepStyle.SQUELCH -> "Tone Squelch"
                                                    RogerBeepStyle.NONE -> "Silent (Covert)"
                                                    else -> style.displayName
                                                },
                                                style = AppleCaption1,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else AppleTextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Bluetooth SCO Headset
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleBluetoothSco() }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AppleSystemIndigo.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Headphones, null, tint = AppleSystemIndigo, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Bluetooth Headset", style = AppleHeadline, fontSize = 14.sp)
                                    Text("Route voice to paired Bluetooth accessories", style = AppleFootnote, color = AppleTextSecondary)
                                }
                            }
                            Switch(
                                checked = isScoActive,
                                onCheckedChange = { viewModel.toggleBluetoothSco() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AppleSystemGreen
                                )
                            )
                        }

                        HorizontalDivider(color = AppleGlassBorder)

                        // Loopback Test
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AppleSystemTeal.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Hearing, null, tint = AppleSystemTeal, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Microphone Loopback", style = AppleHeadline, fontSize = 14.sp)
                                    Text("Echo microphone locally to test audio", style = AppleFootnote, color = AppleTextSecondary)
                                }
                            }
                            Switch(
                                checked = loopbackEnabled,
                                onCheckedChange = {
                                    viewModel.setLoopbackMode(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AppleSystemGreen
                                )
                            )
                        }
                    }
                }
            }

            // GROUP 2: MAPS & VECTOR TILES
            Column {
                Text(
                    text = "MAPS & TILES",
                    style = AppleCaption2,
                    modifier = Modifier.padding(start = 14.dp, bottom = 6.dp)
                )
                GlassSurface(
                    cornerRadius = 20.dp,
                    isOutdoorMode = isOutdoorMode,
                    interactive = true,
                    onClick = onOpenOfflineMaps,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AppleSystemGreen.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Map, null, tint = AppleSystemGreen, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Offline Vector Maps", style = AppleHeadline, fontSize = 14.sp)
                                Text("Pre-bundled OSM road packs & search grids", style = AppleFootnote, color = AppleTextSecondary)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = AppleTextTertiary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // GROUP 3: CONTROLS & SYSTEM
            Column {
                Text(
                    text = "CONTROLS & OVERLAYS",
                    style = AppleCaption2,
                    modifier = Modifier.padding(start = 14.dp, bottom = 6.dp)
                )
                GlassSurface(cornerRadius = 20.dp, isOutdoorMode = isOutdoorMode, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Floating PTT Orb
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AppleSystemPink.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.FlipToFront, null, tint = AppleSystemPink, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Floating PTT Orb", style = AppleHeadline, fontSize = 14.sp)
                                    Text("Movable talk button over Maps and Camera", style = AppleFootnote, color = AppleTextSecondary)
                                }
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
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AppleSystemGreen
                                )
                            )
                        }

                        HorizontalDivider(color = AppleGlassBorder)

                        // Hardware Volume Key PTT
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setVolumeKeyPttEnabled(!isVolumePtt) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AppleSystemYellow.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Smartphone, null, tint = AppleSystemYellow, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Pocket Volume Key PTT", style = AppleHeadline, fontSize = 14.sp)
                                    Text("Hold Volume Down while TalkLoop is on screen", style = AppleFootnote, color = AppleTextSecondary)
                                }
                            }
                            Switch(
                                checked = isVolumePtt,
                                onCheckedChange = { viewModel.setVolumeKeyPttEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AppleSystemGreen
                                )
                            )
                        }

                        HorizontalDivider(color = AppleGlassBorder)

                        // Background Endurance / Battery Optimization Exemption
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isBatteryExempt) {
                                        try {
                                            context.startActivity(viewModel.getBatteryOptimizationIntent())
                                            isBatteryExempt = viewModel.isBatteryOptimizationIgnored()
                                        } catch (e: Exception) {}
                                    }
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isBatteryExempt) AppleSystemGreen.copy(alpha = 0.2f) else AppleSystemOrange.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (isBatteryExempt) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                                        null,
                                        tint = if (isBatteryExempt) AppleSystemGreen else AppleSystemOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Background Doze Exemption", style = AppleHeadline, fontSize = 14.sp)
                                    Text(
                                        if (isBatteryExempt) "Unrestricted • Zero packet drop in pocket/sleep"
                                        else "Optimized • Tap to allow 60-min screen-off reception",
                                        style = AppleFootnote,
                                        color = if (isBatteryExempt) AppleSystemGreen else AppleSystemOrange
                                    )
                                }
                            }
                            if (!isBatteryExempt) {
                                Button(
                                    onClick = {
                                        try {
                                            context.startActivity(viewModel.getBatteryOptimizationIntent())
                                            isBatteryExempt = viewModel.isBatteryOptimizationIgnored()
                                        } catch (e: Exception) {}
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppleSystemOrange, contentColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Allow", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        HorizontalDivider(color = AppleGlassBorder)

                        // Outdoor Mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleOutdoorMode() }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AppleSystemBlue.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.WbSunny, null, tint = AppleSystemBlue, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Outdoor Sunlight Mode", style = AppleHeadline, fontSize = 14.sp)
                                    Text("High-contrast opaque surfaces for direct sun", style = AppleFootnote, color = AppleTextSecondary)
                                }
                            }
                            Switch(
                                checked = isOutdoorMode,
                                onCheckedChange = { viewModel.toggleOutdoorMode() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AppleSystemGreen
                                )
                            )
                        }
                    }
                }
            }

            // GROUP 4: POLICY & MESSAGES
            Column {
                Text(
                    text = "POLICY & PRIVACY",
                    style = AppleCaption2,
                    modifier = Modifier.padding(start = 14.dp, bottom = 6.dp)
                )
                GlassSurface(cornerRadius = 20.dp, isOutdoorMode = isOutdoorMode, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setAllowIncomingRequests(!allowIncomingRequests) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AppleSystemPurple.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PersonAdd, null, tint = AppleSystemPurple, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Allow Incoming Requests", style = AppleHeadline, fontSize = 14.sp)
                                    Text(if (allowIncomingRequests) "New devices prompt for verification" else "Incoming connections rejected", style = AppleFootnote, color = AppleTextSecondary)
                                }
                            }
                            Switch(
                                checked = allowIncomingRequests,
                                onCheckedChange = viewModel::setAllowIncomingRequests,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AppleSystemGreen
                                )
                            )
                        }

                        HorizontalDivider(color = AppleGlassBorder)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setDisturbanceMode(!disturbanceMode) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AppleSystemRed.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.NotificationsOff, null, tint = AppleSystemRed, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Disturbance Mode", style = AppleHeadline, fontSize = 14.sp)
                                    Text("Keep SOS banners visible while silencing audio siren", style = AppleFootnote, color = AppleTextSecondary)
                                }
                            }
                            Switch(
                                checked = disturbanceMode,
                                onCheckedChange = viewModel::setDisturbanceMode,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AppleSystemGreen
                                )
                            )
                        }

                        HorizontalDivider(color = AppleGlassBorder)

                        Button(
                            onClick = { viewModel.markTextMessagesRead() },
                            colors = ButtonDefaults.buttonColors(containerColor = AppleCardSurfaceSecondary, contentColor = AppleTextPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Text("Mark Received Messages Read", style = AppleHeadline, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ============================================================================
// 6. Contacts Screen (Apple iOS Contacts Style)
// ============================================================================

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
            .background(AppleBackground)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppleTextPrimary)
                }
                Text(
                    text = "Contacts",
                    style = AppleLargeTitle,
                    fontSize = 28.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            GlassSurface(
                cornerRadius = 20.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "TRUSTED OFF-GRID PEERS",
                        style = AppleCaption2
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Verified peers with exchanged cryptographic keys. Reconnects automatically.",
                        style = AppleFootnote,
                        color = AppleTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (peers.isEmpty()) {
                        Text(
                            text = "No saved contacts yet. Pair with nearby devices to exchange verified keys.",
                            style = AppleFootnote,
                            color = AppleTextTertiary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        peers.forEachIndexed { idx, peer ->
                            if (idx > 0) HorizontalDivider(color = AppleGlassBorder, modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(AppleCardSurfaceSecondary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = peer.name.take(2).uppercase(),
                                            style = AppleHeadline,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(text = peer.name, style = AppleHeadline, fontSize = 14.sp)
                                        val contactStatus = when {
                                            peer.isConnected && peer.isAuthenticated -> "Online • Secure Link"
                                            peer.isConnected && !peer.isAuthenticated -> "Securing Link…"
                                            else -> "Offline"
                                        }
                                        val contactColor = when {
                                            peer.isConnected && peer.isAuthenticated -> AppleSystemGreen
                                            peer.isConnected && !peer.isAuthenticated -> AppleSystemOrange
                                            else -> AppleTextTertiary
                                        }
                                        Text(text = contactStatus, style = AppleCaption1, color = contactColor)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val batt = peerTelemetry[peer.id]?.batteryPercent
                                    if (batt != null && batt in 0..100) {
                                        Text(
                                            text = "$batt%",
                                            style = AppleMonospacedDigits.copy(fontSize = 11.sp, color = AppleSystemGreen)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Verified Key",
                                        tint = AppleSystemGreen,
                                        modifier = Modifier.size(16.dp)
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

// ============================================================================
// 7. Messages Screen (Apple iMessage Style)
// ============================================================================

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
            .background(AppleBackground)
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppleTextPrimary)
                }
                Text(
                    text = "Messages",
                    style = AppleLargeTitle,
                    fontSize = 28.sp
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
                        text = "No active channel. Connect to a peer device to exchange encrypted off-grid text messages.",
                        style = AppleFootnote,
                        color = AppleTextSecondary,
                        modifier = Modifier.padding(16.dp)
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
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(AppleSystemBlue.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = AppleSystemBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "No Messages Yet",
                                style = AppleHeadline
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Send encrypted text messages to your connected peers.",
                                style = AppleFootnote,
                                color = AppleTextTertiary
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
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.78f)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (isMine) AppleSystemBlue else AppleCardSurfaceSecondary)
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = if (isMine) "You" else msg.senderName,
                                            style = AppleCaption1,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMine) Color.White.copy(alpha = 0.8f) else AppleSystemTeal
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = msg.content,
                                            style = AppleBody,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = when (msg.status) {
                                                    TextDeliveryStatus.SENDING -> "Sending…"
                                                    TextDeliveryStatus.DELIVERED -> "Delivered"
                                                    TextDeliveryStatus.FAILED -> "Failed"
                                                } + if (!isMine && !msg.isRead) " • Unread" else "",
                                                style = AppleCaption1,
                                                color = if (msg.status == TextDeliveryStatus.FAILED) AppleSystemRed else Color.White.copy(alpha = 0.6f)
                                            )
                                            Box {
                                                TextButton(
                                                    onClick = { actionsExpanded = true },
                                                    contentPadding = PaddingValues(0.dp)
                                                ) { Text("•••", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f)) }
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
                }

                // Apple iMessage Input Capsule
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("iMessage • Offline", style = AppleFootnote, color = AppleTextTertiary) },
                        singleLine = true,
                        shape = RoundedCornerShape(22.dp),
                        textStyle = AppleBody,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppleSystemBlue,
                            unfocusedBorderColor = AppleGlassBorder,
                            focusedContainerColor = AppleCardSurfaceSecondary,
                            unfocusedContainerColor = AppleCardSurfaceSecondary,
                            cursorColor = AppleSystemBlue
                        )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (draft.isNotBlank()) {
                                viewModel.sendTextMessage(draft.trim())
                                draft = ""
                            }
                        },
                        enabled = draft.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppleSystemBlue,
                            contentColor = Color.White,
                            disabledContainerColor = AppleCardSurfaceSecondary,
                            disabledContentColor = AppleTextTertiary
                        ),
                        shape = CircleShape,
                        modifier = Modifier.size(46.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ============================================================================
// 8. Create & Join Channel Screens (Apple Card Styling)
// ============================================================================

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
            .background(AppleBackground)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppleTextPrimary)
                }
                Text(
                    text = "Create Channel",
                    style = AppleTitle2
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            GlassSurface(
                cornerRadius = 20.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "CHANNEL NAME",
                        style = AppleCaption2
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = channelName,
                        onValueChange = { channelName = it },
                        placeholder = { Text("e.g. Field Operations Alpha", style = AppleFootnote) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AppleSystemBlue,
                            unfocusedBorderColor = AppleGlassBorder,
                            focusedContainerColor = AppleCardSurfaceSecondary,
                            unfocusedContainerColor = AppleCardSurfaceSecondary,
                            cursorColor = AppleSystemBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You will host this talk channel. Nearby devices can scan and join directly.",
                        style = AppleFootnote,
                        color = AppleTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val name = channelName.ifBlank { "Channel ${System.currentTimeMillis() % 1000}" }
                    viewModel.createChannel(name)
                    viewModel.startNearby()
                    onNavigateToNearby()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppleSystemBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create & Broadcast Channel", fontWeight = FontWeight.Bold)
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
            .background(AppleBackground)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppleTextPrimary)
                }
                Text(
                    text = "Join Channel",
                    style = AppleTitle2
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            GlassSurface(
                cornerRadius = 20.dp,
                isOutdoorMode = isOutdoorMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "CHANNEL SHORT CODE",
                        style = AppleCaption2
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = shortCode,
                        onValueChange = { shortCode = it.uppercase().take(6) },
                        placeholder = { Text("6-character code", style = AppleFootnote) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AppleSystemBlue,
                            unfocusedBorderColor = AppleGlassBorder,
                            focusedContainerColor = AppleCardSurfaceSecondary,
                            unfocusedContainerColor = AppleCardSurfaceSecondary,
                            cursorColor = AppleSystemBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ask the host for the 6-character code, or scan nearby to discover open channels.",
                        style = AppleFootnote,
                        color = AppleTextSecondary
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
                    containerColor = AppleSystemBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
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
                border = androidx.compose.foundation.BorderStroke(1.dp, AppleGlassBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = AppleSystemBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Nearby Instead", color = AppleTextPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
