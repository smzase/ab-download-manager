package ir.amirab.downloader.connection

import ir.amirab.downloader.connection.clearance.ClearanceProvider
import ir.amirab.downloader.connection.proxy.ProxyType
import ir.amirab.downloader.connection.response.HttpResponseInfo
import ir.amirab.downloader.downloaditem.http.IHttpBasedDownloadCredentials
import ir.amirab.downloader.downloaditem.http.IHttpDownloadCredentials
import ir.amirab.downloader.utils.throwIfCancelled
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.source
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Retries only Cloudflare challenge responses with another HTTP implementation.
 * Other responses and all ordinary downloads continue to use [primary].
 */
class CloudflareFallbackHttpDownloaderClient(
    private val primary: HttpDownloaderClient,
    private val fallback: HttpDownloaderClient,
) : HttpDownloaderClient() {
    override suspend fun actualHead(
        credentials: IHttpDownloadCredentials,
        start: Long?,
        end: Long?,
    ): HttpResponseInfo {
        val primaryResponse = primary.head(credentials, start, end)
        if (!primaryResponse.isCloudflareChallenge) {
            return primaryResponse
        }
        return runCatching { fallback.head(credentials, start, end) }
            .getOrElse {
                it.throwIfCancelled()
                primaryResponse
            }
    }

    override suspend fun actualConnect(
        credentials: IHttpBasedDownloadCredentials,
        start: Long?,
        end: Long?,
    ): Connection<HttpResponseInfo> {
        val primaryConnection = primary.connect(credentials, start, end)
        if (!primaryConnection.responseInfo.isCloudflareChallenge) {
            return primaryConnection
        }
        val fallbackConnection = runCatching {
            fallback.connect(credentials, start, end)
        }.getOrElse {
            it.throwIfCancelled()
            return primaryConnection
        }
        primaryConnection.close()
        return fallbackConnection
    }
}

/**
 * Desktop compatibility backend backed by the operating system's curl executable.
 *
 * The complete request is supplied through curl's stdin config, not a shell or process
 * arguments. This prevents command injection and keeps signed URLs, cookies and proxy
 * credentials out of the process command line.
 */
