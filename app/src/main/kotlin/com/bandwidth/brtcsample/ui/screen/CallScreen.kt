package com.bandwidth.brtcsample.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandwidth.brtcsample.ui.component.AudioWaveformView
import com.bandwidth.brtcsample.ui.component.DetailsScreen
import com.bandwidth.brtcsample.ui.component.DialpadView
import com.bandwidth.brtcsample.ui.component.RecentsScreen
import com.bandwidth.brtcsample.ui.component.StatsOverlay
import com.bandwidth.brtcsample.viewmodel.CallViewModel
import com.bandwidth.brtcsample.viewmodel.ConnectionState

@Composable
fun CallScreen(viewModel: CallViewModel) {
    when (viewModel.connectionState) {
        ConnectionState.IN_CALL -> InCallLayout(viewModel)
        ConnectionState.RINGING -> RingingLayout(viewModel)
        else -> DialingLayout(viewModel)
    }
}

@Composable
private fun RingingLayout(viewModel: CallViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(Modifier.weight(1f))

            Icon(
                if (viewModel.isOutboundCall) Icons.Filled.Call else Icons.Filled.Business,
                contentDescription = null,
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (viewModel.isOutboundCall) viewModel.formattedPhoneNumber else "Incoming Call",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Light
            )
            Text(
                if (viewModel.isOutboundCall) "Ringing..." else "Incoming Call...",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp
            )

            Spacer(Modifier.weight(1f))

            if (viewModel.isOutboundCall) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 60.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.declineIncomingCall() },
                        modifier = Modifier
                            .size(70.dp)
                            .background(Color.Red, CircleShape)
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = "Cancel", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(80.dp),
                    modifier = Modifier.padding(bottom = 60.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { viewModel.declineIncomingCall() },
                            modifier = Modifier
                                .size(70.dp)
                                .background(Color.Red, CircleShape)
                        ) {
                            Icon(Icons.Filled.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Decline", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { viewModel.acceptIncomingCall() },
                            modifier = Modifier
                                .size(70.dp)
                                .background(Color(0xFF4CAF50), CircleShape)
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Accept", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialingLayout(viewModel: CallViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Dialpad, contentDescription = null) },
                    label = { Text("Keypad") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                    label = { Text("Recents") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    label = { Text("Details") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> KeypadTab(viewModel)
                1 -> RecentsScreen(
                    callHistory = viewModel.callHistory,
                    onSelectNumber = { e164 ->
                        viewModel.phoneNumber = e164
                        selectedTab = 0
                    }
                )
                2 -> DetailsScreen(viewModel)
            }
        }
    }
}

@Composable
private fun KeypadTab(viewModel: CallViewModel) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            viewModel.formattedPhoneNumber,
            fontSize = 28.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(Modifier.height(8.dp))

        DialpadView(onDigit = { viewModel.dialpadInput(it) })

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(56.dp))

            IconButton(
                onClick = { viewModel.call() },
                modifier = Modifier
                    .size(72.dp)
                    .background(Color(0xFF4CAF50), CircleShape)
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(32.dp))
            }

            if (viewModel.phoneNumber.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.phoneNumber = viewModel.phoneNumber.dropLast(1) },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Filled.Backspace, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Spacer(Modifier.size(56.dp))
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CallControlButton(
                icon = Icons.Filled.MicOff,
                label = "Mute",
                isActive = !viewModel.isMicEnabled,
                buttonSize = 60.dp,
                iconSize = 24.dp,
                onClick = { viewModel.toggleMic() }
            )

            CallControlButton(
                icon = Icons.Filled.VolumeUp,
                label = "Speaker",
                isActive = viewModel.isSpeakerOn,
                activeColor = Color(0xFF4CAF50),
                buttonSize = 60.dp,
                iconSize = 24.dp,
                onClick = { viewModel.toggleSpeaker() }
            )

            CallControlButton(
                icon = Icons.Filled.Close,
                label = "Disconnect",
                isActive = true,
                activeColor = Color.White,
                tint = Color.White,
                buttonSize = 60.dp,
                iconSize = 24.dp,
                onClick = { viewModel.disconnect() }
            )
        }
    }
}

