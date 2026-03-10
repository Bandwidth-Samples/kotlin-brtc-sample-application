package com.bandwidth.brtcsample.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bandwidth.brtcsample.model.CallDetailRecord
import com.bandwidth.brtcsample.model.CallDirection
import com.bandwidth.brtcsample.model.CallHistoryManager
import com.bandwidth.brtcsample.service.TokenService
import com.bandwidth.rtc.BandwidthRTC
import com.bandwidth.rtc.types.CallStatsSnapshot
import com.bandwidth.rtc.types.EndpointType
import com.bandwidth.rtc.types.RtcAuthParams
import com.bandwidth.rtc.types.RtcStream
import com.bandwidth.rtc.util.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, RINGING, IN_CALL
}

class CallViewModel(application: Application) : AndroidViewModel(application) {

    // UI State
    var connectionState by mutableStateOf(ConnectionState.DISCONNECTED)
    var serverURL by mutableStateOf("http://10.0.2.2:3000")
    var phoneNumber by mutableStateOf("")
    var isMicEnabled by mutableStateOf(true)
    var showError by mutableStateOf(false)
    var errorMessage by mutableStateOf("")
    var showDialpad by mutableStateOf(false)
    var statusText by mutableStateOf("")
    var callDuration by mutableLongStateOf(0L)
    var callStats by mutableStateOf<CallStatsSnapshot?>(null)
    var showStatsOverlay by mutableStateOf(false)

    // Audio waveform
    val localAudioLevels = mutableStateListOf<Float>()
    val remoteAudioLevels = mutableStateListOf<Float>()
    private val waveformCapacity = 50

    val callDurationFormatted: String
        get() {
            val minutes = callDuration / 60
            val seconds = callDuration % 60
            return String.format("%d:%02d", minutes, seconds)
        }

    val formattedPhoneNumber: String
        get() {
            val digits = phoneNumber.filter { it.isDigit() }
            if (digits.length == 10) {
                return "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
            } else if (digits.length == 11 && digits.startsWith("1")) {
                return "+1 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7)}"
            }
            return phoneNumber
        }

    val e164PhoneNumber: String
        get() {
            val digits = phoneNumber.filter { it.isDigit() }
            if (digits.length == 10) return "+1$digits"
            if (digits.length == 11 && digits.startsWith("1")) return "+$digits"
            return phoneNumber
        }

    // Call history
    val callHistory = CallHistoryManager()

    // Private
    private val brtc = BandwidthRTC(application.applicationContext, logLevel = LogLevel.DEBUG)
    private val tokenService = TokenService()
    private var localStream: RtcStream? = null
    private var remoteStream: RtcStream? = null
    private var callTimerJob: Job? = null
    private var statsTimerJob: Job? = null
    private var previousStatsSnapshot: CallStatsSnapshot? = null
    private var activeCallRecordId: UUID? = null
    var endpointId: String? by mutableStateOf(null)
        private set

    // Audio level accumulators
    private val localAccumulator = AudioLevelAccumulator()
    private val remoteAccumulator = AudioLevelAccumulator()

    init {
        setupCallbacks()
    }

    // MARK: - Actions

    fun connect() {
        if (connectionState != ConnectionState.DISCONNECTED) return
        connectionState = ConnectionState.CONNECTING
        statusText = "Fetching token..."

        viewModelScope.launch {
            try {
                val result = tokenService.fetchToken(serverURL)
                endpointId = result.endpointId
                statusText = "Connecting to BRTC..."

                brtc.connect(authParams = RtcAuthParams(endpointToken = result.token))

                statusText = "Publishing media..."
                val stream = brtc.publish(audio = true)
                localStream = stream

                connectionState = ConnectionState.CONNECTED
                statusText = "Connected"
            } catch (e: Exception) {
                connectionState = ConnectionState.DISCONNECTED
                showErrorMessage(e.message ?: "Connection failed")
            }
        }
    }

    fun disconnect() {
        callTimerJob?.cancel()
        callTimerJob = null
        callDuration = 0
        stopStatsPolling()
        stopAudioLevelMonitoring()
        viewModelScope.launch {
            brtc.disconnect()
        }
        localStream = null
        remoteStream = null
        endpointId = null
        connectionState = ConnectionState.DISCONNECTED
        statusText = ""
    }

    fun dialpadInput(digit: String) {
        phoneNumber += digit
    }

