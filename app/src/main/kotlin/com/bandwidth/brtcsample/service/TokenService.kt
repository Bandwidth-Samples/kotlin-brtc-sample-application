package com.bandwidth.brtcsample.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class CallStatus(val status: String, val callId: String? = null, val cause: String? = null)

class TokenService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class TokenResult(val token: String, val endpointId: String?)

    suspend fun fetchToken(serverURL: String): TokenResult = withContext(Dispatchers.IO) {
        val url = "${serverURL.trimEnd('/')}/token"
        val request = Request.Builder().url(url).get().build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            Log.e("TokenService", "Network error fetching token: ${e.message}")
            throw e
        }

        if (!response.isSuccessful) {
            Log.e("TokenService", "Server error: ${response.code}")
            throw IOException("Server returned status ${response.code}")
        }

        val body = response.body?.string()
            ?: throw IOException("Empty response from server")

        try {
            val json = JSONObject(body)
            val token = json.getString("token")
            val endpointId = json.optString("endpointId", "")
            return@withContext TokenResult(token, if (endpointId.isEmpty()) null else endpointId)
        } catch (e: Exception) {
            Log.w("TokenService", "Failed to parse as JSON: ${e.message}")
        }

        val token = body.trim()
        if (token.isEmpty()) {
            Log.e("TokenService", "Empty token response")
            throw IOException("Invalid token response from server")
        }
        TokenResult(token, null)
    }

    /** Poll the PSTN call status for an endpoint. */
    suspend fun getCallStatus(serverURL: String, endpointId: String): CallStatus = withContext(Dispatchers.IO) {
        val url = "${serverURL.trimEnd('/')}/api/endpoint/$endpointId/call-status"
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw IOException("Empty response")
        val json = JSONObject(body)
        CallStatus(
            status = json.getString("status"),
            callId = json.optString("callId", null),
            cause = json.optString("cause", null)
        )
    }

    /** Tell the server to hang up the PSTN leg for an endpoint. */
    suspend fun hangupCall(serverURL: String, endpointId: String): Unit = withContext(Dispatchers.IO) {
        val url = "${serverURL.trimEnd('/')}/api/endpoint/$endpointId/hangup"
        val request = Request.Builder().url(url)
            .post("".toRequestBody("application/json".toMediaType()))
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Hangup failed with status ${response.code}")
        }
    }
}
