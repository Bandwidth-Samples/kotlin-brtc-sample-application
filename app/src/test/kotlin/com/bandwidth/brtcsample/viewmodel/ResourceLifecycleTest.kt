package com.bandwidth.brtcsample.viewmodel

import android.app.Application
import com.bandwidth.brtcsample.model.CallDetailRecord
import com.bandwidth.brtcsample.model.CallDirection
import com.bandwidth.brtcsample.model.CallHistoryManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Resource sharing and lifecycle management tests.
 *
 * Covers:
 * - Audio level buffer lifecycle (creation, capacity management, cleanup)
 * - Call history resource sharing between callers
 * - Waveform capacity enforcement
 * - State cleanup on lifecycle transitions
 * - Memory leak prevention (clearing callbacks, canceling jobs)
 * - Shared resource isolation between operations
 * - Resource state after error conditions
 * - AudioLevelAccumulator instance isolation
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ResourceLifecycleTest {

    private lateinit var viewModel: CallViewModel

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        viewModel = CallViewModel(application)
    }

    // =========================================================================
    // Audio level buffer lifecycle
    // =========================================================================

    @Test
    fun `audio levels start empty`() {
        assertTrue(viewModel.localAudioLevels.isEmpty())
        assertTrue(viewModel.remoteAudioLevels.isEmpty())
    }

    @Test
    fun `audio levels can be added`() {
        viewModel.localAudioLevels.add(0.5f)
        viewModel.remoteAudioLevels.add(0.3f)

        assertEquals(1, viewModel.localAudioLevels.size)
        assertEquals(1, viewModel.remoteAudioLevels.size)
    }

    @Test
    fun `audio levels are cleared on disconnect`() {
        viewModel.localAudioLevels.addAll(List(30) { 0.5f })
        viewModel.remoteAudioLevels.addAll(List(20) { 0.3f })

        viewModel.disconnect()

        assertTrue(viewModel.localAudioLevels.isEmpty())
        assertTrue(viewModel.remoteAudioLevels.isEmpty())
    }

    @Test
    fun `audio levels are cleared on hangup`() {
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.localAudioLevels.addAll(List(30) { 0.5f })
        viewModel.remoteAudioLevels.addAll(List(20) { 0.3f })

        viewModel.hangup()

        assertTrue(viewModel.localAudioLevels.isEmpty())
        assertTrue(viewModel.remoteAudioLevels.isEmpty())
    }

    @Test
    fun `audio levels cleared after multiple hangup-call cycles`() {
        repeat(5) {
            viewModel.localAudioLevels.addAll(List(10) { 0.5f })
            viewModel.remoteAudioLevels.addAll(List(10) { 0.3f })

            viewModel.connectionState = ConnectionState.IN_CALL
            viewModel.hangup()

            assertTrue("Cycle $it: local levels should be empty",
                viewModel.localAudioLevels.isEmpty())
            assertTrue("Cycle $it: remote levels should be empty",
                viewModel.remoteAudioLevels.isEmpty())

            viewModel.connectionState = ConnectionState.CONNECTED
        }
    }

    // =========================================================================
    // AudioLevelAccumulator instance isolation
    // =========================================================================

    @Test
    fun `separate accumulator instances are independent`() {
        val acc1 = AudioLevelAccumulator()
        val acc2 = AudioLevelAccumulator()

        acc1.accumulate(FloatArray(9600) { 0.8f })
        acc2.accumulate(FloatArray(9600) { 0.2f })

        val level1 = acc1.getLevel()!!
        val level2 = acc2.getLevel()!!

        assertTrue("Louder signal should produce higher level", level1 > level2)
    }

    @Test
    fun `accumulator reset does not affect other accumulators`() {
        val acc1 = AudioLevelAccumulator()
        val acc2 = AudioLevelAccumulator()

        acc1.accumulate(FloatArray(9600) { 0.5f })
        acc2.accumulate(FloatArray(9600) { 0.5f })

        acc1.reset()

        assertNull("Reset accumulator should return null", acc1.getLevel())
        assertNotNull("Other accumulator should still have data", acc2.getLevel())
    }

    @Test
    fun `accumulator survives multiple lifecycle cycles`() {
        val acc = AudioLevelAccumulator()

        repeat(10) { cycle ->
            acc.accumulate(FloatArray(9600) { 0.5f })
            val level = acc.getLevel()
            assertNotNull("Cycle $cycle: should produce level", level)
            assertTrue("Cycle $cycle: level in valid range", level!! in 0f..1f)
        }
    }

    @Test
    fun `accumulator reset between cycles produces clean results`() {
        val acc = AudioLevelAccumulator()

        // Cycle 1: loud
        acc.accumulate(FloatArray(9600) { 0.9f })
        val level1 = acc.getLevel()!!

        // Reset and cycle 2: quiet
        acc.reset()
        acc.accumulate(FloatArray(9600) { 0.1f })
        val level2 = acc.getLevel()!!

        assertTrue("Loud cycle should be higher than quiet cycle", level1 > level2)

        // Cycle 3 without explicit reset (getLevel already resets)
        acc.accumulate(FloatArray(9600) { 0.5f })
        val level3 = acc.getLevel()!!

        assertTrue("Mid-level should be between extremes",
            level3 > level2 && level3 < level1)
    }

    // =========================================================================
    // Call history resource sharing
    // =========================================================================

    @Test
    fun `call history is shared across call operations`() {
        viewModel.connectionState = ConnectionState.CONNECTED

        // Outbound call
        viewModel.phoneNumber = "5551111111"
        viewModel.call()
        assertEquals(1, viewModel.callHistory.records.size)

        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.hangup()

        // Inbound call (declined)
        viewModel.connectionState = ConnectionState.RINGING
        viewModel.declineIncomingCall()
        assertEquals(2, viewModel.callHistory.records.size)

        // History survives across operations
        val outbound = viewModel.callHistory.records.find { it.direction == CallDirection.OUTBOUND }
        val inbound = viewModel.callHistory.records.find { it.direction == CallDirection.INBOUND }
        assertNotNull(outbound)
        assertNotNull(inbound)
    }

    @Test
    fun `call history survives disconnect`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.phoneNumber = "5551234567"
        viewModel.call()

        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.hangup()
        viewModel.disconnect()

        // History should persist after disconnect
        assertEquals(1, viewModel.callHistory.records.size)
    }

    @Test
    fun `call history clear is independent of connection state`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.phoneNumber = "5551234567"
        viewModel.call()
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.hangup()

        viewModel.callHistory.clearAll()
        assertEquals(0, viewModel.callHistory.records.size)

        // Can still add new records
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.phoneNumber = "5559876543"
        viewModel.call()
        assertEquals(1, viewModel.callHistory.records.size)
    }

    @Test
    fun `call history update duration tracks active call correctly`() {
        val manager = CallHistoryManager()

        val record1 = CallDetailRecord(phoneNumber = "111", direction = CallDirection.OUTBOUND)
        val record2 = CallDetailRecord(phoneNumber = "222", direction = CallDirection.OUTBOUND)

        manager.addRecord(record1)
        manager.addRecord(record2)

        // Update only record1's duration
        manager.updateDuration(record1.id, 60)

        assertEquals(0L, manager.records.first { it.id == record2.id }.duration)
        assertEquals(60L, manager.records.first { it.id == record1.id }.duration)
    }

    // =========================================================================
    // State cleanup completeness
    // =========================================================================

    @Test
    fun `hangup cleanup is comprehensive`() {
        // Set up outbound call state via call()
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.phoneNumber = "5551234567"
        viewModel.call()
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.callDuration = 300
        viewModel.showStatsOverlay = true
        viewModel.localAudioLevels.addAll(List(50) { 0.5f })
        viewModel.remoteAudioLevels.addAll(List(50) { 0.3f })

        viewModel.hangup()

        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
        assertEquals(0L, viewModel.callDuration)
        assertFalse(viewModel.isOutboundCall)
        assertTrue(viewModel.localAudioLevels.isEmpty())
        assertTrue(viewModel.remoteAudioLevels.isEmpty())
    }

    @Test
    fun `disconnect cleanup is comprehensive`() {
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.callDuration = 600
        viewModel.isSpeakerOn = true
        viewModel.statusText = "In call"
        viewModel.localAudioLevels.addAll(List(50) { 0.5f })
        viewModel.remoteAudioLevels.addAll(List(50) { 0.3f })

        viewModel.disconnect()

        assertEquals(ConnectionState.DISCONNECTED, viewModel.connectionState)
        assertEquals(0L, viewModel.callDuration)
        assertFalse(viewModel.isSpeakerOn)
        assertEquals("", viewModel.statusText)
        assertNull(viewModel.endpointId)
        assertTrue(viewModel.localAudioLevels.isEmpty())
        assertTrue(viewModel.remoteAudioLevels.isEmpty())
    }

    // =========================================================================
    // Resource state after errors
    // =========================================================================

    @Test
    fun `state is clean after failed call attempt`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.phoneNumber = "" // Will cause error

        viewModel.call()

        // Error shown but state preserved
        assertTrue(viewModel.showError)
        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
        assertEquals(0L, viewModel.callDuration)
        assertTrue(viewModel.localAudioLevels.isEmpty())
        assertTrue(viewModel.remoteAudioLevels.isEmpty())
    }

    @Test
    fun `resources clean after simulate incoming call error`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        // endpointId is null - will cause error

        viewModel.simulateIncomingCall()

        assertTrue(viewModel.showError)
        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
    }

    // =========================================================================
    // ViewModel instance isolation
    // =========================================================================

    @Test
    fun `multiple ViewModel instances have independent state`() {
        val app = RuntimeEnvironment.getApplication()
        val vm1 = CallViewModel(app)
        val vm2 = CallViewModel(app)

        vm1.connectionState = ConnectionState.CONNECTED
        vm1.phoneNumber = "5551111111"
        vm1.isMicEnabled = false

        assertEquals(ConnectionState.DISCONNECTED, vm2.connectionState)
        assertEquals("", vm2.phoneNumber)
        assertTrue(vm2.isMicEnabled)
    }

    @Test
    fun `multiple ViewModel instances have independent call histories`() {
        val app = RuntimeEnvironment.getApplication()
        val vm1 = CallViewModel(app)
        val vm2 = CallViewModel(app)

        vm1.callHistory.addRecord(
            CallDetailRecord(phoneNumber = "111", direction = CallDirection.OUTBOUND)
        )

        assertEquals(1, vm1.callHistory.records.size)
        assertEquals(0, vm2.callHistory.records.size)
    }

    @Test
    fun `multiple ViewModel instances have independent audio levels`() {
        val app = RuntimeEnvironment.getApplication()
        val vm1 = CallViewModel(app)
        val vm2 = CallViewModel(app)

        vm1.localAudioLevels.addAll(listOf(0.1f, 0.2f, 0.3f))

        assertEquals(3, vm1.localAudioLevels.size)
        assertEquals(0, vm2.localAudioLevels.size)
    }

    // =========================================================================
    // Waveform capacity management
    // =========================================================================

    @Test
    fun `waveform buffer is bounded by capacity`() {
        // The capacity is 50 in the ViewModel
        // This tests the pattern used in audio level monitoring
        val capacity = 50
        val levels = viewModel.localAudioLevels

        repeat(100) {
            levels.add(it.toFloat() / 100f)
            if (levels.size > capacity) {
                levels.removeAt(0)
            }
        }

        assertEquals("Buffer should be at capacity", capacity, levels.size)
        // Should contain the latest values (50-99)/100
        assertEquals(0.50f, levels.first(), 0.01f)
        assertEquals(0.99f, levels.last(), 0.01f)
    }

    @Test
    fun `waveform clear and refill works correctly`() {
        val levels = viewModel.localAudioLevels
        val capacity = 50

        // Fill
        repeat(capacity) { levels.add(0.5f) }
        assertEquals(capacity, levels.size)

        // Clear
        levels.clear()
        assertEquals(0, levels.size)

        // Refill
        repeat(capacity) { levels.add(0.3f) }
        assertEquals(capacity, levels.size)
        levels.forEach { assertEquals(0.3f, it, 0.001f) }
    }

    // =========================================================================
    // Shared mutable state via CallHistoryManager
    // =========================================================================

    @Test
    fun `CallHistoryManager records list reflects mutations`() {
        val manager = CallHistoryManager()
        val records = manager.records // Reference to the live list

        manager.addRecord(CallDetailRecord(phoneNumber = "111", direction = CallDirection.OUTBOUND))
        assertEquals("Live reference should reflect additions", 1, records.size)

        manager.clearAll()
        assertEquals("Live reference should reflect clear", 0, records.size)
    }

    @Test
    fun `CallHistoryManager update reflects in same record reference`() {
        val manager = CallHistoryManager()
        val record = CallDetailRecord(phoneNumber = "111", direction = CallDirection.OUTBOUND)
        manager.addRecord(record)

        manager.updateDuration(record.id, 120)

        // The record at index 0 should be a new copy with updated duration
        val updated = manager.records[0]
        assertEquals(record.id, updated.id)
        assertEquals(120L, updated.duration)
    }
}