    fun call() {
        if (phoneNumber.isEmpty()) {
            showErrorMessage("Enter a phone number")
            return
        }

        connectionState = ConnectionState.IN_CALL
        statusText = "Calling $formattedPhoneNumber..."

        val record = CallDetailRecord(
            phoneNumber = e164PhoneNumber,
            direction = CallDirection.OUTBOUND
        )
        callHistory.addRecord(record)
        activeCallRecordId = record.id

        viewModelScope.launch {
            try {
                val result = brtc.requestOutboundConnection(
                    id = e164PhoneNumber,
                    type = EndpointType.PHONE_NUMBER
                )
                if (result.accepted) {
                    statusText = "Ringing..."
                } else {
                    statusText = "Call not accepted"
                }
            } catch (e: Exception) {
                showErrorMessage(e.message ?: "Call failed")
            }
        }
    }

    fun hangup() {
        finalizeCallRecord()
        callTimerJob?.cancel()
        callTimerJob = null
        callDuration = 0
        stopStatsPolling()
        stopAudioLevelMonitoring()

        viewModelScope.launch {
            if (phoneNumber.isNotEmpty()) {
                try {
                    brtc.hangupConnection(
                        endpoint = e164PhoneNumber,
                        type = EndpointType.PHONE_NUMBER
                    )
                } catch (_: Exception) {}
            }
            connectionState = ConnectionState.CONNECTED
            statusText = "Connected"
            remoteStream = null
        }
    }

    fun toggleMic() {
        isMicEnabled = !isMicEnabled
        brtc.setMicEnabled(isMicEnabled)
    }

    fun sendDtmf(tone: String) {
        brtc.sendDtmf(tone)
    }

    fun simulateIncomingCall() {
        if (endpointId == null) {
            showErrorMessage("Not connected to an endpoint yet")
            return
        }

        statusText = "Incoming call in 3s..."

        viewModelScope.launch {
            delay(3000)
            if (connectionState == ConnectionState.CONNECTED) {
                connectionState = ConnectionState.RINGING
            }
        }
    }

