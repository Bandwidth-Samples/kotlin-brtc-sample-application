package com.bandwidth.brtcsample.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandwidth.brtcsample.viewmodel.CallViewModel
import com.bandwidth.brtcsample.viewmodel.ConnectionState

@Composable
fun DetailsScreen(viewModel: CallViewModel) {
    val scrollState = rememberScrollState()
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DetailsSectionCard(title = "Endpoint") {
            val endpointId = viewModel.endpointId
            if (!endpointId.isNullOrEmpty()) {
                Text(
                    text = endpointId,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(endpointId)) },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Copy Endpoint ID", fontSize = 12.sp)
                }
            } else {
                Text(
                    "Not connected",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DetailsSectionCard(title = "Connection Status") {
            DetailsStatusRow("State", value = viewModel.connectionState.displayName)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DetailsStatusRow("Status", value = viewModel.statusText.ifEmpty { "—" })
        }

        val stats = viewModel.callStats
        DetailsSectionCard(title = "Call Statistics") {
            if (stats != null) {
                Text(
                    "Audio Quality",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                DetailsStatusRow("Codec", value = stats.codec.uppercase())
                DetailsStatusRow("Jitter", value = String.format("%.1f ms", stats.jitter * 1000))
                DetailsStatusRow(
                    "RTT",
                    value = if (stats.roundTripTime > 0)
                        String.format("%.0f ms", stats.roundTripTime * 1000)
                    else "n/a"
                )
                DetailsStatusRow(
                    "Audio Level",
                    value = String.format("%.1f%%", stats.audioLevel * 100)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    "Packet Statistics",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                DetailsStatusRow("Packets Received", value = "${stats.packetsReceived}")
                DetailsStatusRow("Packets Sent", value = "${stats.packetsSent}")
                DetailsStatusRow("Packets Lost", value = "${stats.packetsLost}")
                val total = stats.packetsReceived + stats.packetsLost
                val lossPercent = if (total > 0) (stats.packetsLost.toDouble() / total) * 100.0 else 0.0
                DetailsStatusRow("Packet Loss", value = String.format("%.1f%%", lossPercent))

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    "Bitrate",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                DetailsStatusRow("Inbound", value = viewModel.formatBitrate(stats.inboundBitrate))
                DetailsStatusRow("Outbound", value = viewModel.formatBitrate(stats.outboundBitrate))
            } else {
                Text(
                    "No statistics available",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (viewModel.connectionState == ConnectionState.IN_CALL) {
            DetailsSectionCard(title = "Call Duration") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        viewModel.callDurationFormatted,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DetailsSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun DetailsStatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private val ConnectionState.displayName: String
    get() = when (this) {
        ConnectionState.DISCONNECTED -> "Disconnected"
        ConnectionState.CONNECTING -> "Connecting"
        ConnectionState.CONNECTED -> "Connected"
        ConnectionState.RINGING -> "Ringing"
        ConnectionState.IN_CALL -> "In Call"
    }
