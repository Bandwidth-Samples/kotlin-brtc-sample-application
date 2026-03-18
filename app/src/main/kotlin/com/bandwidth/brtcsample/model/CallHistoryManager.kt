package com.bandwidth.brtcsample.model

import androidx.compose.runtime.mutableStateListOf
import java.util.UUID

class CallHistoryManager {
    val records = mutableStateListOf<CallDetailRecord>()

    fun addRecord(record: CallDetailRecord) {
        records.add(0, record)
    }

    fun updateDuration(id: UUID, duration: Long) {
        val index = records.indexOfFirst { it.id == id }
        if (index >= 0) {
            records[index] = records[index].copy(duration = duration)
        }
    }

    fun clearAll() {
        records.clear()
    }
}
