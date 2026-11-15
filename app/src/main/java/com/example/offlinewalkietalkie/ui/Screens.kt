package com.example.offlinewalkietalkie.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
                                listOf(Color(0xFFFF2A3A), Color(0xFFD6001C))
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
                    text = stringResource(R.string.onboarding_tagline),
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
                        containerColor = Color(0xFFE60023),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.continue_action),
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
                    Text(text = stringResource(R.string.skip_to_app), style = AppleBody, fontSize = 14.sp)
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
                    contentDescription = stringResource(R.string.granted),
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
    val pendingRequest by viewModel.pendingRequest.collectAsState()

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

    // A direct BLE link can finish service discovery while the user is on the
    // home cockpit. Do not require them to first guess that they must open the
    // Nearby page: bring the SAS confirmation screen forward on either side.
    LaunchedEffect(pendingRequest?.peerId) {
        if (pendingRequest != null) onNavigateToPairing()
    }

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
                // 1. Navigation Bar Header
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
                    onOpenMap = onNavigateToDiscoverMap,
                    onOpenRadar = onNavigateToRadar,
                    onOpenHistory = onNavigateToHistory
                )

                // 1b. Status Pill Bar
                StatusPillBar(
                    connectedCount = connectedPeers.size,
                    onClick = onNavigateToNearby
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
                pendingRequest?.let { req ->
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
                                        text = stringResource(R.string.incoming_connection_request),
                                        style = AppleCaption1,
                                        color = Color(0xFFE60023)
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
                                        .background(Color(0xFFFFEFF1))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = req.authCode,
                                        style = AppleHeadline.copy(color = Color(0xFFE60023), letterSpacing = 2.sp)
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
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE60023), contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(stringResource(R.string.confirm_match), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.rejectConnection(req.peerId) },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(stringResource(R.string.reject), fontSize = 12.sp)
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
fun TalkLoopLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val red = Color(0xFFE60023)

        // Upper swept curved wing
        val pathUpper = Path().apply {
            moveTo(0f, h * 0.38f)
            cubicTo(w * 0.22f, h * 0.16f, w * 0.62f, h * 0.08f, w * 0.98f, h * 0.08f)
            cubicTo(w * 0.68f, h * 0.28f, w * 0.40f, h * 0.44f, w * 0.14f, h * 0.48f)
            close()
        }
        drawPath(pathUpper, color = red)

        // Lower swept curved wing
        val pathLower = Path().apply {
            moveTo(0f, h * 0.68f)
            cubicTo(w * 0.18f, h * 0.58f, w * 0.46f, h * 0.54f, w * 0.76f, h * 0.54f)
            cubicTo(w * 0.52f, h * 0.72f, w * 0.30f, h * 0.88f, w * 0.08f, h * 0.94f)
            close()
        }
        drawPath(pathLower, color = red)
    }
}

@Composable
private fun TopHeaderBar(
    connectedCount: Int,
    isOutdoorMode: Boolean,
    isBroadcastingSos: Boolean = false,
    onOpenSos: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenRadar: () -> Unit = {},
    onOpenHistory: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: TalkLoop Logo + Title + Subtitle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            TalkLoopLogo(
                modifier = Modifier.size(width = 30.dp, height = 24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TalkLoop",
                    style = AppleHeadline.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleTextPrimary,
                        letterSpacing = (-0.4).sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.app_descriptor),
                    style = AppleCaption1.copy(
                        fontSize = 12.sp,
                        color = AppleTextSecondary
                    ),
                    maxLines = 1
                )
            }
        }

        // Right Action Buttons: Compass/Map, Radar, Red SOS Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Compass / Map Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .shadow(1.5.dp, CircleShape, spotColor = Color(0x10000000))
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(0.75.dp, AppleGlassBorder, CircleShape)
                    .clickable(onClick = onOpenMap)
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = stringResource(R.string.map_compass),
                    tint = AppleTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // 2. Radar Ping Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .shadow(1.5.dp, CircleShape, spotColor = Color(0x10000000))
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(0.75.dp, AppleGlassBorder, CircleShape)
                    .clickable(onClick = onOpenRadar)
            ) {
                Icon(
                    imageVector = Icons.Default.WifiTethering,
                    contentDescription = stringResource(R.string.radar),
                    tint = AppleTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // 3. Vibrant Red Emergency SOS Pill Button
            Button(
                onClick = onOpenSos,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(48.dp)
                    .shadow(2.dp, RoundedCornerShape(24.dp), spotColor = Color(0xFFE60023).copy(alpha = 0.4f))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFE60023), Color(0xFFFF1E27))
                        ),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Text(
                    text = if (isBroadcastingSos) stringResource(R.string.sos_on) else "SOS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun StatusPillBar(
    connectedCount: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(1.5.dp, RoundedCornerShape(24.dp), spotColor = Color(0x0C000000))
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(0.75.dp, AppleGlassBorder, RoundedCornerShape(24.dp))
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Green Active Dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00C853))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.ready_to_talk),
                style = AppleHeadline.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleTextPrimary
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(13.dp)
                    .background(AppleGlassBorder)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = null,
                tint = AppleTextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (connectedCount > 0) {
                    pluralStringResource(R.plurals.connected_devices_count, connectedCount, connectedCount)
                } else {
                    stringResource(R.string.auto_discovery_on)
                },
                style = AppleFootnote.copy(
                    fontSize = 12.sp,
                    color = AppleTextSecondary
                )
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = AppleTextTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun QuickCircleAction(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    emphasizeActive: Boolean = false,
    onClick: () -> Unit
) {
    val showAccent = isActive && emphasizeActive
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(54.dp)
                .shadow(2.dp, CircleShape, spotColor = Color(0x10000000))
                .clip(CircleShape)
                .background(Color.White)
                .border(0.75.dp, if (showAccent) Color(0xFFFF2A3A) else AppleGlassBorder, CircleShape)
                .clickable(onClick = onClick)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (showAccent) Color(0xFFE60023) else AppleTextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            style = AppleFootnote.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = if (showAccent) Color(0xFFE60023) else AppleTextPrimary
        )
    }
}

