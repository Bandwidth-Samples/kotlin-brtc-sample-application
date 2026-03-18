package com.bandwidth.brtcsample.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandwidth.rtc.types.CallStatsSnapshot

@Composable
fun StatsOverlay(
    stats: CallStatsSnapshot,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    formatBitrate: (Double) -> String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val qualityColor = getQualityColor(stats)
        Row(
            modifier = Modifier
                .clickable { onToggle() }
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(qualityColor, CircleShape)
            )
            Text(
                stats.codec.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.8f)
            )
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(12.dp)
                    .rotate(if (isExpanded) 180f else 0f)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val total = stats.packetsReceived + stats.packetsLost
                val lossPercent = if (total > 0) (stats.packetsLost.toDouble() / total) * 100.0 else 0.0

                StatsRow("Jitter", String.format("%.1f ms", stats.jitter * 1000))
                StatsRow("Packet Loss", String.format("%.1f%% (%d)", lossPercent, stats.packetsLost))
                StatsRow("Packets Recv", "${stats.packetsReceived}")
                StatsRow("RTT", if (stats.roundTripTime > 0) String.format("%.0f ms", stats.roundTripTime * 1000) else "n/a")
                StatsRow("Bitrate In", formatBitrate(stats.inboundBitrate))
                StatsRow("Bitrate Out", formatBitrate(stats.outboundBitrate))
                StatsRow("Codec", stats.codec)
            }
        }
    }
}

@Composable
private fun StatsRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 10.sp, color = Color.White.copy(alpha = 0.9f), fontFamily = FontFamily.Monospace)
    }
}

private fun getQualityColor(stats: CallStatsSnapshot): Color {
    val total = stats.packetsReceived + stats.packetsLost
    val lossPercent = if (total > 0) (stats.packetsLost.toDouble() / total) * 100.0 else 0.0
    val jitterMs = stats.jitter * 1000
    val rttMs = stats.roundTripTime * 1000

    return when {
        lossPercent > 5 || jitterMs > 50 || rttMs > 300 -> Color.Red
        lossPercent > 1 || jitterMs > 20 || rttMs > 150 -> Color.Yellow
        else -> Color(0xFF4CAF50)
    }
}