@Composable
private fun InCallLayout(viewModel: CallViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            viewModel.callStats?.let { stats ->
                StatsOverlay(
                    stats = stats,
                    isExpanded = viewModel.showStatsOverlay,
                    onToggle = { viewModel.showStatsOverlay = !viewModel.showStatsOverlay },
                    formatBitrate = { viewModel.formatBitrate(it) },
                    modifier = Modifier.padding(top = 48.dp, start = 16.dp, end = 16.dp)
                )
            }

            if (viewModel.showDialpad) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.weight(1f))
                    
                    Text(
                        viewModel.phoneNumber,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    DialpadView(
                        onDigit = { viewModel.sendDtmf(it) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                    ) {
                        Text(
                            "Tone Duration: ${viewModel.dtmfDuration}ms",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        Slider(
                            value = viewModel.dtmfDuration.toFloat(),
                            onValueChange = { viewModel.dtmfDuration = it.toInt() },
                            valueRange = 40f..6000f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color(0xFF4CAF50)
                            )
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CallControlButton(
                            icon = Icons.Filled.Dialpad,
                            label = "Hide",
                            isActive = true,
                            activeColor = Color(0xFF4CAF50),
                            tint = Color.White,
                            buttonSize = 64.dp,
                            iconSize = 24.dp,
                            onClick = { viewModel.showDialpad = false }
                        )

                        IconButton(
                            onClick = { viewModel.hangup() },
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color.Red, CircleShape)
                        ) {
                            Icon(Icons.Filled.CallEnd, contentDescription = "End", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(viewModel.formattedPhoneNumber, color = Color.White, fontSize = 28.sp)
                    Text(
                        if (viewModel.callDuration > 0) viewModel.callDurationFormatted else viewModel.statusText,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }

                Spacer(Modifier.height(24.dp))

                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AudioWaveformView(
                        levels = viewModel.localAudioLevels,
                        label = "OUTGOING (mic)",
                        color = Color.Cyan
                    )
                    AudioWaveformView(
                        levels = viewModel.remoteAudioLevels,
                        label = "INCOMING (remote)",
                        color = Color(0xFF4CAF50)
                    )
                }

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallControlButton(
                        icon = Icons.Filled.MicOff,
                        label = "Mute",
                        isActive = !viewModel.isMicEnabled,
                        tint = Color.White,
                        buttonSize = 64.dp,
                        iconSize = 24.dp,
                        onClick = { viewModel.toggleMic() }
                    )

                    CallControlButton(
                        icon = Icons.Filled.VolumeUp,
                        label = "Speaker",
                        isActive = viewModel.isSpeakerOn,
                        activeColor = Color(0xFF4CAF50),
                        tint = Color.White,
                        buttonSize = 64.dp,
                        iconSize = 24.dp,
                        onClick = { viewModel.toggleSpeaker() }
                    )

                    CallControlButton(
                        icon = Icons.Filled.Dialpad,
                        label = "Keypad",
                        isActive = viewModel.showDialpad,
                        tint = Color.White,
                        buttonSize = 64.dp,
                        iconSize = 24.dp,
                        onClick = { viewModel.showDialpad = !viewModel.showDialpad }
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { viewModel.hangup() },
                            modifier = Modifier
                                .size(70.dp)
                                .background(Color.Red, CircleShape)
                        ) {
                            Icon(Icons.Filled.CallEnd, contentDescription = "End", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("End", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    activeColor: Color = Color.Red,
    buttonSize: androidx.compose.ui.unit.Dp = 70.dp,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(buttonSize)
                .background(Color.Gray.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isActive) activeColor else tint,
                modifier = Modifier.size(iconSize)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 10.sp, color = tint.copy(alpha = 0.7f))
    }
}