class CurlHttpDownloaderClient(
    private val customUserAgentProvider: UserAgentProvider,
    private val proxyResolver: ExternalProxyResolver,
    private val clearanceProvider: ClearanceProvider = ClearanceProvider.NoOp(),
    private val executable: String = defaultCurlExecutable(),
) : HttpDownloaderClient() {
    override suspend fun actualHead(
        credentials: IHttpDownloadCredentials,
        start: Long?,
        end: Long?,
    ): HttpResponseInfo {
        return actualConnect(credentials, start, end).use { it.responseInfo }
    }

    override suspend fun actualConnect(
        credentials: IHttpBasedDownloadCredentials,
        start: Long?,
        end: Long?,
    ): Connection<HttpResponseInfo> {
        val proxy = proxyResolver.resolve(credentials.link)
        if (proxy == ExternalProxy.Unsupported) {
            throw CurlUnavailableException("The selected proxy strategy cannot be used by curl")
        }

        val requestHeaders = buildCurlRequestHeaders(
            credentials = credentials,
            start = start,
            end = end,
        )
        val tempDir = Files.createTempDirectory("abdm-curl-")
        val headerFile = tempDir.resolve("response-headers.txt")
        val errorFile = tempDir.resolve("stderr.txt")
        val resources = CurlProcessResources(tempDir, headerFile, errorFile)
        val process = try {
            ProcessBuilder(executable, "--config", "-")
                .redirectError(errorFile.toFile())
                .start()
        } catch (e: Exception) {
            resources.cleanup()
            throw CurlUnavailableException("Unable to start $executable", e)
        }

        try {
            val config = buildCurlConfig(
                url = credentials.link,
                headers = requestHeaders,
                proxy = proxy,
                headerFile = headerFile,
            )
            process.outputStream.bufferedWriter(StandardCharsets.UTF_8).use {
                it.write(config)
            }
            val parsed = awaitCurlResponse(process, resources)
            val responseInfo = HttpResponseInfo(
                statusCode = parsed.statusCode,
                message = parsed.message,
                requestUrl = credentials.link,
                requestHeaders = requestHeaders.entries.associate { (key, value) ->
                    key.lowercase() to value
                },
                responseHeaders = parsed.headers,
            )
            return Connection(
                source = CurlProcessSource(process, resources),
                contentLength = parsed.headers["content-length"]?.toLongOrNull() ?: -1L,
                responseInfo = responseInfo,
            )
        } catch (e: Exception) {
            process.destroy()
            if (process.isAlive) {
                process.destroyForcibly()
            }
            resources.cleanup()
            throw e
        }
    }

    private fun buildCurlRequestHeaders(
        credentials: IHttpBasedDownloadCredentials,
        start: Long?,
        end: Long?,
    ): LinkedHashMap<String, String> {
        val clearance = clearanceProvider.getClearanceFor(credentials.link)
            ?.takeUnless { it.isExpired() }
        val explicitReferer = credentials.headers
            ?.entries
            ?.firstOrNull { it.key.equals("Referer", ignoreCase = true) }
            ?.value
        val effectiveReferer = explicitReferer
            ?: credentials.downloadPage?.takeIf { it.isNotBlank() }
            ?: credentials.link.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}/" }
        val credentialsUserAgent = credentials.userAgent
            ?: credentials.headers
                ?.entries
                ?.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
                ?.value
        val effectiveUserAgent = clearance?.userAgent
            ?: credentialsUserAgent
            ?: customUserAgentProvider.getUserAgent()
            ?: getDefaultUserAgent()

        return linkedMapOf<String, String>().apply {
            defaultHeadersInFirst().forEach { (key, value) -> putHeader(key, value) }
            putHeader("User-Agent", effectiveUserAgent)
            credentials.headers
                ?.filterNot { it.key.equals("Host", ignoreCase = true) }
                ?.forEach { (key, value) -> putHeader(key, value) }
            clearance?.let {
                putHeader("Cookie", mergeCookies(credentials.headers, it.cookie))
            }
            if (explicitReferer == null) {
                effectiveReferer?.let { putHeader("Referer", it) }
            }
            defaultHeadersInLast().forEach { (key, value) -> putHeader(key, value) }
            if (!credentials.username.isNullOrBlank() && !credentials.password.isNullOrBlank()) {
                putHeader(
                    "Authorization",
                    Credentials.basic(credentials.username.orEmpty(), credentials.password.orEmpty()),
                )
            }
            putHeader("User-Agent", effectiveUserAgent)
            start?.let {
                putHeader("Range", createRangeHeader(it, end).second)
            }
        }
    }
}

private fun LinkedHashMap<String, String>.putHeader(name: String, value: String) {
    keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let(::remove)
    put(name, value)
}

internal fun buildCurlConfig(
    url: String,
    headers: Map<String, String>,
    proxy: ExternalProxy,
    headerFile: Path,
): String = buildString {
    append("silent\n")
    append("show-error\n")
    append("location\n")
    append("suppress-connect-headers\n")
    append("no-buffer\n")
    appendCurlValue("max-redirs", "10")
    appendCurlValue("proto", "=http,https")
    appendCurlValue("proto-redir", "=http,https")
    appendCurlValue("url", url)
    appendCurlValue("dump-header", headerFile.toString())
    appendCurlValue("output", "-")
    headers.forEach { (name, value) ->
        require(!name.contains(':') && !name.contains('\r') && !name.contains('\n')) {
            "Invalid HTTP header name"
        }
        appendCurlValue("header", "$name: $value")
    }
    when (proxy) {
        ExternalProxy.Direct -> appendCurlValue("noproxy", "*")
        is ExternalProxy.Endpoint -> {
            val scheme = when (proxy.type) {
                ProxyType.HTTP -> "http"
                ProxyType.SOCKS -> "socks5h"
            }
            val host = if (':' in proxy.host && !proxy.host.startsWith("[")) {
                "[${proxy.host}]"
            } else {
                proxy.host
            }
            appendCurlValue("proxy", "$scheme://$host:${proxy.port}")
            proxy.username?.let {
                appendCurlValue("proxy-user", "$it:${proxy.password.orEmpty()}")
                append("proxy-anyauth\n")
            }
        }

        ExternalProxy.Unsupported -> error("Unsupported proxy strategy")
    }
}

