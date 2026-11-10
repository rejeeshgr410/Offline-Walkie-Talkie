package com.example.offlinewalkietalkie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinewalkietalkie.history.VoiceMessage
import com.example.offlinewalkietalkie.theme.*
import com.example.offlinewalkietalkie.ui.components.GlassSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceHistoryScreen(
    viewModel: WalkieTalkieViewModel,
    onBack: () -> Unit
) {
    val history by viewModel.voiceHistory.collectAsState()
    val isPlaying by viewModel.isHistoryPlaying.collectAsState()
    val playingId by viewModel.currentlyPlayingHistoryId.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice History", style = AppleTitle2) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppleTextPrimary)
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { viewModel.voiceHistoryManager.clearHistory() }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear History", tint = AppleTextSecondary)
                        }
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
                .padding(horizontal = 16.dp)
        ) {
            // Quick 1-Tap Replay Card (Apple Voice Memos Style)
            GlassSurface(
                cornerRadius = 20.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFFEFF1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color(0xFFE60023),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Latest Transmission", style = AppleHeadline, color = AppleTextPrimary)
                            Text("1-tap replay of latest audio", style = AppleFootnote, color = AppleTextSecondary)
                        }
                    }
                    Button(
                        onClick = { viewModel.replayLastMessage() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE60023),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = history.isNotEmpty(),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("Replay", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Text(
                text = "RECORDED TRANSMISSIONS (${history.size})",
                style = AppleCaption2,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
            )

            if (history.isEmpty()) {
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
                                .background(AppleCardSurfaceSecondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = AppleTextTertiary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Recorded Audio",
                            style = AppleHeadline,
                            color = AppleTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Incoming transmissions are auto-saved here for offline replay.",
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
                    items(history, key = { it.id }) { message ->
                        val isThisPlaying = isPlaying && playingId == message.id
                        VoiceMessageRow(
                            message = message,
                            isPlaying = isThisPlaying,
                            onPlayClick = {
                                if (isThisPlaying) {
                                    viewModel.stopReplay()
                                } else {
                                    viewModel.replayMessage(message)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceMessageRow(
    message: VoiceMessage,
    isPlaying: Boolean,
    onPlayClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }
    val durationSec = (message.durationMs / 1000L).coerceAtLeast(1L)

    GlassSurface(
        cornerRadius = 16.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            IconButton(
                onClick = onPlayClick,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPlaying) Color(0xFFE60023)
                        else Color(0xFFFFEFF1)
                    )
                    .border(
                        0.75.dp,
                        if (isPlaying) Color(0xFFE60023) else Color(0xFFFFCDD2),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                    tint = if (isPlaying) Color.White else Color(0xFFE60023),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (message.isIncoming) message.senderName else "You (Outgoing)",
                        style = AppleHeadline,
                        fontSize = 14.sp,
                        color = if (message.isIncoming) AppleTextPrimary else AppleSystemTeal
                    )
                    Text(
                        text = timeStr,
                        style = AppleFootnote,
                        color = AppleTextTertiary
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "0:${String.format("%02d", durationSec)}s",
                        style = AppleMonospacedDigits,
                        color = AppleTextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = if (message.isIncoming) "Incoming" else "Outgoing",
                        style = AppleCaption1,
                        color = AppleTextTertiary
                    )
                }
            }
        }
    }
}
