package com.bandwidth.brtcsample.viewmodel

import android.app.Application
import com.bandwidth.brtcsample.model.CallDirection
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Comprehensive tests for CallViewModel.
 *
 * Covers:
 * - Initial state verification
 * - State machine transitions (ConnectionState)
 * - Phone number formatting (formattedPhoneNumber, e164PhoneNumber)
 * - Call duration formatting
 * - Bitrate formatting
 * - Dialpad input handling
 * - Error message display
 * - Guard clauses (connect/call/accept when in wrong state)
 * - Call lifecycle (call → hangup, incoming → accept/decline)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class CallViewModelTest {

    private lateinit var viewModel: CallViewModel

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        viewModel = CallViewModel(application)
    }

    // =========================================================================
    // Initial state
    // =========================================================================

    @Test
    fun `initial state is DISCONNECTED`() {
        assertEquals(ConnectionState.DISCONNECTED, viewModel.connectionState)
    }

    @Test
    fun `initial server URL is default localhost`() {
        assertEquals("http://localhost:3000", viewModel.serverURL)
    }

    @Test
    fun `initial phone number is empty`() {
        assertEquals("", viewModel.phoneNumber)
    }

    @Test
    fun `initial mic is enabled`() {
        assertTrue(viewModel.isMicEnabled)
    }

    @Test
    fun `initial speaker is off`() {
        assertFalse(viewModel.isSpeakerOn)
    }

    @Test
    fun `initial call duration is zero`() {
        assertEquals(0L, viewModel.callDuration)
    }

    @Test
    fun `initial call stats is null`() {
        assertNull(viewModel.callStats)
    }

    @Test
    fun `initial endpoint id is null`() {
        assertNull(viewModel.endpointId)
    }

    @Test
    fun `initial error state is false`() {
        assertFalse(viewModel.showError)
        assertEquals("", viewModel.errorMessage)
    }

    @Test
    fun `initial audio levels are empty`() {
        assertTrue(viewModel.localAudioLevels.isEmpty())
        assertTrue(viewModel.remoteAudioLevels.isEmpty())
    }

    @Test
    fun `initial isOutboundCall is false`() {
        assertFalse(viewModel.isOutboundCall)
    }

    @Test
    fun `initial showDialpad is false`() {
        assertFalse(viewModel.showDialpad)
    }

    @Test
    fun `initial showStatsOverlay is false`() {
        assertFalse(viewModel.showStatsOverlay)
    }

    @Test
    fun `initial statusText is empty`() {
        assertEquals("", viewModel.statusText)
    }

    // =========================================================================
    // Phone number formatting
    // =========================================================================

    @Test
    fun `formattedPhoneNumber formats 10-digit number`() {
        viewModel.phoneNumber = "5551234567"
        assertEquals("(555) 123-4567", viewModel.formattedPhoneNumber)
    }

    @Test
    fun `formattedPhoneNumber formats 11-digit US number`() {
        viewModel.phoneNumber = "15551234567"
        assertEquals("+1 (555) 123-4567", viewModel.formattedPhoneNumber)
    }

    @Test
    fun `formattedPhoneNumber returns raw for non-standard length`() {
        viewModel.phoneNumber = "12345"
        assertEquals("12345", viewModel.formattedPhoneNumber)
    }

    @Test
    fun `formattedPhoneNumber returns empty for empty input`() {
        viewModel.phoneNumber = ""
        assertEquals("", viewModel.formattedPhoneNumber)
    }

    @Test
    fun `formattedPhoneNumber filters non-digit characters`() {
        viewModel.phoneNumber = "(555) 123-4567"
        assertEquals("(555) 123-4567", viewModel.formattedPhoneNumber)
    }

    @Test
    fun `e164PhoneNumber formats 10-digit with +1 prefix`() {
        viewModel.phoneNumber = "5551234567"
        assertEquals("+15551234567", viewModel.e164PhoneNumber)
    }

    @Test
    fun `e164PhoneNumber formats 11-digit starting with 1`() {
        viewModel.phoneNumber = "15551234567"
        assertEquals("+15551234567", viewModel.e164PhoneNumber)
    }

    @Test
    fun `e164PhoneNumber returns raw for non-standard number`() {
        viewModel.phoneNumber = "123"
        assertEquals("123", viewModel.e164PhoneNumber)
    }

    @Test
    fun `e164PhoneNumber handles number with non-digit chars`() {
        viewModel.phoneNumber = "+15551234567"
        assertEquals("+15551234567", viewModel.e164PhoneNumber)
    }

    // =========================================================================
    // Call duration formatting
    // =========================================================================

    @Test
    fun `callDurationFormatted at zero`() {
        viewModel.callDuration = 0
        assertEquals("0:00", viewModel.callDurationFormatted)
    }

    @Test
    fun `callDurationFormatted for seconds only`() {
        viewModel.callDuration = 45
        assertEquals("0:45", viewModel.callDurationFormatted)
    }

    @Test
    fun `callDurationFormatted for minutes and seconds`() {
        viewModel.callDuration = 125
        assertEquals("2:05", viewModel.callDurationFormatted)
    }

    @Test
    fun `callDurationFormatted for exactly one minute`() {
        viewModel.callDuration = 60
        assertEquals("1:00", viewModel.callDurationFormatted)
    }

    @Test
    fun `callDurationFormatted for long duration`() {
        viewModel.callDuration = 3661
        assertEquals("61:01", viewModel.callDurationFormatted)
    }

    // =========================================================================
    // Bitrate formatting
    // =========================================================================

    @Test
    fun `formatBitrate for megabits`() {
        assertEquals("1.5 Mbps", viewModel.formatBitrate(1_500_000.0))
    }

    @Test
    fun `formatBitrate for kilobits`() {
        assertEquals("256 kbps", viewModel.formatBitrate(256_000.0))
    }

    @Test
    fun `formatBitrate for bits`() {
        assertEquals("500 bps", viewModel.formatBitrate(500.0))
    }

    @Test
    fun `formatBitrate for zero`() {
        assertEquals("---", viewModel.formatBitrate(0.0))
    }

    @Test
    fun `formatBitrate for negative`() {
        assertEquals("---", viewModel.formatBitrate(-100.0))
    }

    @Test
    fun `formatBitrate at exactly 1000`() {
        // bps > 1_000 is false when exactly 1000, falls to bps > 0 branch
        assertEquals("1000 bps", viewModel.formatBitrate(1000.0))
    }

    @Test
    fun `formatBitrate at exactly 1000001`() {
        assertEquals("1.0 Mbps", viewModel.formatBitrate(1_000_001.0))
    }

    @Test
    fun `formatBitrate just above zero`() {
        assertEquals("1 bps", viewModel.formatBitrate(1.0))
    }

    // =========================================================================
    // Dialpad input
    // =========================================================================

    @Test
    fun `dialpadInput appends digit to phone number`() {
        viewModel.dialpadInput("1")
        assertEquals("1", viewModel.phoneNumber)
    }

    @Test
    fun `dialpadInput appends multiple digits`() {
        viewModel.dialpadInput("5")
        viewModel.dialpadInput("5")
        viewModel.dialpadInput("5")
        assertEquals("555", viewModel.phoneNumber)
    }

    @Test
    fun `dialpadInput handles star`() {
        viewModel.dialpadInput("*")
        assertEquals("*", viewModel.phoneNumber)
    }

    @Test
    fun `dialpadInput handles hash`() {
        viewModel.dialpadInput("#")
        assertEquals("#", viewModel.phoneNumber)
    }

    @Test
    fun `dialpadInput builds full number`() {
        "5551234567".forEach { viewModel.dialpadInput(it.toString()) }
        assertEquals("5551234567", viewModel.phoneNumber)
    }

    // =========================================================================
    // State guards
    // =========================================================================

    @Test
    fun `connect does nothing when not DISCONNECTED`() {
        viewModel.connectionState = ConnectionState.CONNECTING
        val initialState = viewModel.connectionState

        viewModel.connect()

        assertEquals("State should not change when connecting from CONNECTING",
            initialState, viewModel.connectionState)
    }

    @Test
    fun `connect does nothing when CONNECTED`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.connect()
        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
    }

    @Test
    fun `connect does nothing when IN_CALL`() {
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.connect()
        assertEquals(ConnectionState.IN_CALL, viewModel.connectionState)
    }

    @Test
    fun `call with empty phone number shows error`() {
        viewModel.phoneNumber = ""
        viewModel.connectionState = ConnectionState.CONNECTED

        viewModel.call()

        assertTrue("Should show error for empty phone number", viewModel.showError)
        assertEquals("Enter a phone number", viewModel.errorMessage)
    }

    @Test
    fun `acceptIncomingCall does nothing when not RINGING`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.acceptIncomingCall()
        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
    }

    @Test
    fun `acceptIncomingCall does nothing when DISCONNECTED`() {
        viewModel.connectionState = ConnectionState.DISCONNECTED
        viewModel.acceptIncomingCall()
        assertEquals(ConnectionState.DISCONNECTED, viewModel.connectionState)
    }

    @Test
    fun `simulateIncomingCall shows error when not connected to endpoint`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        // endpointId is null by default

        viewModel.simulateIncomingCall()

        assertTrue(viewModel.showError)
        assertEquals("Not connected to an endpoint yet", viewModel.errorMessage)
    }

    // =========================================================================
    // Call lifecycle state transitions
    // =========================================================================

    @Test
    fun `call transitions to RINGING`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.phoneNumber = "5551234567"

        viewModel.call()

        assertEquals(ConnectionState.RINGING, viewModel.connectionState)
        assertTrue(viewModel.isOutboundCall)
    }

    @Test
    fun `call sets outbound call flag`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.phoneNumber = "5551234567"

        viewModel.call()

        assertTrue(viewModel.isOutboundCall)
    }

    @Test
    fun `call creates call history record`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.phoneNumber = "5551234567"

        viewModel.call()

        assertEquals(1, viewModel.callHistory.records.size)
        assertEquals(CallDirection.OUTBOUND, viewModel.callHistory.records[0].direction)
    }

    @Test
    fun `hangup transitions to CONNECTED`() {
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.phoneNumber = "5551234567"

        viewModel.hangup()

        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
        assertEquals("Connected", viewModel.statusText)
    }

    @Test
    fun `hangup resets call duration`() {
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.callDuration = 120

        viewModel.hangup()

        assertEquals(0L, viewModel.callDuration)
    }

    @Test
    fun `hangup resets isOutboundCall`() {
        // Set up outbound call state via the call() method
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.phoneNumber = "5551234567"
        viewModel.call()
        assertTrue(viewModel.isOutboundCall)

        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.hangup()

        assertFalse(viewModel.isOutboundCall)
    }

    @Test
    fun `declineIncomingCall from RINGING transitions to CONNECTED`() {
        viewModel.connectionState = ConnectionState.RINGING

        viewModel.declineIncomingCall()

        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
        assertEquals("Connected", viewModel.statusText)
    }

    @Test
    fun `declineIncomingCall from RINGING adds to call history`() {
        viewModel.connectionState = ConnectionState.RINGING

        viewModel.declineIncomingCall()

        assertEquals(1, viewModel.callHistory.records.size)
        assertEquals(CallDirection.INBOUND, viewModel.callHistory.records[0].direction)
        assertEquals(0L, viewModel.callHistory.records[0].duration)
    }

    @Test
    fun `declineIncomingCall from IN_CALL calls hangup`() {
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.phoneNumber = "5551234567"

        viewModel.declineIncomingCall()

        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
    }

    @Test
    fun `acceptIncomingCall from RINGING transitions to IN_CALL`() {
        viewModel.connectionState = ConnectionState.RINGING

        viewModel.acceptIncomingCall()

        assertEquals(ConnectionState.IN_CALL, viewModel.connectionState)
    }

    @Test
    fun `acceptIncomingCall creates inbound call record`() {
        viewModel.connectionState = ConnectionState.RINGING

        viewModel.acceptIncomingCall()

        assertEquals(1, viewModel.callHistory.records.size)
        assertEquals(CallDirection.INBOUND, viewModel.callHistory.records[0].direction)
    }

    // =========================================================================
    // Disconnect cleanup
    // =========================================================================

    @Test
    fun `disconnect resets to DISCONNECTED`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, viewModel.connectionState)
    }

    @Test
    fun `disconnect clears call duration`() {
        viewModel.callDuration = 300
        viewModel.disconnect()
        assertEquals(0L, viewModel.callDuration)
    }

    @Test
    fun `disconnect clears endpoint`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.disconnect()
        assertNull(viewModel.endpointId)
    }

    @Test
    fun `disconnect clears status text`() {
        viewModel.statusText = "Connected"
        viewModel.disconnect()
        assertEquals("", viewModel.statusText)
    }

    @Test
    fun `disconnect resets speaker state`() {
        viewModel.isSpeakerOn = true
        viewModel.disconnect()
        assertFalse(viewModel.isSpeakerOn)
    }

    @Test
    fun `disconnect clears audio levels`() {
        viewModel.localAudioLevels.addAll(listOf(0.1f, 0.2f, 0.3f))
        viewModel.remoteAudioLevels.addAll(listOf(0.4f, 0.5f))
        viewModel.disconnect()
        assertTrue(viewModel.localAudioLevels.isEmpty())
        assertTrue(viewModel.remoteAudioLevels.isEmpty())
    }

    // =========================================================================
    // Toggle controls
    // =========================================================================

    @Test
    fun `toggleMic flips mic state`() {
        assertTrue(viewModel.isMicEnabled)
        viewModel.toggleMic()
        assertFalse(viewModel.isMicEnabled)
        viewModel.toggleMic()
        assertTrue(viewModel.isMicEnabled)
    }

    @Test
    fun `toggleSpeaker flips speaker state`() {
        assertFalse(viewModel.isSpeakerOn)
        viewModel.toggleSpeaker()
        assertTrue(viewModel.isSpeakerOn)
        viewModel.toggleSpeaker()
        assertFalse(viewModel.isSpeakerOn)
    }

    // =========================================================================
    // Call history integration
    // =========================================================================

    @Test
    fun `call history is initially empty`() {
        assertTrue(viewModel.callHistory.records.isEmpty())
    }

    @Test
    fun `call adds record to history`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.phoneNumber = "5551234567"
        viewModel.call()

        assertEquals(1, viewModel.callHistory.records.size)
    }

    @Test
    fun `multiple calls build up history`() {
        viewModel.connectionState = ConnectionState.CONNECTED

        viewModel.phoneNumber = "5551111111"
        viewModel.call()

        // Reset state for next call
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.phoneNumber = "5552222222"
        viewModel.call()

        assertEquals(2, viewModel.callHistory.records.size)
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    @Test
    fun `multiple hangups do not crash`() {
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.hangup()
        viewModel.hangup()
        viewModel.hangup()
        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState)
    }

    @Test
    fun `multiple disconnects do not crash`() {
        viewModel.disconnect()
        viewModel.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, viewModel.connectionState)
    }

    @Test
    fun `dialpad input after call does not affect state`() {
        viewModel.connectionState = ConnectionState.IN_CALL
        viewModel.dialpadInput("5")
        assertEquals(ConnectionState.IN_CALL, viewModel.connectionState)
    }

    @Test
    fun `call with special characters in phone number`() {
        viewModel.connectionState = ConnectionState.CONNECTED
        viewModel.phoneNumber = "+1(555)123-4567"
        viewModel.call()

        assertEquals(ConnectionState.RINGING, viewModel.connectionState)
    }
}
