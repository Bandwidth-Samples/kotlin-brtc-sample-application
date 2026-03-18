package com.bandwidth.brtcsample.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandwidth.brtcsample.model.CallDetailRecord
import com.bandwidth.brtcsample.model.CallDirection
import com.bandwidth.brtcsample.model.CallHistoryManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    callHistory: CallHistoryManager,
    onSelectNumber: (String) -> Unit
) {
    var filter by remember { mutableStateOf(CallFilter.ALL) }

    val records = when (filter) {
        CallFilter.ALL -> callHistory.records.toList()
        CallFilter.MISSED -> callHistory.records.filter { it.isMissed }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = filter == CallFilter.ALL,
                            onClick = { filter = CallFilter.ALL },
                            label = { Text("All") }
                        )
                        FilterChip(
                            selected = filter == CallFilter.MISSED,
                            onClick = { filter = CallFilter.MISSED },
                            label = { Text("Missed") }
                        )
                    }
                },
                actions = {
                    if (callHistory.records.isNotEmpty()) {
                        TextButton(onClick = { callHistory.clearAll() }) {
                            Text("Clear", color = Color.Red)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (filter == CallFilter.MISSED) "No Missed Calls" else "No Recent Calls",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (filter == CallFilter.MISSED) "You haven't missed any calls." else "Your call history will appear here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    CallRecordRow(
                        record = record,
                        onCall = {
                            normalizedE164(record.phoneNumber)?.let(onSelectNumber)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CallRecordRow(record: CallDetailRecord, onCall: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCall() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (record.direction == CallDirection.OUTBOUND) Icons.Filled.CallMade else Icons.Filled.CallReceived,
            contentDescription = null,
            tint = if (record.isMissed) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                record.displayNumber,
                fontWeight = FontWeight.Medium,
                color = if (record.isMissed) Color.Red else MaterialTheme.colorScheme.onSurface
            )
            Text(
                record.callSubtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            record.formattedDate,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isDialable(record.phoneNumber)) {
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = onCall, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Call, contentDescription = "Call", tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun normalizedE164(rawValue: String): String? {
    val digits = rawValue.filter { it.isDigit() }
    if (rawValue.startsWith("+") && digits.isNotEmpty()) return "+$digits"
    if (digits.length == 11 && digits.startsWith("1")) return "+$digits"
    if (digits.length == 10) return "+1$digits"
    return null
}

private fun isDialable(phoneNumber: String): Boolean {
    val digits = phoneNumber.filter { it.isDigit() }
    if (phoneNumber.startsWith("+")) return digits.isNotEmpty()
    if (digits.length == 11 && digits.startsWith("1")) return true
    return digits.length == 10
}

private enum class CallFilter { ALL, MISSED }
