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
                Icons.Filled.Business,
                contentDescription = null,
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("Incoming Call", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
            Text("Incoming Call...", color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp)

            Spacer(Modifier.weight(1f))

            // Accept / Decline
            Row(
                horizontalArrangement = Arrangement.spacedBy(80.dp),
                modifier = Modifier.padding(bottom = 60.dp)
            ) {
                // Decline
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

                // Accept
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
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (selectedTab == 0) {
                KeypadTab(viewModel)
            } else {
                RecentsScreen(
                    callHistory = viewModel.callHistory,
                    onSelectNumber = { e164 ->
                        viewModel.phoneNumber = e164
                        selectedTab = 0
                    }
                )
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
        Spacer(Modifier.height(48.dp))

        // Endpoint ID
        viewModel.endpointId?.let { eid ->
            if (eid.isNotEmpty()) {
                Text("Endpoint ID", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(eid, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.height(12.dp))
            }
        }

        // Phone number display
        Text(
            viewModel.formattedPhoneNumber,
            fontSize = 36.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(Modifier.height(24.dp))

        // Dialpad
        DialpadView(onDigit = { viewModel.dialpadInput(it) })

        Spacer(Modifier.height(16.dp))

        // Call + delete row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(80.dp))

            // Green call button
            IconButton(
                onClick = { viewModel.call() },
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFF4CAF50), CircleShape)
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(32.dp))
            }

            // Delete button
            if (viewModel.phoneNumber.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.phoneNumber = viewModel.phoneNumber.dropLast(1) },
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(Icons.Filled.Backspace, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Spacer(Modifier.size(80.dp))
            }
        }

        Spacer(Modifier.weight(1f))

        // Bottom controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Mute
            CallControlButton(
                icon = Icons.Filled.MicOff,
                label = "Mute",
                isActive = !viewModel.isMicEnabled,
                onClick = { viewModel.toggleMic() }
            )

            // Disconnect
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { viewModel.disconnect() },
                    modifier = Modifier
                        .size(70.dp)
                        .background(Color.Gray, CircleShape)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Disconnect", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text("Disconnect", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
            // Stats overlay
            viewModel.callStats?.let { stats ->
                StatsOverlay(
                    stats = stats,
                    isExpanded = viewModel.showStatsOverlay,
                    onToggle = { viewModel.showStatsOverlay = !viewModel.showStatsOverlay },
                    formatBitrate = { viewModel.formatBitrate(it) },
                    modifier = Modifier.padding(top = 48.dp, start = 16.dp, end = 16.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            // Contact info + timer
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

            // Audio waveforms
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

            Spacer(Modifier.height(12.dp))

            // In-call dialpad
            if (viewModel.showDialpad) {
                DialpadView(
                    onDigit = { viewModel.sendDtmf(it) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(16.dp))
            }

            // Bottom controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CallControlButton(
                    icon = Icons.Filled.MicOff,
                    label = "Mute",
                    isActive = !viewModel.isMicEnabled,
                    tint = Color.White,
                    onClick = { viewModel.toggleMic() }
                )

                CallControlButton(
                    icon = Icons.Filled.Dialpad,
                    label = "Keypad",
                    isActive = viewModel.showDialpad,
                    tint = Color.White,
                    onClick = { viewModel.showDialpad = !viewModel.showDialpad }
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { viewModel.hangup() },
                        modifier = Modifier
                            .size(70.dp)
                            .background(Color.Red, CircleShape)
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = "End", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("End", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
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
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(70.dp)
                .background(Color.Gray.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isActive) activeColor else tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 10.sp, color = tint.copy(alpha = 0.7f))
    }
}
