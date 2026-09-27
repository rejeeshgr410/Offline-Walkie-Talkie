package com.example.offlinewalkietalkie.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinewalkietalkie.R
import com.example.offlinewalkietalkie.protocol.FloorState
import com.example.offlinewalkietalkie.theme.*

@Composable
fun PttOrb(
    floorState: FloorState,
    isOutdoorMode: Boolean = false,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    val isTalking = floorState is FloorState.HeldByMe || floorState is FloorState.Talking || floorState is FloorState.Granted
    val isRequesting = floorState is FloorState.Requesting
    val isHeldByOther = floorState is FloorState.HeldByOther
    // pointerInput(Unit) builds its gesture block once, so reading isHeldByOther directly
    // inside it would pin the value from first composition.
    val heldByOther by rememberUpdatedState(isHeldByOther)

    val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isTalking) 1.08f else if (isRequesting) 1.04f else 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val currentBrush = when {
        isTalking -> PttTalkingBrush
        else -> PttReadyBrush
    }

    val stateText = when (floorState) {
        is FloorState.HeldByMe, is FloorState.Talking, is FloorState.Granted -> stringResource(R.string.talking)
        is FloorState.Requesting -> stringResource(R.string.requesting_talk)
        is FloorState.HeldByOther -> stringResource(R.string.peer_is_speaking, floorState.peerName)
        is FloorState.Denied -> "Floor Denied"
        is FloorState.Releasing -> "Releasing..."
        is FloorState.Idle -> stringResource(R.string.hold_to_talk)
    }

    val targetSize = if (isPressed) 184.dp else 176.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(200.dp)
            .semantics {
                contentDescription = "Push to Talk button. Status: $stateText. Double tap or hold to talk."
            }
    ) {
        // Outer glow/pulse ring
        Box(
            modifier = Modifier
                .size(192.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .border(
                    width = if (isTalking) 2.dp else 1.dp,
                    color = if (isTalking) RoseActive.copy(alpha = 0.6f) else ElectricAqua.copy(alpha = 0.3f),
                    shape = CircleShape
                )
        )

        // Main 176dp Orb
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(targetSize)
                .clip(CircleShape)
                .background(
                    when {
                        isOutdoorMode && isTalking -> RoseActive
                        isOutdoorMode -> ElectricAqua
                        else -> Color.Transparent
                    }
                )
                .then(
                    if (!isOutdoorMode) Modifier.background(currentBrush) else Modifier
                )
                .border(2.dp, Color(0x66FFFFFF), CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (!heldByOther) {
                                isPressed = true
                                onPressStart()
                                tryAwaitRelease()
                                isPressed = false
                                onPressEnd()
                            }
                        }
                    )
                }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                // Mic Icon Badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .border(1.dp, Color(0x4DFFFFFF), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stateText,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}
