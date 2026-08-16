package ir.amirab.downloader.connection

import ir.amirab.downloader.connection.proxy.AutoConfigurableProxyProvider
import ir.amirab.downloader.connection.proxy.ProxyStrategy
import ir.amirab.downloader.connection.proxy.ProxyStrategyProvider
import ir.amirab.downloader.connection.proxy.ProxyType
import ir.amirab.downloader.connection.proxy.SystemProxySelectorProvider
import ir.amirab.downloader.connection.response.HttpResponseInfo
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.downloader.downloaditem.http.IHttpBasedDownloadCredentials
import ir.amirab.downloader.downloaditem.http.IHttpDownloadCredentials
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.buffer
import java.net.ProxySelector
import java.net.ServerSocket
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurlResponseParserTest {
    @Test
    fun anIncompleteHeaderBlockIsNotReturned() {
        assertNull(parseLastCompleteCurlResponse("HTTP/1.1 206 Partial Content\r\nContent-Length: 4\r\n"))
    }

    @Test
    fun theLastCompletedResponseWinsAfterARedirect() {
        val parsed = parseLastCompleteCurlResponse(
            "HTTP/1.1 302 Found\r\nLocation: /file\r\n\r\n" +
                "HTTP/2 206 Partial Content\r\nContent-Length: 4\r\n" +
                "Cf-Mitigated: challenge\r\n\r\n",
        )

        assertEquals(206, parsed?.statusCode)
        assertEquals("Partial Content", parsed?.message)
        assertEquals("4", parsed?.headers?.get("content-length"))
        assertEquals("challenge", parsed?.headers?.get("cf-mitigated"))
    }
}

class CurlConfigTest {
    @Test
    fun signedUrlsAndHeadersAreWrittenToTheStdinConfig() {
        val config = buildCurlConfig(
            url = "https://example.com/a.zip?token=a&b=two",
            headers = mapOf("Cookie" to "cf_clearance=a=b", "Range" to "bytes=0-3"),
            proxy = ExternalProxy.Endpoint(
                type = ProxyType.HTTP,
                host = "127.0.0.1",
                port = 7897,
            ),
            headerFile = Path.of("headers.txt"),
        )

        assertTrue(config.contains("url = \"https://example.com/a.zip?token=a&b=two\""))
        assertTrue(config.contains("header = \"Cookie: cf_clearance=a=b\""))
        assertTrue(config.contains("header = \"Range: bytes=0-3\""))
        assertTrue(config.contains("proxy = \"http://127.0.0.1:7897\""))
    }
}

class CloudflareFallbackHttpDownloaderClientTest {
    private val credentials = HttpDownloadCredentials("https://example.com/file.zip")

    @Test
    fun ordinaryResponsesNeverInvokeFallback() = runBlocking {
        val primary = StubClient(success())
        val fallback = StubClient(success())
        val client = CloudflareFallbackHttpDownloaderClient(primary, fallback)

        assertEquals(200, client.head(credentials, 0, 3).statusCode)
        assertEquals(1, primary.headCalls)
        assertEquals(0, fallback.headCalls)
    }

    @Test
    fun aCloudflareChallengeUsesFallback() = runBlocking {
        val primary = StubClient(challenge())
        val fallback = StubClient(success(status = 206))
        val client = CloudflareFallbackHttpDownloaderClient(primary, fallback)

        assertEquals(206, client.head(credentials, 0, 3).statusCode)
        assertEquals(1, fallback.headCalls)
    }

    @Test
    fun aMissingFallbackPreservesTheActionableChallenge() = runBlocking {
        val primary = StubClient(challenge())
        val fallback = StubClient(success(), fail = true)
        val client = CloudflareFallbackHttpDownloaderClient(primary, fallback)

        val response = client.head(credentials, 0, 3)
        assertTrue(response.isCloudflareChallenge)
    }

    private class StubClient(
        private val response: HttpResponseInfo,
        private val fail: Boolean = false,
    ) : HttpDownloaderClient() {
        var headCalls = 0

        override suspend fun actualHead(
            credentials: IHttpDownloadCredentials,
            start: Long?,
            end: Long?,
        ): HttpResponseInfo {
            headCalls++
            if (fail) error("fallback unavailable")
            return response
        }

        override suspend fun actualConnect(
            credentials: IHttpBasedDownloadCredentials,
            start: Long?,
            end: Long?,
        ): Connection<HttpResponseInfo> {
            if (fail) error("fallback unavailable")
            return Connection(Buffer(), 0, response)
        }
    }

    companion object {
        private fun success(status: Int = 200) = HttpResponseInfo(
            statusCode = status,
            message = "OK",
            requestUrl = "https://example.com/file.zip",
        )

        private fun challenge() = HttpResponseInfo(
            statusCode = 403,
            message = "Forbidden",
            requestUrl = "https://example.com/file.zip",
            responseHeaders = mapOf("cf-mitigated" to "challenge"),
        )
    }
}

class CurlProcessIntegrationTest {
    @Test
    fun curlStreamsARangedResponseThroughTheExistingConnectionType() = runBlocking {
        val executable = if (System.getProperty("os.name").contains("Windows", true)) {
            "curl.exe"
        } else {
            "curl"
        }
        if (!curlExists(executable)) {
            return@runBlocking
        }

        ServerSocket(0).use { server ->
            var requestHeaders = ""
            val serverThread = thread(isDaemon = true) {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    requestHeaders = generateSequence { input.readLine() }
                        .takeWhile { it.isNotEmpty() }
                        .joinToString("\n")
                    socket.getOutputStream().write(
                        (
                            "HTTP/1.1 206 Partial Content\r\n" +
                                "Content-Length: 4\r\n" +
                                "Content-Range: bytes 0-3/4\r\n" +
                                "Connection: close\r\n\r\n" +
                                "test"
                            ).toByteArray(Charsets.ISO_8859_1),
                    )
                }
            }
            val client = CurlHttpDownloaderClient(
                customUserAgentProvider = object : UserAgentProvider {
                    override fun getUserAgent(): String = "ABDM-Test"
                },
                proxyResolver = directProxyResolver(),
                executable = executable,
            )
            val connection = client.connect(
                HttpDownloadCredentials("http://127.0.0.1:${server.localPort}/file"),
                0,
                3,
            )
            connection.use {
                assertEquals(206, it.responseInfo.statusCode)
                assertEquals(4, it.contentLength)
                assertEquals("test", it.source.buffer().readUtf8())
            }
            serverThread.join(2_000)
            assertFalse(serverThread.isAlive)
            assertTrue(requestHeaders.contains("Range: bytes=0-3", ignoreCase = true))
            assertTrue(requestHeaders.contains("User-Agent: ABDM-Test", ignoreCase = true))
        }
    }

    private fun curlExists(executable: String): Boolean {
        return runCatching {
            ProcessBuilder(executable, "--version").start().let { process ->
                process.inputStream.close()
                process.errorStream.close()
                process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0
            }
        }.getOrDefault(false)
    }

    private fun directProxyResolver() = ExternalProxyResolver(
        proxyStrategyProvider = object : ProxyStrategyProvider {
            override fun getProxyStrategyFor(url: String): ProxyStrategy = ProxyStrategy.Direct
        },
        systemProxySelectorProvider = object : SystemProxySelectorProvider {
            override fun getSystemProxySelector(): ProxySelector? = null
        },
        autoConfigurableProxyProvider = AutoConfigurableProxyProvider.NoOp(),
    )
}
