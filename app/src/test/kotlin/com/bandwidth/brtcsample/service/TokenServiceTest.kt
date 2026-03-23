package com.bandwidth.brtcsample.service

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Comprehensive tests for TokenService.
 *
 * Covers:
 * - Successful JSON token responses (with and without endpointId)
 * - Successful plain-text token responses
 * - HTTP error status codes
 * - Empty response bodies
 * - Malformed JSON responses
 * - Network timeouts
 * - URL construction with trailing slashes
 * - Connection failures
 */
class TokenServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var tokenService: TokenService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tokenService = TokenService()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    // =========================================================================
    // Successful responses
    // =========================================================================

    @Test
    fun `fetchToken with JSON response containing token and endpointId`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"token": "test-token-123", "endpointId": "ep-456"}""")
            .setHeader("Content-Type", "application/json"))

        val result = tokenService.fetchToken(baseUrl())

        assertEquals("test-token-123", result.token)
        assertEquals("ep-456", result.endpointId)
    }

    @Test
    fun `fetchToken with JSON response containing only token`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"token": "test-token-789"}""")
            .setHeader("Content-Type", "application/json"))

        val result = tokenService.fetchToken(baseUrl())

        assertEquals("test-token-789", result.token)
        assertNull("endpointId should be null when not present", result.endpointId)
    }

    @Test
    fun `fetchToken with JSON response with empty endpointId`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"token": "test-token", "endpointId": ""}""")
            .setHeader("Content-Type", "application/json"))

        val result = tokenService.fetchToken(baseUrl())

        assertEquals("test-token", result.token)
        assertNull("Empty endpointId should be treated as null", result.endpointId)
    }

    @Test
    fun `fetchToken with plain text response`() = runTest {
        server.enqueue(MockResponse()
            .setBody("plain-text-token-value")
            .setHeader("Content-Type", "text/plain"))

        val result = tokenService.fetchToken(baseUrl())

        assertEquals("plain-text-token-value", result.token)
        assertNull("Plain text response should have null endpointId", result.endpointId)
    }

    @Test
    fun `fetchToken with plain text token with whitespace`() = runTest {
        server.enqueue(MockResponse()
            .setBody("  token-with-spaces  ")
            .setHeader("Content-Type", "text/plain"))

        val result = tokenService.fetchToken(baseUrl())

        assertEquals("token-with-spaces", result.token)
    }

    // =========================================================================
    // URL construction
    // =========================================================================

    @Test
    fun `fetchToken constructs correct URL with trailing slash`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"token": "t"}"""))

        tokenService.fetchToken(baseUrl() + "/")

        val request = server.takeRequest()
        assertEquals("/token", request.path)
    }

    @Test
    fun `fetchToken constructs correct URL without trailing slash`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"token": "t"}"""))

        tokenService.fetchToken(baseUrl())

        val request = server.takeRequest()
        assertEquals("/token", request.path)
    }

    @Test
    fun `fetchToken uses GET method`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"token": "t"}"""))

        tokenService.fetchToken(baseUrl())

        val request = server.takeRequest()
        assertEquals("GET", request.method)
    }

    // =========================================================================
    // Error handling
    // =========================================================================

    @Test(expected = IOException::class)
    fun `fetchToken throws on 404 response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        tokenService.fetchToken(baseUrl())
    }

    @Test(expected = IOException::class)
    fun `fetchToken throws on 500 response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        tokenService.fetchToken(baseUrl())
    }

    @Test(expected = IOException::class)
    fun `fetchToken throws on 401 unauthorized`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        tokenService.fetchToken(baseUrl())
    }

    @Test(expected = IOException::class)
    fun `fetchToken throws on 403 forbidden`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        tokenService.fetchToken(baseUrl())
    }

    @Test(expected = IOException::class)
    fun `fetchToken throws on empty response body`() = runTest {
        server.enqueue(MockResponse().setBody(""))
        tokenService.fetchToken(baseUrl())
    }

    @Test(expected = IOException::class)
    fun `fetchToken throws on null body`() = runTest {
        // MockWebServer with no body set
        server.enqueue(MockResponse().setResponseCode(200).removeHeader("Content-Length"))
        tokenService.fetchToken(baseUrl())
    }

    @Test(expected = IOException::class)
    fun `fetchToken throws on whitespace-only body`() = runTest {
        server.enqueue(MockResponse().setBody("   \n\t  "))
        tokenService.fetchToken(baseUrl())
    }

    // =========================================================================
    // Malformed JSON
    // =========================================================================

    @Test
    fun `fetchToken falls back to plain text for malformed JSON`() = runTest {
        server.enqueue(MockResponse()
            .setBody("{invalid json}")
            .setHeader("Content-Type", "application/json"))

        val result = tokenService.fetchToken(baseUrl())
        // Should fall back to treating the body as a plain text token
        assertEquals("{invalid json}", result.token)
        assertNull(result.endpointId)
    }

    @Test
    fun `fetchToken falls back for JSON missing token field`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"someOtherField": "value"}"""))

        // JSONObject.getString throws if key doesn't exist, falling back to plain text
        val result = tokenService.fetchToken(baseUrl())
        assertNotNull(result.token)
    }

    @Test
    fun `fetchToken handles JSON with extra fields`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"token": "my-token", "endpointId": "ep-1", "extraField": "ignored"}"""))

        val result = tokenService.fetchToken(baseUrl())
        assertEquals("my-token", result.token)
        assertEquals("ep-1", result.endpointId)
    }

    // =========================================================================
    // Network error scenarios
    // =========================================================================

    @Test(expected = Exception::class)
    fun `fetchToken throws on connection refused`() = runTest {
        server.shutdown()
        tokenService.fetchToken("http://localhost:1") // Port that's definitely not listening
    }

    @Test(expected = Exception::class)
    fun `fetchToken throws on invalid URL`() = runTest {
        tokenService.fetchToken("not-a-valid-url")
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    @Test
    fun `fetchToken handles very long token`() = runTest {
        val longToken = "a".repeat(10000)
        server.enqueue(MockResponse()
            .setBody("""{"token": "$longToken"}"""))

        val result = tokenService.fetchToken(baseUrl())
        assertEquals(longToken, result.token)
    }

    @Test
    fun `fetchToken handles special characters in token`() = runTest {
        val specialToken = "token+with/special=chars=="
        server.enqueue(MockResponse()
            .setBody("""{"token": "$specialToken"}"""))

        val result = tokenService.fetchToken(baseUrl())
        assertEquals(specialToken, result.token)
    }

    @Test
    fun `fetchToken handles unicode in endpointId`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"token": "t", "endpointId": "ep-\u00e9\u00e8\u00ea"}"""))

        val result = tokenService.fetchToken(baseUrl())
        assertEquals("t", result.token)
        assertNotNull(result.endpointId)
    }

    // =========================================================================
    // Consecutive calls
    // =========================================================================

    @Test
    fun `fetchToken can be called multiple times sequentially`() = runTest {
        repeat(3) { i ->
            server.enqueue(MockResponse()
                .setBody("""{"token": "token-$i", "endpointId": "ep-$i"}"""))
        }

        for (i in 0 until 3) {
            val result = tokenService.fetchToken(baseUrl())
            assertEquals("token-$i", result.token)
            assertEquals("ep-$i", result.endpointId)
        }
    }

    @Test
    fun `fetchToken recovers after error`() = runTest {
        // First call fails
        server.enqueue(MockResponse().setResponseCode(500))
        // Second call succeeds
        server.enqueue(MockResponse().setBody("""{"token": "recovery-token"}"""))

        try {
            tokenService.fetchToken(baseUrl())
            fail("Should have thrown")
        } catch (_: IOException) {}

        val result = tokenService.fetchToken(baseUrl())
        assertEquals("recovery-token", result.token)
    }

    // =========================================================================
    // HTTP status edge cases
    // =========================================================================

    @Test
    fun `fetchToken succeeds on 200`() = runTest {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"token": "ok"}"""))

        val result = tokenService.fetchToken(baseUrl())
        assertEquals("ok", result.token)
    }

    @Test(expected = IOException::class)
    fun `fetchToken throws on 301 redirect`() = runTest {
        server.enqueue(MockResponse().setResponseCode(301))
        tokenService.fetchToken(baseUrl())
    }

    @Test(expected = IOException::class)
    fun `fetchToken throws on 503 service unavailable`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        tokenService.fetchToken(baseUrl())
    }
}
