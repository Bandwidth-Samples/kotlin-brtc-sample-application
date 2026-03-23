package com.bandwidth.brtcsample.model

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Comprehensive tests for CallDetailRecord data class.
 *
 * Covers:
 * - Phone number formatting (various formats)
 * - Duration formatting
 * - Call subtitle generation
 * - Date formatting (today, yesterday, last week, older)
 * - Missed call detection
 * - Edge cases (empty strings, unusual formats)
 * - Data class behavior (copy, equality)
 */
class CallDetailRecordTest {

    // =========================================================================
    // Phone number formatting
    // =========================================================================

    @Test
    fun `formatPhoneNumber with 10-digit number`() {
        val result = CallDetailRecord.formatPhoneNumber("5551234567")
        assertEquals("(555) 123-4567", result)
    }

    @Test
    fun `formatPhoneNumber with 11-digit US number starting with 1`() {
        val result = CallDetailRecord.formatPhoneNumber("15551234567")
        assertEquals("+1 (555) 123-4567", result)
    }

    @Test
    fun `formatPhoneNumber with E164 format`() {
        val result = CallDetailRecord.formatPhoneNumber("+15551234567")
        assertEquals("+1 (555) 123-4567", result)
    }

    @Test
    fun `formatPhoneNumber with dashes and parens returns formatted`() {
        val result = CallDetailRecord.formatPhoneNumber("(555) 123-4567")
        assertEquals("(555) 123-4567", result)
    }

    @Test
    fun `formatPhoneNumber with short number returns as-is`() {
        val result = CallDetailRecord.formatPhoneNumber("911")
        assertEquals("911", result)
    }

    @Test
    fun `formatPhoneNumber with international non-US number returns as-is`() {
        val result = CallDetailRecord.formatPhoneNumber("+442071234567")
        assertEquals("+442071234567", result)
    }

    @Test
    fun `formatPhoneNumber with empty string returns empty`() {
        val result = CallDetailRecord.formatPhoneNumber("")
        assertEquals("", result)
    }

    @Test
    fun `formatPhoneNumber with text returns as-is`() {
        val result = CallDetailRecord.formatPhoneNumber("Incoming Call")
        assertEquals("Incoming Call", result)
    }

    @Test
    fun `formatPhoneNumber with 11 digits not starting with 1`() {
        val result = CallDetailRecord.formatPhoneNumber("25551234567")
        assertEquals("25551234567", result)
    }

    @Test
    fun `displayNumber returns formatted phone number`() {
        val record = CallDetailRecord(
            phoneNumber = "+15551234567",
            direction = CallDirection.OUTBOUND
        )
        assertEquals("+1 (555) 123-4567", record.displayNumber)
    }

    // =========================================================================
    // Duration formatting
    // =========================================================================

