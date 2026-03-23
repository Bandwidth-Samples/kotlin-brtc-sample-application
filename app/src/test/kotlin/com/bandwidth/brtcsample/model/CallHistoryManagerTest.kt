package com.bandwidth.brtcsample.model

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Comprehensive tests for CallHistoryManager.
 *
 * Covers:
 * - Adding records (ordering, duplicates)
 * - Updating duration
 * - Clearing records
 * - Edge cases (non-existent IDs, empty state)
 * - Concurrent-like access patterns
 * - Resource management
 */
class CallHistoryManagerTest {

    private lateinit var manager: CallHistoryManager

    @Before
    fun setUp() {
        manager = CallHistoryManager()
    }

    // =========================================================================
    // Adding records
    // =========================================================================

    @Test
    fun `addRecord adds to front of list`() {
        val record1 = CallDetailRecord(phoneNumber = "111", direction = CallDirection.OUTBOUND)
        val record2 = CallDetailRecord(phoneNumber = "222", direction = CallDirection.OUTBOUND)

        manager.addRecord(record1)
        manager.addRecord(record2)

        assertEquals("Most recent record should be at index 0", record2, manager.records[0])
        assertEquals("Older record should be at index 1", record1, manager.records[1])
    }

    @Test
    fun `addRecord increases list size`() {
        assertEquals(0, manager.records.size)

        manager.addRecord(CallDetailRecord(phoneNumber = "111", direction = CallDirection.OUTBOUND))
        assertEquals(1, manager.records.size)

        manager.addRecord(CallDetailRecord(phoneNumber = "222", direction = CallDirection.OUTBOUND))
        assertEquals(2, manager.records.size)
    }

    @Test
    fun `multiple records maintain insertion order`() {
        val records = (1..10).map {
            CallDetailRecord(phoneNumber = "$it", direction = CallDirection.OUTBOUND)
        }

        records.forEach { manager.addRecord(it) }

        assertEquals(10, manager.records.size)
        // Records should be in reverse order (newest first)
        for (i in records.indices) {
            assertEquals(records[records.size - 1 - i], manager.records[i])
        }
    }

    @Test
    fun `addRecord with inbound direction`() {
        val record = CallDetailRecord(phoneNumber = "555", direction = CallDirection.INBOUND)
        manager.addRecord(record)
        assertEquals(CallDirection.INBOUND, manager.records[0].direction)
    }

    @Test
    fun `addRecord preserves all record fields`() {
        val id = UUID.randomUUID()
        val record = CallDetailRecord(
            id = id,
            phoneNumber = "+15551234567",
            direction = CallDirection.INBOUND,
            duration = 42
        )
        manager.addRecord(record)

        val stored = manager.records[0]
        assertEquals(id, stored.id)
        assertEquals("+15551234567", stored.phoneNumber)
        assertEquals(CallDirection.INBOUND, stored.direction)
        assertEquals(42L, stored.duration)
    }

    // =========================================================================
    // Updating duration
    // =========================================================================

    @Test
    fun `updateDuration updates correct record`() {
        val record = CallDetailRecord(phoneNumber = "555", direction = CallDirection.OUTBOUND)
        manager.addRecord(record)

        manager.updateDuration(record.id, 120)
        assertEquals(120L, manager.records[0].duration)
    }

    @Test
    fun `updateDuration does not affect other records`() {
        val record1 = CallDetailRecord(phoneNumber = "111", direction = CallDirection.OUTBOUND)
        val record2 = CallDetailRecord(phoneNumber = "222", direction = CallDirection.OUTBOUND)
        manager.addRecord(record1)
        manager.addRecord(record2)

        manager.updateDuration(record1.id, 60)

        assertEquals(0L, manager.records[0].duration) // record2
        assertEquals(60L, manager.records[1].duration) // record1
    }

    @Test
    fun `updateDuration with non-existent id does nothing`() {
        val record = CallDetailRecord(phoneNumber = "555", direction = CallDirection.OUTBOUND)
        manager.addRecord(record)

        manager.updateDuration(UUID.randomUUID(), 999)
        assertEquals(0L, manager.records[0].duration)
    }

    @Test
    fun `updateDuration on empty list does nothing`() {
        // Should not throw
        manager.updateDuration(UUID.randomUUID(), 60)
        assertEquals(0, manager.records.size)
    }

    @Test
    fun `updateDuration updates to zero`() {
        val record = CallDetailRecord(phoneNumber = "555", direction = CallDirection.OUTBOUND, duration = 100)
        manager.addRecord(record)

        manager.updateDuration(record.id, 0)
        assertEquals(0L, manager.records[0].duration)
    }

    @Test
    fun `updateDuration with large value`() {
        val record = CallDetailRecord(phoneNumber = "555", direction = CallDirection.OUTBOUND)
        manager.addRecord(record)

        manager.updateDuration(record.id, Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, manager.records[0].duration)
    }

    @Test
    fun `updateDuration can be called multiple times`() {
        val record = CallDetailRecord(phoneNumber = "555", direction = CallDirection.OUTBOUND)
        manager.addRecord(record)

        manager.updateDuration(record.id, 10)
        assertEquals(10L, manager.records[0].duration)

        manager.updateDuration(record.id, 20)
        assertEquals(20L, manager.records[0].duration)

        manager.updateDuration(record.id, 30)
        assertEquals(30L, manager.records[0].duration)
    }

