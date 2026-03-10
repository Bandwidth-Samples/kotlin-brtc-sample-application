package com.bandwidth.brtcsample.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class CallDirection {
    INBOUND, OUTBOUND
}

data class CallDetailRecord(
    val id: UUID = UUID.randomUUID(),
    val phoneNumber: String,
    val direction: CallDirection,
    val timestamp: Date = Date(),
    var duration: Long = 0 // seconds
) {
    val isMissed: Boolean get() = direction == CallDirection.INBOUND && duration == 0L

    val displayNumber: String get() = formatPhoneNumber(phoneNumber)

    val formattedDuration: String get() {
        val minutes = duration / 60
        val seconds = duration % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    val callSubtitle: String get() {
        if (isMissed) return "Missed Call"
        val type = if (direction == CallDirection.OUTBOUND) "Outgoing" else "Incoming"
        return if (duration > 0) "$type · $formattedDuration" else type
    }

    val formattedDate: String get() {
        val calendar = Calendar.getInstance()
        val now = Calendar.getInstance()

        calendar.time = timestamp

        return when {
            isSameDay(calendar, now) -> {
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(timestamp)
            }
            isYesterday(calendar, now) -> "Yesterday"
            isWithinLastWeek(calendar, now) -> {
                SimpleDateFormat("EEEE", Locale.getDefault()).format(timestamp)
            }
            else -> {
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(timestamp)
            }
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
               a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(a: Calendar, now: Calendar): Boolean {
        val yesterday = now.clone() as Calendar
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        return isSameDay(a, yesterday)
    }

    private fun isWithinLastWeek(a: Calendar, now: Calendar): Boolean {
        val weekAgo = now.clone() as Calendar
        weekAgo.add(Calendar.DAY_OF_YEAR, -6)
        return a.after(weekAgo)
    }

    companion object {
        fun formatPhoneNumber(value: String): String {
            val digits = value.filter { it.isDigit() }
            if (digits.length == 11 && digits.startsWith("1")) {
                val area = digits.substring(1, 4)
                val mid = digits.substring(4, 7)
                val last = digits.substring(7)
                return "+1 ($area) $mid-$last"
            }
            if (digits.length == 10) {
                val area = digits.substring(0, 3)
                val mid = digits.substring(3, 6)
                val last = digits.substring(6)
                return "($area) $mid-$last"
            }
            return value
        }
    }
}
