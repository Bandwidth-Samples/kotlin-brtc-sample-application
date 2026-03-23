package com.bandwidth.brtcsample.viewmodel

import android.app.Application
import com.bandwidth.brtcsample.model.CallDirection
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Order of operations and determinism tests for CallViewModel.
 *
 * Ensures that the SDK behaves deterministically regardless of how operations are sequenced.
 * Tests verify that:
 * - State machine transitions follow valid paths
 * - Invalid transition sequences are handled gracefully
 * - Cleanup operations are idempotent
 * - Resource state is consistent after any sequence of operations
 * - Double-action scenarios (double hangup, double disconnect) are safe
 * - Interleaved call/hangup sequences maintain valid state
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class CallViewModelOrderOfOperationsTest {

    private lateinit var viewModel: CallViewModel

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        viewModel = CallViewModel(application)
    }

    // =========================================================================
    // Valid state transition sequences
    // =========================================================================

    @Test
    fun `DISCONNECTED to CONNECTING via connect`() {
        assertEquals(ConnectionState.DISCONNECTED, viewModel.connectionState)
        viewModel.connect()
        assertEquals("connect() should transition to CONNECTING",
            ConnectionState.CONNECTING, viewModel.connectionState)
    }

    @Test
    fun `hangup always results in CONNECTED state`() {
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.hangup()
        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
    }

    @Test
    fun `disconnect always results in DISCONNECTED state`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, viewModel.connectionState)
    }

    @Test
    fun `full outbound call lifecycle`() {
        // Start disconnected
        assertEquals(ConnectionState.DISCONNECTED, viewModel.connectionState)

        // Connect (initiates async, transitions to CONNECTING)
        viewModel.connect()
        assertEquals(ConnectionState.CONNECTING, viewModel.connectionState)

        // Simulate connection complete
        viewModel.connectionState = ConnectionState.CONNECTED
        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)

        // Place call
        viewModel.phoneNumber = "5551234567"
        viewModel.call()
        assertEquals(ConnectionState.RINGING, viewModel.connectionState)
        assertTrue(viewModel.isOutboundCall)
        assertEquals(1, viewModel.callHistory.records.size)

        // Simulate call answered
        viewModel.connectionState = ConnectionState.IN_CALL

        // Hangup
        viewModel.hangup()
        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
        assertFalse(viewModel.isOutboundCall)
        assertEquals(0L, viewModel.callDuration)

        // Disconnect
        viewModel.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, viewModel.connectionState)
    }

    @Test
    fun `full inbound call lifecycle - accepted`() {
        viewModel.connectionState = ConnectionState.CONNECTED

        // Incoming call
        viewModel.connectionState = ConnectionState.RINGING

        // Accept
        viewModel.acceptIncomingCall()
        assertEquals(ConnectionState.IN_CALL, viewModel.connectionState)
        assertEquals(1, viewModel.callHistory.records.size)
        assertEquals(CallDirection.INBOUND, viewModel.callHistory.records[0].direction)

        // Hangup
        viewModel.hangup()
        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
    }

    @Test
    fun `full inbound call lifecycle - declined`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.connectionState = ConnectionState.RINGING

        viewModel.declineIncomingCall()

        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
        assertEquals(1, viewModel.callHistory.records.size)
        assertTrue(viewModel.callHistory.records[0].isMissed)
    }

    // =========================================================================
    // Invalid operation sequences (graceful handling)
    // =========================================================================

    @Test
    fun `call before connect does not crash`() {
        viewModel.phoneNumber = "5551234567"
        // Call while DISCONNECTED - should set to RINGING (no guard for this)
        viewModel.connectionState = ConnectionState.DISCONNECTED
        // call() doesn't check for CONNECTED state, only empty phone number
        viewModel.call()
        // Should not crash, state machine should handle it
    }

    @Test
    fun `hangup when DISCONNECTED does not crash`() {
        viewModel.connectionState = ConnectionState.DISCONNECTED
        viewModel.hangup()
        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
    }

    @Test
    fun `disconnect when already DISCONNECTED is idempotent`() {
        viewModel.connectionState = ConnectionState.DISCONNECTED
        viewModel.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, viewModel.connectionState)
    }

    @Test
    fun `connect during active call does not disrupt`() {
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.connect() // Should be guarded (only connects from DISCONNECTED)
        assertEquals("IN_CALL state should be preserved",
            ConnectionState.IN_CALL, viewModel.connectionState)
    }

    @Test
    fun `connect during RINGING does not disrupt`() {
        viewModel.connectionState = ConnectionState.RINGING
        viewModel.connect()
        assertEquals(ConnectionState.RINGING, viewModel.connectionState)
    }

    @Test
    fun `accept when not RINGING is no-op`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        val historyBefore = viewModel.callHistory.records.size
        viewModel.acceptIncomingCall()
        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
        assertEquals(historyBefore, viewModel.callHistory.records.size)
    }

    @Test
    fun `decline when DISCONNECTED does nothing`() {
        viewModel.connectionState = ConnectionState.DISCONNECTED
        viewModel.declineIncomingCall()
        // declineIncomingCall checks for RINGING and IN_CALL
        assertEquals(ConnectionState.DISCONNECTED, viewModel.connectionState)
    }

    // =========================================================================
    // Double action idempotency
    // =========================================================================

    @Test
    fun `double hangup is idempotent on state`() {
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.phoneNumber = "5551234567"

        viewModel.hangup()
        val stateAfterFirst = viewModel.connectionState
        val durationAfterFirst = viewModel.callDuration

        viewModel.hangup()
        assertEquals(stateAfterFirst, viewModel.connectionState)
        assertEquals(durationAfterFirst, viewModel.callDuration)
    }

    @Test
    fun `double disconnect is idempotent on state`() {
        viewModel.connectionState = ConnectionState.CONNECTED

        viewModel.disconnect()
        val stateAfterFirst = viewModel.connectionState

        viewModel.disconnect()
        assertEquals(stateAfterFirst, viewModel.connectionState)
    }

    @Test
    fun `double accept is safe`() {
        viewModel.connectionState = ConnectionState.RINGING
        viewModel.acceptIncomingCall()

        // Second accept should be no-op since state is now IN_CALL
        viewModel.acceptIncomingCall()
        assertEquals(ConnectionState.IN_CALL, viewModel.connectionState)
    }

    @Test
    fun `double decline is safe`() {
        viewModel.connectionState = ConnectionState.RINGING
        viewModel.declineIncomingCall()

        // Second decline with CONNECTED state should be no-op
        viewModel.declineIncomingCall()
        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
    }

    @Test
    fun `double connect from DISCONNECTED only transitions once`() {
        viewModel.connectionState = ConnectionState.DISCONNECTED
        viewModel.connect()
        assertEquals(ConnectionState.CONNECTING, viewModel.connectionState)

        // Second connect should be guarded (state is now CONNECTING)
        viewModel.connect()
        assertEquals(ConnectionState.CONNECTING, viewModel.connectionState)
    }

    // =========================================================================
    // Resource cleanup ordering
    // =========================================================================

    @Test
    fun `hangup clears duration before state change`() {
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.callDuration = 300

        viewModel.hangup()

        assertEquals(0L, viewModel.callDuration)
        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
    }

    @Test
    fun `disconnect clears all resources`() {
        // Set up state as if in a call
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.callDuration = 600
        viewModel.isSpeakerOn = true
        viewModel.statusText = "In call"
        viewModel.localAudioLevels.addAll(listOf(0.1f, 0.2f, 0.3f))
        viewModel.remoteAudioLevels.addAll(listOf(0.4f, 0.5f))

        viewModel.disconnect()

        assertEquals(ConnectionState.DISCONNECTED, viewModel.connectionState)
        assertEquals(0L, viewModel.callDuration)
        assertFalse(viewModel.isSpeakerOn)
        assertEquals("", viewModel.statusText)
        assertNull(viewModel.endpointId)
        assertTrue(viewModel.localAudioLevels.isEmpty())
        assertTrue(viewModel.remoteAudioLevels.isEmpty())
    }

    @Test
    fun `hangup followed by disconnect clears everything`() {
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.callDuration = 120
        viewModel.phoneNumber = "5551234567"
        viewModel.isSpeakerOn = true
        viewModel.statusText = "In call"

        viewModel.hangup()
        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
        assertEquals(0L, viewModel.callDuration)
        assertEquals("Connected", viewModel.statusText)

        viewModel.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, viewModel.connectionState)
        assertEquals("", viewModel.statusText)
    }

    // =========================================================================
    // Interleaved call sequences
    // =========================================================================

    @Test
    fun `call then immediate hangup before answer`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.phoneNumber = "5551234567"

        viewModel.call()
        assertEquals(ConnectionState.RINGING, viewModel.connectionState)

        viewModel.hangup()
        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
    }

    @Test
    fun `multiple call-hangup cycles maintain consistent state`() {
        repeat(10) { iteration ->
            viewModel.connectionState = ConnectionState.CONNECTED
            viewModel.phoneNumber = "555${iteration}"

            viewModel.call()
            assertEquals("Iteration $iteration: Should be RINGING after call",
                ConnectionState.RINGING, viewModel.connectionState)

            viewModel.connectionState = ConnectionState.IN_CALL
            viewModel.hangup()
            assertEquals("Iteration $iteration: Should be CONNECTED after hangup",
                ConnectionState.CONNECTED, viewModel.connectionState)
            assertEquals(0L, viewModel.callDuration)
        }
    }

    @Test
    fun `alternating outbound and inbound calls`() {
        viewModel.connectionState = ConnectionState.CONNECTED

        // Outbound call
        viewModel.phoneNumber = "5551111111"
        viewModel.call()
        assertTrue(viewModel.isOutboundCall)
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.hangup()
        assertFalse(viewModel.isOutboundCall)

        // Inbound call
        viewModel.connectionState = ConnectionState.RINGING
        viewModel.acceptIncomingCall()
        assertEquals(ConnectionState.IN_CALL, viewModel.connectionState)
        viewModel.hangup()
        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)

        // History should have entries for both
        assertTrue(viewModel.callHistory.records.size >= 2)
    }

    // =========================================================================
    // Error state management ordering
    // =========================================================================

    @Test
    fun `error does not affect connection state`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.phoneNumber = ""

        viewModel.call() // Should trigger error

        assertTrue(viewModel.showError)
        assertEquals("Enter a phone number", viewModel.errorMessage)
        assertEquals("Error should not change connection state",
            ConnectionState.CONNECTED, viewModel.connectionState)
    }

    @Test
    fun `error can be cleared and call retried`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.phoneNumber = ""
        viewModel.call()

        assertTrue(viewModel.showError)

        // Clear error
        viewModel.showError = false
        viewModel.errorMessage = ""

        // Retry with valid number
        viewModel.phoneNumber = "5551234567"
        viewModel.call()

        assertEquals(ConnectionState.RINGING, viewModel.connectionState)
    }

    @Test
    fun `simulate incoming call error does not affect state`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        // endpointId is null

        viewModel.simulateIncomingCall()

        assertTrue(viewModel.showError)
        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
    }

    // =========================================================================
    // State consistency after complex sequences
    // =========================================================================

    @Test
    fun `connect-call-hangup-call-hangup-disconnect sequence`() {
        viewModel.connect()
        assertEquals(ConnectionState.CONNECTING, viewModel.connectionState)

        viewModel.connectionState = ConnectionState.CONNECTED

        viewModel.phoneNumber = "5551234567"
        viewModel.call()
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.callDuration = 30
        viewModel.hangup()

        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
        assertEquals(0L, viewModel.callDuration)

        viewModel.phoneNumber = "5559876543"
        viewModel.call()
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.callDuration = 60
        viewModel.hangup()

        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
        assertEquals(0L, viewModel.callDuration)

        viewModel.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, viewModel.connectionState)
    }

    @Test
    fun `disconnect during RINGING cleans up properly`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.phoneNumber = "5551234567"
        viewModel.call()
        assertEquals(ConnectionState.RINGING, viewModel.connectionState)

        viewModel.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, viewModel.connectionState)
        assertEquals(0L, viewModel.callDuration)
        assertEquals("", viewModel.statusText)
    }

    @Test
    fun `disconnect during IN_CALL cleans up properly`() {
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.callDuration = 500
        viewModel.isSpeakerOn = true

        viewModel.disconnect()

        assertEquals(ConnectionState.DISCONNECTED, viewModel.connectionState)
        assertEquals(0L, viewModel.callDuration)
        assertFalse(viewModel.isSpeakerOn)
    }

    // =========================================================================
    // Determinism verification
    // =========================================================================

    @Test
    fun `same operation sequence always produces same final state`() {
        repeat(5) { run ->
            val app = RuntimeEnvironment.getApplication()
            val vm = CallViewModel(app)

            vm.connectionState = ConnectionState.CONNECTED
            vm.phoneNumber = "5551234567"
            vm.call()
            vm.connectionState = ConnectionState.IN_CALL
            vm.callDuration = 45
            vm.toggleMic()
            vm.toggleSpeaker()
            vm.hangup()

            assertEquals("Run $run: State should be CONNECTED",
                ConnectionState.CONNECTED, vm.connectionState)
            assertEquals("Run $run: Duration should be 0", 0L, vm.callDuration)
            assertFalse("Run $run: Mic should be off (toggled once from on)", vm.isMicEnabled)
            assertFalse("Run $run: isOutboundCall should be false", vm.isOutboundCall)
            assertEquals("Run $run: Status should be Connected", "Connected", vm.statusText)
        }
    }

    @Test
    fun `property reads are consistent within same state`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.phoneNumber = "5551234567"
        viewModel.callDuration = 125

        // Multiple reads should return same values
        repeat(100) {
            assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
            assertEquals("5551234567", viewModel.phoneNumber)
            assertEquals("(555) 123-4567", viewModel.formattedPhoneNumber)
            assertEquals("+15551234567", viewModel.e164PhoneNumber)
            assertEquals("2:05", viewModel.callDurationFormatted)
        }
    }

    // =========================================================================
    // Call history ordering after complex operations
    // =========================================================================

    @Test
    fun `call history maintains chronological order through multiple operations`() {
        viewModel.connectionState = ConnectionState.CONNECTED

        // Make several calls
        for (i in 1..5) {
            viewModel.phoneNumber = "555000000$i"
            viewModel.call()
            viewModel.connectionState = ConnectionState.IN_CALL
            viewModel.hangup()
            viewModel.connectionState = ConnectionState.CONNECTED
        }

        assertEquals(5, viewModel.callHistory.records.size)

        // Most recent call should be first (phone number "5550000005" → e164: "+15550000005")
        assertEquals("+15550000005", viewModel.callHistory.records[0].phoneNumber)
    }

    @Test
    fun `decline creates missed call record then accept creates answered record`() {
        viewModel.connectionState = ConnectionState.RINGING
        viewModel.declineIncomingCall()

        assertEquals(1, viewModel.callHistory.records.size)
        assertTrue(viewModel.callHistory.records[0].isMissed)

        viewModel.connectionState = ConnectionState.RINGING
        viewModel.acceptIncomingCall()

        assertEquals(2, viewModel.callHistory.records.size)
        // Most recent (index 0) is the accepted call
        assertEquals(CallDirection.INBOUND, viewModel.callHistory.records[0].direction)
    }
}