@Composable
private fun BentoActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    statusDot: Color? = null,
    hasLock: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        cornerRadius = 18.dp,
        interactive = true,
        onClick = onClick,
        modifier = modifier.height(72.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFEFF1)),
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
                    style = AppleHeadline.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    color = AppleTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (statusDot != null) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusDot)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (hasLock) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = AppleTextSecondary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(
                        text = subtitle,
                        style = AppleFootnote.copy(fontSize = 11.sp),
                        color = AppleTextSecondary
                    )
                }
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showMuteConfirmation) {
            AlertDialog(
                onDismissRequest = { showMuteConfirmation = false },
                containerColor = Color.White,
                shape = RoundedCornerShape(20.dp),
                title = { Text(stringResource(R.string.mute_live_audio_title), style = AppleTitle3, color = AppleTextPrimary) },
                text = {
                    Text(
                        stringResource(R.string.mute_live_audio_explanation),
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
                    ) { Text(stringResource(R.string.mute_audio), fontWeight = FontWeight.Bold, color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showMuteConfirmation = false }) {
                        Text(stringResource(R.string.cancel), color = AppleTextSecondary)
                    }
                }
            )
        }

        // Live Audio Muted Pill Indicator
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
                                text = stringResource(R.string.live_audio_muted),
                                style = AppleHeadline,
                                color = AppleSystemOrange,
                                fontSize = 13.sp
                            )
                            Text(
                                text = stringResource(R.string.transmissions_saved),
                                style = AppleCaption1,
                                color = AppleTextSecondary
                            )
                        }
                    }
                    Button(
                        onClick = onToggleMute,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppleCardSurfaceSecondary,
                            contentColor = AppleTextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(stringResource(R.string.unmute), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // 1. PTT Hero Dial (Prominently Centered with Concentric Rings)
        PttOrb(
            floorState = floorState,
            isOutdoorMode = isOutdoorMode,
            onPressStart = onPttStart,
            onPressEnd = onPttEnd
        )

        // 2. Quick Action Pods: Speaker, Monitor, Mute
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickCircleAction(
                icon = if (speakerphoneEnabled) Icons.Default.VolumeUp else Icons.Default.Headset,
                label = stringResource(R.string.speaker),
                isActive = speakerphoneEnabled,
                onClick = onToggleSpeaker
            )
            QuickCircleAction(
                icon = Icons.Default.Headphones,
                label = stringResource(R.string.monitor),
                isActive = isRelayModeActive,
                onClick = onToggleRelay
            )
            QuickCircleAction(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.MicOff,
                label = stringResource(R.string.mute),
                isActive = isMuted,
                emphasizeActive = true,
                onClick = {
                    if (isMuted) onToggleMute() else showMuteConfirmation = true
                }
            )
        }

        // 3. Direct Walkie-Talkie Card
        GlassSurface(
            cornerRadius = 20.dp,
            isOutdoorMode = isOutdoorMode,
            interactive = true,
            onClick = onNavigateToContacts,
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
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFFEFF1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = null,
                                tint = Color(0xFFE60023),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = if (connectedPeers.isNotEmpty()) channelName else stringResource(R.string.direct_walkie_talkie),
                                style = AppleHeadline.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppleTextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = pluralStringResource(
                                    R.plurals.devices_online_count,
                                    connectedPeers.size.coerceAtLeast(1),
                                    connectedPeers.size.coerceAtLeast(1)
                                ),
                                style = AppleFootnote.copy(fontSize = 12.sp, color = AppleTextSecondary)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = AppleTextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // If peers are connected, display participant avatars
                if (connectedPeers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isYouSpeaking = floorState is FloorState.HeldByMe || floorState is FloorState.Talking || floorState is FloorState.Granted
                        AvatarBadge(
                            name = stringResource(R.string.you),
                            initials = stringResource(R.string.me_initials),
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
        }

        // 4. 2x2 Bento Feature Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BentoActionCard(
                title = stringResource(R.string.nearby_devices),
                subtitle = stringResource(R.string.scan_pair),
                icon = Icons.Default.WifiTethering,
                iconTint = Color(0xFFE60023),
                onClick = onNavigateToNearby,
                modifier = Modifier.weight(1f)
            )
            BentoActionCard(
                title = stringResource(R.string.mesh_relay),
                subtitle = if (isRelayModeActive) stringResource(R.string.active) else stringResource(R.string.standby),
                icon = Icons.Default.Hub,
                iconTint = Color(0xFFE60023),
                statusDot = Color(0xFF00C853),
                onClick = onToggleRelay,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val replaySeconds = if (instantReplayDurationMs > 0) {
                minOf(10, (instantReplayDurationMs / 1000).coerceAtLeast(1)).toInt()
            } else {
                2
            }
            val replaySub = stringResource(R.string.last_seconds, replaySeconds)
            BentoActionCard(
                title = stringResource(R.string.instant_replay),
                subtitle = if (isInstantReplayPlaying) stringResource(R.string.playing) else replaySub,
                icon = Icons.Default.Replay,
                iconTint = Color(0xFFE60023),
                onClick = onReplayLast,
                modifier = Modifier.weight(1f)
            )
            BentoActionCard(
                title = stringResource(R.string.security),
                subtitle = if (securedPeerCount > 0) stringResource(R.string.encrypted_peer_count, securedPeerCount) else stringResource(R.string.direct_link),
                icon = Icons.Default.Shield,
                iconTint = Color(0xFFE60023),
                hasLock = true,
                onClick = onNavigateToPairing,
                modifier = Modifier.weight(1f)
            )
        }

        // Leave Channel Button (if in a joined session)
        if (connectedPeers.isNotEmpty()) {
            OutlinedButton(
                onClick = onLeaveSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(0.75.dp, Color(0xFFE60023).copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE60023))
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.leave_channel), style = AppleHeadline, fontSize = 13.sp)
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

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFEFF1))
                    .border(
                        1.dp,
                        if (isRelay) AppleSystemOrange else if (isSpeaking) AppleSystemGreen else Color(0xFFFFCDD2),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = AppleHeadline,
                    fontSize = 13.sp,
                    color = Color(0xFFE60023)
                )
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(if (isRelay) AppleSystemOrange else if (isOnline) AppleSystemGreen else AppleTextTertiary)
                    .border(1.5.dp, Color.White, CircleShape)
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
        Box(modifier = Modifier.width(3.dp).height(5.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFE60023)))
        Box(modifier = Modifier.width(3.dp).height(8.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFE60023)))
        Box(modifier = Modifier.width(3.dp).height(11.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFE60023)))
        Box(modifier = Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFE60023)))
        Box(modifier = Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(1.dp)).background(AppleTextQuaternary))
    }
}

