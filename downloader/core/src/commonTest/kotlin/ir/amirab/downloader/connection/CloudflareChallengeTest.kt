package ir.amirab.downloader.connection

import ir.amirab.downloader.connection.response.HttpResponseInfo
import ir.amirab.downloader.exception.CloudflareChallengeException
import ir.amirab.downloader.exception.UnSuccessfulResponseException
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudflareChallengeTest {
    @Test
    fun challengeResponseCreatesSpecificException() {
        val response = HttpResponseInfo(
            statusCode = 403,
            message = "Forbidden",
            requestUrl = "https://example.com/file.zip",
            responseHeaders = mapOf(
                "content-type" to "text/html; charset=UTF-8",
                "cf-mitigated" to "challenge",
            ),
        )

        assertTrue(response.isCloudflareChallenge)
        assertIs<CloudflareChallengeException>(response.unsuccessFullException)
    }

    @Test
    fun ordinaryForbiddenResponseKeepsGenericException() {
        val response = HttpResponseInfo(
            statusCode = 403,
            message = "Forbidden",
            requestUrl = "https://example.com/file.zip",
        )

        assertFalse(response.isCloudflareChallenge)
        assertIs<UnSuccessfulResponseException>(response.unsuccessFullException)
        assertFalse(response.unsuccessFullException is CloudflareChallengeException)
    }

    @Test
    fun antiLeechRetryDoesNotRetryCloudflareChallenge() {
        val request = request(referer = "https://download.example.com/")
        val response = response(
            request = request,
            headers = mapOf(
                "Access-Control-Allow-Origin" to "https://allowed.example.com",
                "Cf-Mitigated" to "challenge",
            ),
        )

        assertNull(getAntiLeechRetryReferer(request, response))
    }

    @Test
    fun ordinaryAntiLeechForbiddenResponseUsesAllowedOrigin() {
        val request = request(referer = "https://download.example.com/")
        val response = response(
            request = request,
            headers = mapOf(
                "Access-Control-Allow-Origin" to "https://allowed.example.com",
            ),
        )

        assertEquals(
            "https://allowed.example.com",
            getAntiLeechRetryReferer(request, response),
        )
    }

    private fun request(referer: String): Request {
        return Request.Builder()
            .url("https://download.example.com/file.zip")
            .header("Referer", referer)
            .build()
    }

    private fun response(
        request: Request,
        headers: Map<String, String>,
    ): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(403)
            .message("Forbidden")
            .apply {
                headers.forEach { (name, value) -> header(name, value) }
            }
            .build()
    }
}