    @Test
    fun `formattedDuration for zero seconds`() {
        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.OUTBOUND,
            duration = 0
        )
        assertEquals("0:00", record.formattedDuration)
    }

    @Test
    fun `formattedDuration for seconds only`() {
        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.OUTBOUND,
            duration = 45
        )
        assertEquals("0:45", record.formattedDuration)
    }

    @Test
    fun `formattedDuration for minutes and seconds`() {
        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.OUTBOUND,
            duration = 125
        )
        assertEquals("2:05", record.formattedDuration)
    }

    @Test
    fun `formattedDuration for exactly one minute`() {
        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.OUTBOUND,
            duration = 60
        )
        assertEquals("1:00", record.formattedDuration)
    }

    @Test
    fun `formattedDuration for long call`() {
        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.OUTBOUND,
            duration = 3661 // 61 minutes, 1 second
        )
        assertEquals("61:01", record.formattedDuration)
    }

    @Test
    fun `formattedDuration pads seconds with leading zero`() {
        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.OUTBOUND,
            duration = 9
        )
        assertEquals("0:09", record.formattedDuration)
    }

    // =========================================================================
    // Missed call detection
    // =========================================================================

    @Test
    fun `isMissed is true for inbound with zero duration`() {
        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.INBOUND,
            duration = 0
        )
        assertTrue(record.isMissed)
    }

    @Test
    fun `isMissed is false for inbound with non-zero duration`() {
        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.INBOUND,
            duration = 30
        )
        assertFalse(record.isMissed)
    }

    @Test
    fun `isMissed is false for outbound with zero duration`() {
        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.OUTBOUND,
            duration = 0
        )
        assertFalse(record.isMissed)
    }

    @Test
    fun `isMissed is false for outbound with non-zero duration`() {
        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.OUTBOUND,
            duration = 60
        )
        assertFalse(record.isMissed)
    }

    // =========================================================================
    // Call subtitle
    // =========================================================================

    @Test
    fun `callSubtitle for missed call`() {
        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.INBOUND,
            duration = 0
        )
        assertEquals("Missed Call", record.callSubtitle)
    }

    @Test
    fun `callSubtitle for outgoing call with duration`() {
        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.OUTBOUND,
            duration = 90
        )
        assertEquals("Outgoing \u00B7 1:30", record.callSubtitle)
    }

    @Test
    fun `callSubtitle for incoming call with duration`() {
        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.INBOUND,
            duration = 30
        )
        assertEquals("Incoming \u00B7 0:30", record.callSubtitle)
    }

    @Test
    fun `callSubtitle for outgoing call with zero duration`() {
        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.OUTBOUND,
            duration = 0
        )
        assertEquals("Outgoing", record.callSubtitle)
    }

    @Test
    fun `callSubtitle for incoming call with non-zero duration is not missed`() {
        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.INBOUND,
            duration = 1
        )
        assertEquals("Incoming \u00B7 0:01", record.callSubtitle)
    }

    // =========================================================================
    // Date formatting
    // =========================================================================

    @Test
    fun `formattedDate for today shows time`() {
        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.OUTBOUND,
            timestamp = Date()
        )
        // Should be in format like "2:30 PM"
        val formatted = record.formattedDate
        assertTrue("Today's date should show time format (contains AM or PM)",
            formatted.contains("AM") || formatted.contains("PM"))
    }

    @Test
    fun `formattedDate for yesterday shows Yesterday`() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)

        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.OUTBOUND,
            timestamp = cal.time
        )
        assertEquals("Yesterday", record.formattedDate)
    }

    @Test
    fun `formattedDate for 3 days ago shows day name`() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -3)

        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.OUTBOUND,
            timestamp = cal.time
        )
        val dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
        assertEquals(dayName, record.formattedDate)
    }

    @Test
    fun `formattedDate for 5 days ago shows day name`() {
        // Note: isWithinLastWeek checks if date is after (now - 6 days).
        // At exactly -6 days, the boundary may not be "after" due to time-of-day,
        // so we use -5 to be safely within the last week.
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -5)

        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.OUTBOUND,
            timestamp = cal.time
        )
        val dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
        assertEquals(dayName, record.formattedDate)
    }

    @Test
    fun `formattedDate for more than a week ago shows full date`() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -10)

        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.OUTBOUND,
            timestamp = cal.time
        )
        val expected = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(cal.time)
        assertEquals(expected, record.formattedDate)
    }

    @Test
    fun `formattedDate for date in previous year`() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.YEAR, -1)

        val record = CallDetailRecord(
            phoneNumber = "5551234567",
            direction = CallDirection.OUTBOUND,
            timestamp = cal.time
        )
        val expected = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(cal.time)
        assertEquals(expected, record.formattedDate)
    }

    // =========================================================================
    // Data class behavior
    // =========================================================================

    @Test
    fun `unique id is generated for each record`() {
        val record1 = CallDetailRecord(phoneNumber = "5551234567", direction = CallDirection.OUTBOUND)
        val record2 = CallDetailRecord(phoneNumber = "5551234567", direction = CallDirection.OUTBOUND)
        assertNotEquals("Each record should have a unique ID", record1.id, record2.id)
    }

    @Test
    fun `copy preserves original id`() {
        val original = CallDetailRecord(phoneNumber = "5551234567", direction = CallDirection.OUTBOUND)
        val copy = original.copy(duration = 100)
        assertEquals(original.id, copy.id)
        assertEquals(100L, copy.duration)
    }

    @Test
    fun `copy with new phone number`() {
        val original = CallDetailRecord(phoneNumber = "5551234567", direction = CallDirection.OUTBOUND)
        val copy = original.copy(phoneNumber = "5559876543")
        assertEquals("5559876543", copy.phoneNumber)
        assertEquals(original.direction, copy.direction)
    }

    @Test
    fun `custom id can be set`() {
        val customId = UUID.randomUUID()
        val record = CallDetailRecord(
            id = customId,
            phoneNumber = "5551234567",
            direction = CallDirection.OUTBOUND
        )
        assertEquals(customId, record.id)
    }

    @Test
    fun `records with same id and data are equal`() {
        val id = UUID.randomUUID()
        val timestamp = Date()
        val record1 = CallDetailRecord(id = id, phoneNumber = "555", direction = CallDirection.INBOUND, timestamp = timestamp)
        val record2 = CallDetailRecord(id = id, phoneNumber = "555", direction = CallDirection.INBOUND, timestamp = timestamp)
        assertEquals(record1, record2)
    }

    @Test
    fun `duration can be mutated`() {
        val record = CallDetailRecord(phoneNumber = "5551234567", direction = CallDirection.OUTBOUND)
        assertEquals(0L, record.duration)
        record.duration = 120
        assertEquals(120L, record.duration)
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    @Test
    fun `phone number with spaces and special characters`() {
        val result = CallDetailRecord.formatPhoneNumber("+1 (555) 123-4567")
        assertEquals("+1 (555) 123-4567", result)
    }

    @Test
    fun `phone number with dots`() {
        val result = CallDetailRecord.formatPhoneNumber("555.123.4567")
        assertEquals("(555) 123-4567", result)
    }

    @Test
    fun `very long phone number returns as-is`() {
        val longNumber = "123456789012345"
        val result = CallDetailRecord.formatPhoneNumber(longNumber)
        assertEquals(longNumber, result)
    }

    @Test
    fun `single digit number returns as-is`() {
        assertEquals("5", CallDetailRecord.formatPhoneNumber("5"))
    }

    @Test
    fun `nine digit number returns as-is`() {
        assertEquals("123456789", CallDetailRecord.formatPhoneNumber("123456789"))
    }
}
