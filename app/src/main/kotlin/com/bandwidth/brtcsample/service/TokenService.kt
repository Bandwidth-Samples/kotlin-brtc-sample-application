package com.bandwidth.brtcsample.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class TokenService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class TokenResult(val token: String, val endpointId: String?)

    suspend fun fetchToken(serverURL: String): TokenResult = withContext(Dispatchers.IO) {
        Log.d("TokenService", "Fetching token from: $serverURL")
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
        
        Log.d("TokenService", "Received response body: $body")

        // Try JSON first
        try {
            val json = JSONObject(body)
            val token = json.getString("token")
            val endpointId = json.optString("endpointId", "")
            Log.d("TokenService", "Parsed JSON token: $token, endpointId: $endpointId")
            return@withContext TokenResult(token, if (endpointId.isEmpty()) null else endpointId)
        } catch (e: Exception) {
            Log.w("TokenService", "Failed to parse as JSON: ${e.message}")
        }

        // Fall back to raw string
        val token = body.trim()
        if (token.isEmpty()) {
            Log.e("TokenService", "Empty token response")
            throw IOException("Invalid token response from server")
        }
        Log.d("TokenService", "Using raw body as token: $token")
        TokenResult(token, null)
    }
}
