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
import androidx.compose.material.icons.filled.ArrowBack
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
 * Entry point for multi-device connection: create a group, or join one that is nearby.
 * Deliberately only two choices, so there is no decision to get wrong.
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
    var groupName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.startNearby() }

    // The moment we belong to a group, move on to the roster.
    LaunchedEffect(groupState.isInGroup) {
        if (groupState.isInGroup) onGroupReady()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = "Connect Devices",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            GlassSurface(isOutdoorMode = isOutdoorMode, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = "Start a new group",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "You become the host. Everyone else picks your group from their list.",
                        color = Color(0xFF9FB3C8),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it.take(16) },
                        placeholder = { Text("Group name", color = Color(0xFF7A8CA0)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = ElectricAqua,
                            unfocusedBorderColor = GlassBorder,
                            cursorColor = ElectricAqua
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.createGroup(groupName.trim()) },
                        enabled = groupName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricAqua,
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF29415A)
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Create Group", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Join a group nearby",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            Spacer(Modifier.height(10.dp))

            if (discovered.isEmpty()) {
                GlassSurface(isOutdoorMode = isOutdoorMode, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = ElectricAqua,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = "Looking for groups nearby…",
                            color = Color(0xFFCBD5E1),
                            fontSize = 14.sp
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
    GlassSurface(isOutdoorMode = isOutdoorMode, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "${group.devicesVisible} device${if (group.devicesVisible == 1) "" else "s"} nearby",
                    color = Color(0xFF9FB3C8),
                    fontSize = 13.sp
                )
            }
            Button(
                onClick = onJoin,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricAqua,
                    contentColor = Color.Black
                )
            ) {
                Text("Join", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * The group roster: who is in, who is still connecting, and what went wrong. This is the
 * screen that answers "which group am I in and who am I actually connected to".
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
            .background(AtmosphericDarkBrush)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column {
                    Text(
                        text = group?.name ?: "Group",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (state.isCoordinator) {
                            "You are the host"
                        } else {
                            "Host: ${state.coordinator?.displayName ?: "…"}"
                        },
                        color = ElectricAqua,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            GlassSurface(isOutdoorMode = isOutdoorMode, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        text = "${state.liveMemberCount} of ${state.members.size} devices connected",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (state.pending.isNotEmpty()) {
                        Text(
                            text = "${state.pending.size} still connecting…",
                            color = WarmAmber,
                            fontSize = 13.sp
                        )
                    }
                    if (state.failed.isNotEmpty()) {
                        Text(
                            text = "${state.failed.size} could not connect",
                            color = RoseActive,
                            fontSize = 13.sp
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

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "To add another phone: open TalkLoop on it, tap Connect Devices, " +
                        "then pick \"${group?.name ?: ""}\".",
                    color = Color(0xFF7A8CA0),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onStartTalking,
                enabled = state.connected.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricAqua,
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF29415A),
                    disabledContentColor = Color(0xFF7A8CA0)
                ),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text(
                    text = if (state.connected.isEmpty()) "Waiting for a device…" else "Start Talking",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { viewModel.leaveGroup() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseActive),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Leave Group")
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
                        text = if (isLocal) "${member.displayName} (you)" else member.displayName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (member.role == GroupRole.COORDINATOR) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "HOST",
                            color = ElectricAqua,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ElectricAqua.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = statusLabel(member, isLocal),
                    color = statusColor(member, isLocal),
                    fontSize = 12.sp
                )
            }

            when {
                member.state == MemberState.PENDING_APPROVAL -> {
                    IconButton(onClick = onAccept) {
                        Icon(Icons.Default.Check, contentDescription = "Accept", tint = MintGreen)
                    }
                    IconButton(onClick = onDecline) {
                        Icon(Icons.Default.Close, contentDescription = "Decline", tint = RoseActive)
                    }
                }
                member.state == MemberState.FAILED && !isLocal -> {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = WarmAmber)
                    }
                }
                member.state == MemberState.CONNECTING ||
                    member.state == MemberState.RECONNECTING -> {
                    CircularProgressIndicator(
                        color = WarmAmber,
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
    member.state == MemberState.LEFT -> "Left the group"
    else -> ""
}

private fun statusColor(member: GroupMember, isLocal: Boolean): Color = when {
    isLocal -> ElectricAqua
    member.state == MemberState.CONNECTED -> MintGreen
    member.state == MemberState.FAILED -> RoseActive
    member.state == MemberState.PENDING_APPROVAL -> BrightCyan
    member.state == MemberState.CONNECTING ||
        member.state == MemberState.RECONNECTING -> WarmAmber
    else -> Color(0xFF9FB3C8)
}