    @Test
    fun `updateDuration creates new record instance via copy`() {
        val record = CallDetailRecord(phoneNumber = "555", direction = CallDirection.OUTBOUND)
        manager.addRecord(record)

        val beforeUpdate = manager.records[0]
        manager.updateDuration(record.id, 60)
        val afterUpdate = manager.records[0]

        // The record should be a new copy with the same id
        assertEquals(beforeUpdate.id, afterUpdate.id)
        assertEquals(beforeUpdate.phoneNumber, afterUpdate.phoneNumber)
        assertNotEquals(beforeUpdate.duration, afterUpdate.duration)
    }

    // =========================================================================
    // Clearing records
    // =========================================================================

    @Test
    fun `clearAll removes all records`() {
        manager.addRecord(CallDetailRecord(phoneNumber = "111", direction = CallDirection.OUTBOUND))
        manager.addRecord(CallDetailRecord(phoneNumber = "222", direction = CallDirection.OUTBOUND))
        manager.addRecord(CallDetailRecord(phoneNumber = "333", direction = CallDirection.OUTBOUND))

        manager.clearAll()
        assertEquals(0, manager.records.size)
    }

    @Test
    fun `clearAll on empty list is safe`() {
        manager.clearAll()
        assertEquals(0, manager.records.size)
    }

    @Test
    fun `clearAll followed by add works correctly`() {
        manager.addRecord(CallDetailRecord(phoneNumber = "111", direction = CallDirection.OUTBOUND))
        manager.clearAll()

        val newRecord = CallDetailRecord(phoneNumber = "222", direction = CallDirection.OUTBOUND)
        manager.addRecord(newRecord)

        assertEquals(1, manager.records.size)
        assertEquals("222", manager.records[0].phoneNumber)
    }

    @Test
    fun `double clearAll is safe`() {
        manager.addRecord(CallDetailRecord(phoneNumber = "111", direction = CallDirection.OUTBOUND))
        manager.clearAll()
        manager.clearAll()
        assertEquals(0, manager.records.size)
    }

    // =========================================================================
    // Large dataset tests
    // =========================================================================

    @Test
    fun `handles large number of records`() {
        val count = 1000
        repeat(count) {
            manager.addRecord(CallDetailRecord(phoneNumber = "$it", direction = CallDirection.OUTBOUND))
        }
        assertEquals(count, manager.records.size)
    }

    @Test
    fun `updateDuration finds record in large list`() {
        val records = (1..100).map {
            CallDetailRecord(phoneNumber = "$it", direction = CallDirection.OUTBOUND)
        }
        records.forEach { manager.addRecord(it) }

        val targetRecord = records[50]
        manager.updateDuration(targetRecord.id, 999)

        val found = manager.records.first { it.id == targetRecord.id }
        assertEquals(999L, found.duration)
    }

    // =========================================================================
    // Order of operations tests
    // =========================================================================

    @Test
    fun `add then update then clear leaves empty state`() {
        val record = CallDetailRecord(phoneNumber = "555", direction = CallDirection.OUTBOUND)
        manager.addRecord(record)
        manager.updateDuration(record.id, 60)
        manager.clearAll()

        assertEquals(0, manager.records.size)
    }

    @Test
    fun `interleaved adds and updates maintain consistency`() {
        val record1 = CallDetailRecord(phoneNumber = "111", direction = CallDirection.OUTBOUND)
        manager.addRecord(record1)

        val record2 = CallDetailRecord(phoneNumber = "222", direction = CallDirection.OUTBOUND)
        manager.addRecord(record2)

        manager.updateDuration(record1.id, 60)

        val record3 = CallDetailRecord(phoneNumber = "333", direction = CallDirection.OUTBOUND)
        manager.addRecord(record3)

        manager.updateDuration(record2.id, 120)

        assertEquals(3, manager.records.size)
        assertEquals(120L, manager.records.first { it.id == record2.id }.duration)
        assertEquals(60L, manager.records.first { it.id == record1.id }.duration)
        assertEquals(0L, manager.records.first { it.id == record3.id }.duration)
    }

    // =========================================================================
    // Filtering behavior (testing with isMissed)
    // =========================================================================

    @Test
    fun `can filter missed calls from records`() {
        manager.addRecord(CallDetailRecord(phoneNumber = "111", direction = CallDirection.INBOUND, duration = 0))
        manager.addRecord(CallDetailRecord(phoneNumber = "222", direction = CallDirection.OUTBOUND, duration = 0))
        manager.addRecord(CallDetailRecord(phoneNumber = "333", direction = CallDirection.INBOUND, duration = 30))
        manager.addRecord(CallDetailRecord(phoneNumber = "444", direction = CallDirection.INBOUND, duration = 0))

        val missed = manager.records.filter { it.isMissed }
        assertEquals(2, missed.size)
        assertTrue(missed.all { it.direction == CallDirection.INBOUND && it.duration == 0L })
    }
}
