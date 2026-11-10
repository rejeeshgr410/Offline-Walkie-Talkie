package com.example.offlinewalkietalkie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinewalkietalkie.group.DiscoveredGroup
import com.example.offlinewalkietalkie.group.GroupMember
import com.example.offlinewalkietalkie.group.GroupRole
import com.example.offlinewalkietalkie.group.MemberState
import com.example.offlinewalkietalkie.theme.*
import com.example.offlinewalkietalkie.ui.components.GlassSurface

/**
 * Apple-inspired Group Lobby Screen.
 */
@Composable
fun GroupLobbyScreen(
    viewModel: WalkieTalkieViewModel,
    onGroupReady: () -> Unit,
    onBack: () -> Unit
) {
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    val discovered by viewModel.discoveredGroups.collectAsState()
    val groupState by viewModel.groupState.collectAsState()
    var groupName by remember { mutableStateOf(android.os.Build.MODEL.take(8).trim().ifBlank { "Alpha" } + " Channel") }

    LaunchedEffect(Unit) { viewModel.startNearby() }

    LaunchedEffect(groupState.isInGroup) {
        if (groupState.isInGroup) onGroupReady()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleBackground)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppleTextPrimary)
                }
                Column {
                    Text(
                        text = "Connect Devices",
                        style = AppleTitle2
                    )
                    Text(
                        text = "Host or join an offline mesh talk group",
                        style = AppleFootnote,
                        color = AppleTextSecondary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            GlassSurface(cornerRadius = 20.dp, isOutdoorMode = isOutdoorMode, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = "START A NEW GROUP",
                        style = AppleCaption2
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "You become the host. Nearby devices can discover and join your channel.",
                        style = AppleFootnote,
                        color = AppleTextSecondary
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it.take(20) },
                        placeholder = { Text("Channel Name", style = AppleFootnote) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppleTextPrimary,
                            unfocusedTextColor = AppleTextPrimary,
                            focusedBorderColor = AppleSystemRed,
                            unfocusedBorderColor = AppleGlassBorder,
                            focusedContainerColor = AppleCardSurfaceSecondary,
                            unfocusedContainerColor = AppleCardSurfaceSecondary,
                            cursorColor = AppleSystemRed
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { viewModel.createGroup(groupName.trim()) },
                        enabled = groupName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppleSystemBlue,
                            contentColor = Color.White,
                            disabledContainerColor = AppleCardSurfaceSecondary,
                            disabledContentColor = AppleTextTertiary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Create Channel", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "NEARBY CHANNELS",
                style = AppleCaption2,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            if (discovered.isEmpty()) {
                GlassSurface(cornerRadius = 18.dp, isOutdoorMode = isOutdoorMode, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(18.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = AppleSystemBlue,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = "Scanning for nearby channels…",
                            style = AppleBody,
                            color = AppleTextSecondary
                        )
                    }
                }
            } else {
                discovered.forEach { group ->
                    DiscoveredGroupCard(
                        group = group,
                        isOutdoorMode = isOutdoorMode,
                        onJoin = { viewModel.joinGroup(group) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun DiscoveredGroupCard(
    group: DiscoveredGroup,
    isOutdoorMode: Boolean,
    onJoin: () -> Unit
) {
    GlassSurface(cornerRadius = 16.dp, isOutdoorMode = isOutdoorMode, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = AppleHeadline
                )
                Text(
                    text = "${group.devicesVisible} device${if (group.devicesVisible == 1) "" else "s"} broadcasting",
                    style = AppleFootnote,
                    color = AppleTextSecondary
                )
            }
            Button(
                onClick = onJoin,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppleSystemBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text("Join", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

/**
 * Apple-inspired Group Roster Screen.
 */
@Composable
fun GroupRoomScreen(
    viewModel: WalkieTalkieViewModel,
    onStartTalking: () -> Unit,
    onLeft: () -> Unit,
    onBack: () -> Unit
) {
    val isOutdoorMode by viewModel.isOutdoorMode.collectAsState()
    val state by viewModel.groupState.collectAsState()

    LaunchedEffect(state.isInGroup) {
        if (!state.isInGroup) onLeft()
    }

    val group = state.group

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleBackground)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppleTextPrimary)
                }
                Column {
                    Text(
                        text = group?.name ?: "Group",
                        style = AppleTitle2
                    )
                    Text(
                        text = if (state.isCoordinator) "You are the channel host" else "Host: ${state.coordinator?.displayName ?: "…"}",
                        style = AppleFootnote,
                        color = AppleSystemTeal
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            GlassSurface(cornerRadius = 20.dp, isOutdoorMode = isOutdoorMode, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "${state.liveMemberCount} of ${state.members.size} devices connected",
                        style = AppleHeadline
                    )
                    if (state.pending.isNotEmpty()) {
                        Text(
                            text = "${state.pending.size} still connecting…",
                            style = AppleFootnote,
                            color = AppleSystemOrange
                        )
                    }
                    if (state.failed.isNotEmpty()) {
                        Text(
                            text = "${state.failed.size} connection failure",
                            style = AppleFootnote,
                            color = AppleSystemRed
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                state.members.sortedBy { it.displayName }.forEach { member ->
                    MemberRow(
                        member = member,
                        isLocal = member.memberId == state.localMemberId,
                        isOutdoorMode = isOutdoorMode,
                        onAccept = { viewModel.respondToGroupRequest(member.memberId, true) },
                        onDecline = { viewModel.respondToGroupRequest(member.memberId, false) },
                        onRemove = { viewModel.removeGroupMember(member.memberId) }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "To add another phone: open TalkLoop on it, tap Connect Devices, and select \"${group?.name ?: ""}\".",
                    style = AppleFootnote,
                    color = AppleTextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onStartTalking,
                enabled = state.connected.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppleSystemBlue,
                    contentColor = Color.White,
                    disabledContainerColor = AppleCardSurfaceSecondary,
                    disabledContentColor = AppleTextTertiary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(
                    text = if (state.connected.isEmpty()) "Waiting for peers…" else "Start Talking",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { viewModel.leaveGroup() },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppleSystemRed),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("Leave Channel", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: GroupMember,
    isLocal: Boolean,
    isOutdoorMode: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onRemove: () -> Unit
) {
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
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(statusColor(member, isLocal).copy(alpha = 0.18f))
                    .border(1.dp, statusColor(member, isLocal).copy(alpha = 0.5f), CircleShape)
            ) {
                Text(
                    text = member.displayName.take(2).uppercase(),
                    color = statusColor(member, isLocal),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isLocal) "${member.displayName} (You)" else member.displayName,
                        style = AppleHeadline,
                        fontSize = 14.sp
                    )
                    if (member.role == GroupRole.COORDINATOR) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "HOST",
                            color = AppleSystemTeal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AppleSystemTeal.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = statusLabel(member, isLocal),
                    color = statusColor(member, isLocal),
                    style = AppleCaption1
                )
                if (member.state == MemberState.PENDING_APPROVAL && member.authCode != null) {
                    Text(
                        text = "Auth Code: ${member.authCode}",
                        style = AppleMonospacedDigits.copy(color = AppleTextPrimary, fontSize = 14.sp)
                    )
                }
            }

            when {
                member.state == MemberState.PENDING_APPROVAL -> {
                    IconButton(onClick = onAccept) {
                        Icon(Icons.Default.Check, contentDescription = "Accept", tint = AppleSystemGreen)
                    }
                    IconButton(onClick = onDecline) {
                        Icon(Icons.Default.Close, contentDescription = "Decline", tint = AppleSystemRed)
                    }
                }
                member.state == MemberState.FAILED && !isLocal -> {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = AppleSystemOrange)
                    }
                }
                member.state == MemberState.CONNECTING ||
                    member.state == MemberState.RECONNECTING -> {
                    CircularProgressIndicator(
                        color = AppleSystemOrange,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun statusLabel(member: GroupMember, isLocal: Boolean): String = when {
    isLocal -> "This device"
    member.state == MemberState.CONNECTED && !member.isDirect -> "Connected via relay"
    member.state == MemberState.CONNECTED -> "Connected"
    member.state == MemberState.CONNECTING -> "Connecting…"
    member.state == MemberState.PENDING_APPROVAL -> "Wants to join"
    member.state == MemberState.RECONNECTING -> "Reconnecting…"
    member.state == MemberState.FAILED -> member.failureReason ?: "Could not connect"
    member.state == MemberState.DISCOVERED -> "Found nearby"
    member.state == MemberState.LEFT -> "Left channel"
    else -> ""
}

private fun statusColor(member: GroupMember, isLocal: Boolean): Color = when {
    isLocal -> AppleSystemTeal
    member.state == MemberState.CONNECTED -> AppleSystemGreen
    member.state == MemberState.FAILED -> AppleSystemRed
    member.state == MemberState.PENDING_APPROVAL -> AppleSystemBlue
    member.state == MemberState.CONNECTING ||
        member.state == MemberState.RECONNECTING -> AppleSystemOrange
    else -> AppleTextTertiary
}