@Composable
private fun BottomNavigationBar(
    activeTab: Int,
    isOutdoorMode: Boolean,
    onSelectTab: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(32.dp),
                spotColor = Color(0x12000000),
                ambientColor = Color(0x06000000)
            )
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White)
            .border(0.75.dp, AppleGlassBorder, RoundedCornerShape(32.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs: List<Pair<String, ImageVector>> = listOf(
                Pair(stringResource(R.string.walkie), Icons.Default.Radio),
                Pair(stringResource(R.string.messages), Icons.Default.ChatBubbleOutline),
                Pair(stringResource(R.string.contacts), Icons.Default.PeopleOutline),
                Pair(stringResource(R.string.settings), Icons.Default.Settings)
            )

            tabs.forEachIndexed { index, pair ->
                val isSelected = activeTab == index
                if (isSelected) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFFFEFF1))
                            .clickable { onSelectTab(index) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = pair.second,
                            contentDescription = pair.first,
                            tint = Color(0xFFE60023),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = pair.first,
                            style = AppleHeadline.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = Color(0xFFE60023)
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectTab(index) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = pair.second,
                            contentDescription = pair.first,
                            tint = AppleTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = pair.first,
                            style = AppleFootnote.copy(fontSize = 10.sp),
                            color = AppleTextSecondary
                        )
                    }
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
    val discoverabilityText = stringResource(
        if (isAdvertising) R.string.discoverable else R.string.not_discoverable
    )
    val scanningText = stringResource(if (isDiscovering) R.string.scanning else R.string.idle)

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = AppleTextPrimary)
                }
                Column {
                    Text(
                        text = stringResource(R.string.nearby_devices),
                        style = AppleTitle2
                    )
                    Text(
                        text = stringResource(R.string.nearby_discovery_subtitle),
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
                        text = stringResource(R.string.visible_as, viewModel.localDeviceName),
                        style = AppleHeadline
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$discoverabilityText • $scanningText",
                        style = AppleFootnote,
                        color = if (isAdvertising && isDiscovering) AppleSystemGreen else AppleSystemOrange
                    )
                    TextButton(onClick = {
                        viewModel.setTransportType(if (transportType == com.example.offlinewalkietalkie.transport.TransportType.BLUETOOTH_CLASSIC)
                            com.example.offlinewalkietalkie.transport.TransportType.BLE_GATT
                        else com.example.offlinewalkietalkie.transport.TransportType.BLUETOOTH_CLASSIC)
                    }) {
                        Text(if (transportType == com.example.offlinewalkietalkie.transport.TransportType.BLUETOOTH_CLASSIC)
                            "Use Bluetooth LE" else "Try Bluetooth Classic")
                    }
                    Text(
                        text = stringResource(R.string.keep_nearby_open),
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
                text = stringResource(R.string.discovered_peers_count, peers.size),
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
                        CircularProgressIndicator(color = Color(0xFFE60023), strokeWidth = 2.5.dp, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.searching_nearby_devices),
                            style = AppleHeadline,
                            color = AppleTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.ensure_other_phone),
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
                                        .background(Color(0xFFFFEFF1)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Smartphone,
                                        contentDescription = null,
                                        tint = Color(0xFFE60023),
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
                                        peer.isConnected && peer.isAuthenticated -> if (peer.isDirect) stringResource(R.string.peer_connected_direct) else stringResource(R.string.peer_connected_mesh, peer.hopCount)
                                        peer.isConnected && !peer.isAuthenticated -> stringResource(R.string.peer_authenticating)
                                        else -> stringResource(R.string.peer_nearby_connect)
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
                                        containerColor = if (peer.isConnected) AppleCardSurfaceSecondary else Color(0xFFE60023),
                                        contentColor = if (peer.isConnected) AppleSystemRed else Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = if (peer.isConnected) stringResource(R.string.disconnect) else stringResource(R.string.connect),
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
    val transportError by viewModel.transportError.collectAsState()
    var accepted by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(isInSession) {
        if (isInSession && !dismissed) {
            dismissed = true
            onPaired()
        }
    }

    // A rejection is delivered by the bearer before it tears the GATT link
    // down. Do not leave the other phone stranded on an empty verification
    // spinner after its partner has already refused the identity.
    LaunchedEffect(request, transportError) {
        if (request == null && transportError?.contains("rejected", ignoreCase = true) == true && !dismissed) {
            dismissed = true
            onBack()
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = AppleTextPrimary)
                }
                Text(
                    text = stringResource(R.string.verify_identity),
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
                        CircularProgressIndicator(color = Color(0xFFE60023), strokeWidth = 2.5.dp)
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
                        val req = request ?: return@Column
                        Text(
                            text = stringResource(R.string.pairing_with, req.peerName),
                            style = AppleHeadline,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Giant Monospaced 6-digit Code
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFFFEFF1))
                                .border(0.75.dp, Color(0xFFFF2A3A).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = req.authCode,
                                style = AppleLargeTitle.copy(
                                    color = Color(0xFFE60023),
                                    fontSize = 36.sp,
                                    letterSpacing = 6.sp,
                                    fontFeatureSettings = "tnum"
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.pairing_compare_help),
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
                                    viewModel.acceptConnection(req.peerId)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE60023),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.confirm_match),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.rejectConnection(req.peerId)
                                    onBack()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppleSystemRed),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AppleSystemRed.copy(alpha = 0.5f))
                            ) {
                                Text(text = stringResource(R.string.reject), fontWeight = FontWeight.SemiBold)
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
    val adaptiveAudio by viewModel.adaptiveAudioEnabled.collectAsState()
    val relayOnlyJoining by viewModel.relayOnlyJoining.collectAsState()
    val isVolumePtt by viewModel.isVolumeKeyPttEnabled.collectAsState()
    val isScoActive by viewModel.isBluetoothScoActive.collectAsState()
    val linkTelemetry by viewModel.linkTelemetry.collectAsState()
    val isLinkTestRunning by viewModel.isLinkTestRunning.collectAsState()
    val diagnosticsReport by viewModel.diagnosticsReport.collectAsState()
    var showDiagnosticsReport by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val shareReportLabel = stringResource(R.string.share_report)

    LaunchedEffect(diagnosticsReport) {
        if (diagnosticsReport != null) showDiagnosticsReport = true
    }

    if (showDiagnosticsReport && diagnosticsReport != null) {
        val report = diagnosticsReport.orEmpty()
        AlertDialog(
            onDismissRequest = {
                showDiagnosticsReport = false
                viewModel.clearDiagnosticsReport()
            },
            title = { Text(stringResource(R.string.diagnostics_report_title), style = AppleTitle2) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        stringResource(R.string.diagnostics_privacy_notice),
                        style = AppleFootnote,
                        color = AppleSystemGreen
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        report,
                        style = AppleMonospacedDigits.copy(fontSize = 11.sp),
                        color = AppleTextPrimary
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("TalkLoop diagnostics", report))
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.copy_report)) }
                    TextButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "TalkLoop redacted diagnostics")
                                putExtra(Intent.EXTRA_TEXT, report)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, shareReportLabel))
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.share_report)) }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiagnosticsReport = false
                        viewModel.clearDiagnosticsReport()
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.close)) }
            }
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            isBatteryExempt = viewModel.isBatteryOptimizationIgnored()
            kotlinx.coroutines.delay(1000)
        }
    }

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = AppleTextPrimary)
                }
                Text(
                    text = stringResource(R.string.settings),
                    style = AppleLargeTitle,
                    fontSize = 28.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // GROUP 1: AUDIO & RANGE
            Column {
                Text(
                    text = stringResource(R.string.audio_codec_section),
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
                                        Text(stringResource(R.string.survival_range_mode), style = AppleHeadline, fontSize = 14.sp)
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
                                        if (isSurvivalMode) stringResource(R.string.survival_codec_active)
                                        else stringResource(R.string.standard_codec_active),
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

                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Adaptive audio", style = AppleHeadline)
                            Switch(checked = adaptiveAudio, onCheckedChange = viewModel::setAdaptiveAudioEnabled)
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Join groups as relay only", style = AppleHeadline)
                            Switch(checked = relayOnlyJoining, onCheckedChange = viewModel::setRelayOnlyJoining)
                        }

                        HorizontalDivider(color = AppleGlassBorder)

                        // Audio Output Route Selector
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFFEFF1)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.VolumeUp, null, tint = Color(0xFFE60023), modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(stringResource(R.string.audio_output_route), style = AppleHeadline, fontSize = 14.sp)
                                    val activeRouteLabel = when (currentAudioRoute) {
                                        AudioRoute.SPEAKER -> stringResource(R.string.speaker)
                                        AudioRoute.EARPIECE -> stringResource(R.string.earpiece)
                                        AudioRoute.BLUETOOTH_SCO -> stringResource(R.string.bluetooth_headset_short)
                                        AudioRoute.WIRED_HEADSET -> stringResource(R.string.wired_headset_short)
                                    }
                                    Text(stringResource(R.string.active_route, activeRouteLabel), style = AppleFootnote, color = Color(0xFFE60023))
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
                                            .background(if (isSelected) Color(0xFFE60023) else AppleCardSurfaceSecondary)
                                            .clickable { viewModel.setAudioRoute(route) }
                                            .padding(vertical = 6.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            when (route) {
                                                AudioRoute.SPEAKER -> stringResource(R.string.speaker)
                                                AudioRoute.EARPIECE -> stringResource(R.string.earpiece)
                                                AudioRoute.BLUETOOTH_SCO -> stringResource(R.string.bluetooth_headset_short)
                                                AudioRoute.WIRED_HEADSET -> stringResource(R.string.wired_headset_short)
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
                                            .background(Color(0xFFFFEFF1)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.NotificationsActive, null, tint = Color(0xFFE60023), modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(stringResource(R.string.roger_beep_chimes), style = AppleHeadline, fontSize = 14.sp)
                                        Text(
                                            when (rogerBeepStyle) {
                                                RogerBeepStyle.MOTOROLA -> stringResource(R.string.roger_motorola_description)
                                                RogerBeepStyle.QUINDAR -> stringResource(R.string.roger_quindar_description)
                                                RogerBeepStyle.TACTICAL_CHIRP -> stringResource(R.string.roger_tactical_description)
                                                RogerBeepStyle.SQUELCH -> stringResource(R.string.roger_squelch_description)
                                                RogerBeepStyle.NONE -> stringResource(R.string.roger_silent_description)
                                            },
                                            style = AppleFootnote,
                                            color = AppleTextSecondary
                                        )
                                    }
                                }
                                if (rogerBeepStyle != RogerBeepStyle.NONE) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFFFEFF1))
                                            .clickable { viewModel.previewRogerBeepStyle(rogerBeepStyle) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.preview_tone), tint = Color(0xFFE60023), modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.preview), style = AppleCaption1, fontWeight = FontWeight.Bold, color = Color(0xFFE60023))
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
                                                .background(if (isSelected) Color(0xFFE60023) else AppleCardSurfaceSecondary)
                                                .clickable { viewModel.setRogerBeepStyle(style) }
                                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                when (style) {
                                                    RogerBeepStyle.MOTOROLA -> stringResource(R.string.roger_motorola)
                                                    RogerBeepStyle.QUINDAR -> stringResource(R.string.roger_quindar)
                                                    RogerBeepStyle.TACTICAL_CHIRP -> stringResource(R.string.roger_tactical)
                                                    else -> stringResource(R.string.roger_silent)
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
                                                .background(if (isSelected) Color(0xFFE60023) else AppleCardSurfaceSecondary)
                                                .clickable { viewModel.setRogerBeepStyle(style) }
                                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                when (style) {
                                                    RogerBeepStyle.SQUELCH -> stringResource(R.string.roger_squelch)
                                                    RogerBeepStyle.NONE -> stringResource(R.string.roger_silent)
                                                    else -> stringResource(R.string.roger_silent)
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
                                    Text(stringResource(R.string.bluetooth_headset), style = AppleHeadline, fontSize = 14.sp)
                                    Text(stringResource(R.string.bluetooth_headset_desc), style = AppleFootnote, color = AppleTextSecondary)
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
                                    Text(stringResource(R.string.microphone_loopback), style = AppleHeadline, fontSize = 14.sp)
                                    Text(stringResource(R.string.microphone_loopback_desc), style = AppleFootnote, color = AppleTextSecondary)
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
                    text = stringResource(R.string.maps_tiles_section),
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
                                Text(stringResource(R.string.offline_vector_maps), style = AppleHeadline, fontSize = 14.sp)
                                Text(stringResource(R.string.offline_vector_maps_desc), style = AppleFootnote, color = AppleTextSecondary)
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = AppleTextTertiary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // GROUP 3: CONTROLS & SYSTEM
            Column {
                Text(
                    text = stringResource(R.string.controls_overlays_section),
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
                                    Text(stringResource(R.string.floating_ptt_orb), style = AppleHeadline, fontSize = 14.sp)
                                    Text(stringResource(R.string.floating_ptt_orb_desc), style = AppleFootnote, color = AppleTextSecondary)
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
                                    Text(stringResource(R.string.pocket_volume_ptt), style = AppleHeadline, fontSize = 14.sp)
                                    Text(stringResource(R.string.pocket_volume_ptt_desc), style = AppleFootnote, color = AppleTextSecondary)
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
                                        viewModel.requestBatteryOptimization(context)
                                        isBatteryExempt = viewModel.isBatteryOptimizationIgnored()
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
                                    Text(stringResource(R.string.background_doze_exemption), style = AppleHeadline, fontSize = 14.sp)
                                    Text(
                                        if (isBatteryExempt) stringResource(R.string.battery_unrestricted_desc)
                                        else stringResource(R.string.battery_optimized_desc),
                                        style = AppleFootnote,
                                        color = if (isBatteryExempt) AppleSystemGreen else AppleSystemOrange
                                    )
                                }
                            }
                            if (!isBatteryExempt) {
                                Button(
                                    onClick = {
                                        viewModel.requestBatteryOptimization(context)
                                        isBatteryExempt = viewModel.isBatteryOptimizationIgnored()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppleSystemOrange, contentColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text(stringResource(R.string.allow), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                        .background(AppleSystemOrange.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.WbSunny, null, tint = AppleSystemOrange, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(stringResource(R.string.outdoor_sunlight_mode), style = AppleHeadline, fontSize = 14.sp)
                                    Text(stringResource(R.string.outdoor_sunlight_desc), style = AppleFootnote, color = AppleTextSecondary)
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

            // GROUP 4: CONNECTION DIAGNOSTICS
            Column {
                Text(
                    text = stringResource(R.string.connection_diagnostics),
                    style = AppleCaption2,
                    modifier = Modifier.padding(start = 14.dp, bottom = 6.dp)
                )
                GlassSurface(cornerRadius = 20.dp, isOutdoorMode = isOutdoorMode, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AppleSystemBlue.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.NetworkCheck, null, tint = AppleSystemBlue, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.offline_link_test), style = AppleHeadline, fontSize = 14.sp)
                                Text(
                                    when {
                                        isLinkTestRunning -> stringResource(R.string.testing_link)
                                        linkTelemetry.packetsSent > 0 -> stringResource(
                                            R.string.link_test_complete,
                                            linkTelemetry.packetsReceived,
                                            linkTelemetry.packetsSent
                                        )
                                        else -> stringResource(R.string.link_test_connect_peer)
                                    },
                                    style = AppleFootnote,
                                    color = AppleTextSecondary
                                )
                            }
                            Text(
                                "${linkTelemetry.qualityBars}/5",
                                style = AppleMonospacedDigits.copy(fontSize = 13.sp),
                                color = if (linkTelemetry.qualityBars >= 4) AppleSystemGreen else AppleSystemOrange
                            )
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DiagnosticMetric(
                                label = stringResource(R.string.rtt_label),
                                value = if (linkTelemetry.rttMs > 0) stringResource(R.string.milliseconds_value, linkTelemetry.rttMs.toString()) else "—",
                                modifier = Modifier.weight(1f)
                            )
                            DiagnosticMetric(
                                label = stringResource(R.string.loss_label),
                                value = "${"%.0f".format(linkTelemetry.packetLossPercent)}%",
                                modifier = Modifier.weight(1f)
                            )
                            DiagnosticMetric(
                                label = stringResource(R.string.jitter_label),
                                value = stringResource(R.string.milliseconds_value, "%.1f".format(linkTelemetry.jitterMs)),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Button(
                            onClick = viewModel::runLinkTest,
                            enabled = !isLinkTestRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = AppleSystemBlue, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            if (isLinkTestRunning) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(
                                if (isLinkTestRunning) stringResource(R.string.testing_link)
                                else stringResource(R.string.run_link_test),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.generateRedactedDiagnostics() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.PrivacyTip, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.generate_redacted_report), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // GROUP 5: POLICY & MESSAGES
            Column {
                Text(
                    text = stringResource(R.string.policy_privacy_section),
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
                                    Text(stringResource(R.string.allow_incoming_requests), style = AppleHeadline, fontSize = 14.sp)
                                    Text(
                                        if (allowIncomingRequests) stringResource(R.string.incoming_verification_desc)
                                        else stringResource(R.string.incoming_rejected_desc),
                                        style = AppleFootnote,
                                        color = AppleTextSecondary
                                    )
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
                                    Text(stringResource(R.string.disturbance_mode), style = AppleHeadline, fontSize = 14.sp)
                                    Text(stringResource(R.string.disturbance_mode_desc), style = AppleFootnote, color = AppleTextSecondary)
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
                            Text(stringResource(R.string.mark_messages_read), style = AppleHeadline, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DiagnosticMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppleCardSurfaceSecondary)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = AppleCaption2, color = AppleTextSecondary, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(value, style = AppleMonospacedDigits.copy(fontSize = 12.sp), color = AppleTextPrimary, maxLines = 1)
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = AppleTextPrimary)
                }
                Text(
                    text = stringResource(R.string.contacts),
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
                        text = stringResource(R.string.trusted_peers_section),
                        style = AppleCaption2
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.trusted_peers_desc),
                        style = AppleFootnote,
                        color = AppleTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (peers.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_saved_contacts),
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
                                            .background(Color(0xFFFFEFF1)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = peer.name.take(2).uppercase(),
                                            style = AppleHeadline,
                                            fontSize = 13.sp,
                                            color = Color(0xFFE60023)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(text = peer.name, style = AppleHeadline, fontSize = 14.sp)
                                        val contactStatus = when {
                                            peer.isConnected && peer.isAuthenticated -> stringResource(R.string.contact_online_secure)
                                            peer.isConnected && !peer.isAuthenticated -> stringResource(R.string.contact_securing)
                                            else -> stringResource(R.string.offline)
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
                                        contentDescription = stringResource(R.string.verified_key),
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = AppleTextPrimary)
                }
                Text(
                    text = stringResource(R.string.messages),
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
                        text = stringResource(R.string.no_active_channel_messages),
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
                                    .background(Color(0xFFFFEFF1)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFE60023),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.no_messages_yet),
                                style = AppleHeadline
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.send_encrypted_messages),
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
                            val isMine = msg.isOutgoing
                            var actionsExpanded by remember(msg.messageId) { mutableStateOf(false) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.78f)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            if (isMine) {
                                                Brush.horizontalGradient(
                                                    listOf(Color(0xFFE60023), Color(0xFFFF2A3A))
                                                )
                                            } else {
                                                Brush.linearGradient(
                                                    listOf(Color(0xFFF3F4F6), Color(0xFFF3F4F6))
                                                )
                                            }
                                        )
                                        .border(
                                            0.75.dp,
                                            if (isMine) Color(0xFFE60023) else AppleGlassBorder,
                                            RoundedCornerShape(18.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = if (isMine) stringResource(R.string.you) else msg.senderName,
                                            style = AppleCaption1,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMine) Color.White.copy(alpha = 0.85f) else Color(0xFFE60023)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = msg.content,
                                            style = AppleBody,
                                            color = if (isMine) Color.White else AppleTextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = when (msg.status) {
                                                    TextDeliveryStatus.SENDING -> stringResource(R.string.sending)
                                                    TextDeliveryStatus.DELIVERED -> stringResource(R.string.delivered)
                                                    TextDeliveryStatus.FAILED -> stringResource(R.string.failed)
                                                } + if (!isMine && !msg.isRead) " • ${stringResource(R.string.unread)}" else "",
                                                style = AppleCaption1,
                                                color = if (msg.status == TextDeliveryStatus.FAILED) AppleSystemRed else if (isMine) Color.White.copy(alpha = 0.7f) else AppleTextSecondary
                                            )
                                            Box {
                                                TextButton(
                                                    onClick = { actionsExpanded = true },
                                                    contentPadding = PaddingValues(0.dp)
                                                ) { Text("•••", fontSize = 12.sp, color = if (isMine) Color.White.copy(alpha = 0.7f) else AppleTextSecondary) }
                                                DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                                                    if (!isMine && !msg.isRead) DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.mark_read)) },
                                                        onClick = { actionsExpanded = false; viewModel.markTextMessageRead(msg.messageId) }
                                                    )
                                                    if (isMine && msg.status == TextDeliveryStatus.FAILED) DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.resend)) },
                                                        onClick = { actionsExpanded = false; viewModel.resendTextMessage(msg.messageId) }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.delete)) },
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
                        placeholder = { Text(stringResource(R.string.message_placeholder), style = AppleFootnote, color = AppleTextTertiary) },
                        singleLine = true,
                        shape = RoundedCornerShape(22.dp),
                        textStyle = AppleBody.copy(color = AppleTextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppleTextPrimary,
                            unfocusedTextColor = AppleTextPrimary,
                            focusedBorderColor = Color(0xFFE60023),
                            unfocusedBorderColor = AppleGlassBorder,
                            focusedContainerColor = AppleCardSurfaceSecondary,
                            unfocusedContainerColor = AppleCardSurfaceSecondary,
                            cursorColor = Color(0xFFE60023)
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
                            containerColor = Color(0xFFE60023),
                            contentColor = Color.White,
                            disabledContainerColor = AppleCardSurfaceSecondary,
                            disabledContentColor = AppleTextTertiary
                        ),
                        shape = CircleShape,
                        modifier = Modifier.size(46.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.send), tint = Color.White, modifier = Modifier.size(20.dp))
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
    val context = LocalContext.current
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    var channelName by remember { mutableStateOf("") }
    val generatedChannelName = stringResource(
        R.string.default_channel_name,
        (System.currentTimeMillis() % 1000).toString()
    )

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = AppleTextPrimary)
                }
                Text(
                    text = stringResource(R.string.create_channel),
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
                        text = stringResource(R.string.channel_name_label),
                        style = AppleCaption2
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = channelName,
                        onValueChange = { channelName = it },
                        placeholder = { Text(stringResource(R.string.channel_name_example), style = AppleFootnote) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppleTextPrimary,
                            unfocusedTextColor = AppleTextPrimary,
                            focusedBorderColor = Color(0xFFE60023),
                            unfocusedBorderColor = AppleGlassBorder,
                            focusedContainerColor = AppleCardSurfaceSecondary,
                            unfocusedContainerColor = AppleCardSurfaceSecondary,
                            cursorColor = Color(0xFFE60023)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.create_channel_desc),
                        style = AppleFootnote,
                        color = AppleTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val name = channelName.ifBlank { generatedChannelName }
                    viewModel.createChannel(name)
                    viewModel.startNearby()
                    onNavigateToNearby()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE60023),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.create_broadcast_channel), fontWeight = FontWeight.Bold)
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
    val context = LocalContext.current
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    var shortCode by remember { mutableStateOf("") }
    val defaultChannelName = stringResource(R.string.default_channel_name, shortCode)

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = AppleTextPrimary)
                }
                Text(
                    text = stringResource(R.string.join_channel_title),
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
                        text = stringResource(R.string.channel_short_code_label),
                        style = AppleCaption2
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = shortCode,
                        onValueChange = { shortCode = it.uppercase().take(6) },
                        placeholder = { Text(stringResource(R.string.short_code_placeholder), style = AppleFootnote) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppleTextPrimary,
                            unfocusedTextColor = AppleTextPrimary,
                            focusedBorderColor = Color(0xFFE60023),
                            unfocusedBorderColor = AppleGlassBorder,
                            focusedContainerColor = AppleCardSurfaceSecondary,
                            unfocusedContainerColor = AppleCardSurfaceSecondary,
                            cursorColor = Color(0xFFE60023)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.join_channel_desc),
                        style = AppleFootnote,
                        color = AppleTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.setChannelName(defaultChannelName)
                    viewModel.startNearby()
                    onNavigateToNearby()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE60023),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.join_with_code), fontWeight = FontWeight.Bold)
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
                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFE60023), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.scan_nearby_instead), color = AppleTextPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