    fun acceptIncomingCall() {
        if (connectionState != ConnectionState.RINGING) return
        connectionState = ConnectionState.IN_CALL
        statusText = "Connecting..."
        callDuration = 0

        recordIncomingCall("Incoming Call")

        val eid = endpointId ?: run {
            statusText = "Error: no endpoint"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "${serverURL.trimEnd('/')}/simulate-incoming-call"
                val json = JSONObject().apply {
                    put("endpointId", eid)
                    put("delaySeconds", 0)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).post(body).build()
                OkHttpClient().newCall(request).execute()
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    showErrorMessage(e.message ?: "Failed to initiate incoming call")
                }
            }
        }
    }

    fun declineIncomingCall() {
        if (connectionState == ConnectionState.RINGING) {
            remoteStream = null
            connectionState = ConnectionState.CONNECTED
            statusText = "Connected"

            callHistory.addRecord(CallDetailRecord(
                phoneNumber = "Incoming Call",
                direction = CallDirection.INBOUND,
                duration = 0
            ))
        } else if (connectionState == ConnectionState.IN_CALL) {
            hangup()
        }
    }

    fun formatBitrate(bps: Double): String {
        return when {
            bps > 1_000_000 -> String.format("%.1f Mbps", bps / 1_000_000)
            bps > 1_000 -> String.format("%.0f kbps", bps / 1_000)
            bps > 0 -> String.format("%.0f bps", bps)
            else -> "---"
        }
    }

    // MARK: - Private

    private fun setupCallbacks() {
        brtc.onStreamAvailable = { stream ->
            viewModelScope.launch(Dispatchers.Main) {
                remoteStream = stream

                when (connectionState) {
                    ConnectionState.RINGING -> {
                        // Hold until user accepts
                    }
                    ConnectionState.CONNECTED -> {
                        connectionState = ConnectionState.IN_CALL
                        statusText = "Incoming call"
                        recordIncomingCall("Incoming Call")
                        startCallTimer()
                    }
                    ConnectionState.IN_CALL -> {
                        if (statusText == "Connecting...") {
                            statusText = "Incoming call"
                        }
                        if (callTimerJob == null) {
                            startCallTimer()
                        }
                    }
                    else -> {}
                }
            }
        }

        brtc.onStreamUnavailable = { _ ->
            viewModelScope.launch(Dispatchers.Main) {
                remoteStream = null

                if (connectionState == ConnectionState.IN_CALL) {
                    finalizeCallRecord()
                    callTimerJob?.cancel()
                    callTimerJob = null
                    callDuration = 0
                    stopStatsPolling()
                    stopAudioLevelMonitoring()
                    connectionState = ConnectionState.CONNECTED
                    statusText = "Call ended"
                }
            }
        }

        brtc.onRemoteDisconnected = {
            viewModelScope.launch(Dispatchers.Main) {
                if (connectionState == ConnectionState.RINGING) {
                    connectionState = ConnectionState.CONNECTED
                    statusText = "Missed call"
                } else if (connectionState == ConnectionState.IN_CALL) {
                    finalizeCallRecord()
                    callTimerJob?.cancel()
                    callTimerJob = null
                    callDuration = 0
                    stopStatsPolling()
                    stopAudioLevelMonitoring()
                    connectionState = ConnectionState.CONNECTED
                    statusText = "Call ended"
                    remoteStream = null
                }
            }
        }

        brtc.onReady = { metadata ->
            viewModelScope.launch(Dispatchers.Main) {
                metadata.endpointId?.let { endpointId = it }
                statusText = "Ready\n${metadata.endpointId ?: ""}"
            }
        }
    }

    private fun startCallTimer() {
        callTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                callDuration++
            }
        }
        startStatsPolling()
        startAudioLevelMonitoring()
    }

    private fun startStatsPolling() {
        statsTimerJob?.cancel()
        previousStatsSnapshot = null
        statsTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(2000)
                pollStats()
            }
        }
    }

    private fun stopStatsPolling() {
        statsTimerJob?.cancel()
        statsTimerJob = null
        callStats = null
        previousStatsSnapshot = null
        showStatsOverlay = false
    }

    private fun startAudioLevelMonitoring() {
        localAudioLevels.clear()
        remoteAudioLevels.clear()
        localAccumulator.reset()
        remoteAccumulator.reset()

        brtc.onLocalAudioLevel = { samples ->
            localAccumulator.accumulate(samples)
            localAccumulator.getLevel()?.let { level ->
                viewModelScope.launch(Dispatchers.Main) {
                    val displayLevel = if (isMicEnabled) level else 0f
                    localAudioLevels.add(displayLevel)
                    if (localAudioLevels.size > waveformCapacity) localAudioLevels.removeAt(0)
                }
            }
        }

        brtc.onRemoteAudioLevel = { samples ->
            remoteAccumulator.accumulate(samples)
            remoteAccumulator.getLevel()?.let { level ->
                viewModelScope.launch(Dispatchers.Main) {
                    remoteAudioLevels.add(level)
                    if (remoteAudioLevels.size > waveformCapacity) remoteAudioLevels.removeAt(0)
                }
            }
        }
    }

    private fun stopAudioLevelMonitoring() {
        brtc.onLocalAudioLevel = null
        brtc.onRemoteAudioLevel = null
        localAudioLevels.clear()
        remoteAudioLevels.clear()
    }

    private fun pollStats() {
        brtc.getCallStats(previousSnapshot = previousStatsSnapshot) { snapshot ->
            viewModelScope.launch(Dispatchers.Main) {
                callStats = snapshot
                previousStatsSnapshot = snapshot
            }
        }
    }

    private fun recordIncomingCall(phoneNumber: String) {
        val record = CallDetailRecord(
            phoneNumber = phoneNumber,
            direction = CallDirection.INBOUND,
            duration = 0
        )
        callHistory.addRecord(record)
        activeCallRecordId = record.id
    }

    private fun finalizeCallRecord() {
        val id = activeCallRecordId ?: return
        callHistory.updateDuration(id, callDuration)
        activeCallRecordId = null
    }

    private fun showErrorMessage(message: String) {
        errorMessage = message
        showError = true
    }
}

// Audio Level Accumulator
private class AudioLevelAccumulator {
    private var sumSq: Float = 0f
    private var frameCount = 0
    private val lock = Any()

    fun reset() {
        synchronized(lock) {
            sumSq = 0f
            frameCount = 0
        }
    }

    fun accumulate(samples: FloatArray) {
        synchronized(lock) {
            for (s in samples) {
                sumSq += s * s
            }
            frameCount += samples.size
        }
    }

    fun getLevel(): Float? {
        synchronized(lock) {
            if (frameCount < 9600) return null

            val rms = sqrt(sumSq / frameCount)
            val db = 20f * log10(max(rms, 1e-7f))
            val level = max(0f, min(1f, (db + 70f) / 70f))

            sumSq = 0f
            frameCount = 0

            return level
        }
    }
}