private fun StringBuilder.appendCurlValue(option: String, value: String) {
    require(!value.contains('\r') && !value.contains('\n')) {
        "Curl config values cannot contain line breaks"
    }
    val escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    append(option)
    append(" = \"")
    append(escaped)
    append("\"\n")
}

internal data class ParsedCurlResponse(
    val statusCode: Int,
    val message: String,
    val headers: Map<String, String>,
)

internal fun parseLastCompleteCurlResponse(rawHeaders: String): ParsedCurlResponse? {
    val normalized = rawHeaders.replace("\r\n", "\n")
    val completeEnd = normalized.lastIndexOf("\n\n")
    if (completeEnd < 0) {
        return null
    }
    val blocks = normalized.substring(0, completeEnd).split("\n\n")
    return blocks.mapNotNull { block ->
        val lines = block.lines().filter { it.isNotEmpty() }
        val status = lines.firstOrNull()?.let(HTTP_STATUS_REGEX::matchEntire)
            ?: return@mapNotNull null
        val headers = linkedMapOf<String, String>()
        lines.drop(1).forEach { line ->
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase()] =
                    line.substring(separator + 1).trim()
            }
        }
        ParsedCurlResponse(
            statusCode = status.groupValues[1].toInt(),
            message = status.groupValues.getOrElse(2) { "" },
            headers = headers,
        )
    }.lastOrNull()
}

private val HTTP_STATUS_REGEX = Regex("""HTTP/\S+\s+(\d{3})(?:\s+(.*))?""")

private suspend fun awaitCurlResponse(
    process: Process,
    resources: CurlProcessResources,
): ParsedCurlResponse {
    while (true) {
        currentCoroutineContext().ensureActive()
        val parsed = runCatching {
            if (Files.exists(resources.headerFile)) {
                parseLastCompleteCurlResponse(
                    Files.readString(resources.headerFile, StandardCharsets.ISO_8859_1),
                )
            } else {
                null
            }
        }.getOrNull()
        if (parsed != null && (parsed.statusCode !in 100..399 || !process.isAlive)) {
            return parsed
        }
        if (!process.isAlive) {
            val exitCode = process.exitValue()
            if (parsed != null) {
                return parsed
            }
            throw CurlUnavailableException(resources.failureMessage(exitCode))
        }
        delay(25)
    }
}

private class CurlProcessSource(
    private val process: Process,
    private val resources: CurlProcessResources,
) : Source {
    private val delegate = process.inputStream.source()
    private var closed = false

    override fun read(sink: Buffer, byteCount: Long): Long {
        val read = try {
            delegate.read(sink, byteCount)
        } catch (e: Exception) {
            close()
            throw e
        }
        if (read == -1L) {
            val exitCode = process.waitFor()
            val failure = if (exitCode == 0) null else resources.failureMessage(exitCode)
            close()
            if (failure != null) {
                throw IOException(failure)
            }
        }
        return read
    }

    override fun timeout(): Timeout = delegate.timeout()

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        runCatching { delegate.close() }
        if (process.isAlive) {
            process.destroy()
            runCatching { process.waitFor(200, TimeUnit.MILLISECONDS) }
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
        resources.cleanup()
    }
}

private data class CurlProcessResources(
    val tempDir: Path,
    val headerFile: Path,
    val errorFile: Path,
) {
    fun failureMessage(exitCode: Int): String {
        val stderr = runCatching {
            Files.readString(errorFile).trim().take(2_000)
        }.getOrDefault("")
        return buildString {
            append("curl exited with code ")
            append(exitCode)
            if (stderr.isNotEmpty()) {
                append(": ")
                append(stderr)
            }
        }
    }

    fun cleanup() {
        runCatching { Files.deleteIfExists(headerFile) }
        runCatching { Files.deleteIfExists(errorFile) }
        runCatching { Files.deleteIfExists(tempDir) }
    }
}

private class CurlUnavailableException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

private fun defaultCurlExecutable(): String {
    return if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
        "curl.exe"
    } else {
        "curl"
    }
}
