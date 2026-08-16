package com.abdownloadmanager.desktop.utils.clearance

import com.abdownloadmanager.desktop.window.ChromeBrowser
import com.abdownloadmanager.desktop.window.EdgeBrowser
import com.abdownloadmanager.shared.util.DefinedPaths
import ir.amirab.downloader.connection.ExternalProxy
import ir.amirab.downloader.connection.ExternalProxyResolver
import ir.amirab.downloader.connection.clearance.ChallengeSolver
import ir.amirab.downloader.connection.clearance.Clearance
import ir.amirab.downloader.connection.clearance.ClearanceStore
import ir.amirab.downloader.connection.proxy.ProxyType
import ir.amirab.util.logger.appLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Opens an installed Chrome or Edge with a dedicated temporary profile so the user can
 * complete a real Cloudflare challenge. The resulting HttpOnly cookies are read through
 * the local DevTools protocol and stored for the curl compatibility backend.
 */
class ChromiumChallengeSolver(
    private val clearanceStore: ClearanceStore,
    private val proxyResolver: ExternalProxyResolver,
    private val okHttpClient: OkHttpClient,
    private val definedPaths: DefinedPaths,
) : ChallengeSolver {
    private val logger = appLogger.withTag("ChromiumChallengeSolver")
    private val solveMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun solve(url: String): Boolean = solveMutex.withLock {
        clearanceStore.getClearanceFor(url)?.let { return@withLock true }
        try {
            solveInBrowser(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Unable to solve Cloudflare challenge in Chromium" }
            false
        }
    }

    private suspend fun solveInBrowser(url: String): Boolean = withContext(Dispatchers.IO) {
        val targetUrl = url.toHttpUrlOrNull() ?: return@withContext false
        val executable = findChromiumExecutable() ?: return@withContext false
        val proxy = proxyResolver.resolve(url)
        if (proxy == ExternalProxy.Unsupported) {
            return@withContext false
        }

        val profilesRoot = definedPaths.systemDir.toFile().toPath()
            .resolve("cloudflare-browser")
            .toAbsolutePath()
            .normalize()
        Files.createDirectories(profilesRoot)
        val profile = Files.createTempDirectory(profilesRoot, "challenge-")
            .toAbsolutePath()
            .normalize()
        val process = startBrowser(executable, profile, proxy)
        var cdp: CdpClient? = null
        try {
            val pageWebSocketUrl = waitForPageWebSocket(profile, process)
            cdp = CdpClient.connect(okHttpClient, json, pageWebSocketUrl)
            cdp.command("Page.enable")
            cdp.command("Network.enable")
            denyBrowserDownloads(cdp)
            val userAgent = readUserAgent(cdp)
            cdp.command(
                "Page.navigate",
                buildJsonObject { put("url", url) },
            )

            val deadline = System.currentTimeMillis() + SOLVE_TIMEOUT_MILLIS
            while (process.isAlive && System.currentTimeMillis() < deadline) {
                val cookies = readCookies(cdp)
                val clearanceCookie = cookies.firstOrNull {
                    it.name == "cf_clearance" && it.matches(targetUrl.host, targetUrl.encodedPath)
                }
                if (clearanceCookie != null) {
                    val applicableCookies = cookies
                        .filter { it.matches(targetUrl.host, targetUrl.encodedPath) }
                        .sortedByDescending { it.path.length }
                    clearanceStore.put(
                        clearanceCookie.domain,
                        Clearance(
                            cookie = applicableCookies.joinToString("; ") { "${it.name}=${it.value}" },
                            userAgent = userAgent,
                            expiresAt = clearanceCookie.expiresAt,
                        ),
                    )
                    return@withContext true
                }
                delay(COOKIE_POLL_INTERVAL_MILLIS)
            }
            false
        } finally {
            cdp?.let {
                runCatching { it.command("Browser.close") }
                it.close()
            }
            stopProcess(process)
            cleanupProfile(profilesRoot, profile)
        }
    }

    private fun startBrowser(
        executable: File,
        profile: Path,
        proxy: ExternalProxy,
    ): Process {
        val command = buildList {
            add(executable.absolutePath)
            add("--user-data-dir=$profile")
            add("--remote-debugging-address=127.0.0.1")
            add("--remote-debugging-port=0")
            add("--remote-allow-origins=*")
            add("--no-first-run")
            add("--no-default-browser-check")
            add("--disable-default-apps")
            add("--app=about:blank")
            add("--window-size=960,720")
            when (proxy) {
                ExternalProxy.Direct -> add("--no-proxy-server")
                is ExternalProxy.Endpoint -> {
                    val scheme = when (proxy.type) {
                        ProxyType.HTTP -> "http"
                        ProxyType.SOCKS -> "socks5"
                    }
                    val host = if (':' in proxy.host && !proxy.host.startsWith("[")) {
                        "[${proxy.host}]"
                    } else {
                        proxy.host
                    }
                    add("--proxy-server=$scheme://$host:${proxy.port}")
                }

                ExternalProxy.Unsupported -> error("Unsupported proxy strategy")
            }
        }
        return ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    }

    private suspend fun waitForPageWebSocket(profile: Path, process: Process): String {
        val activePortFile = profile.resolve("DevToolsActivePort")
        val deadline = System.currentTimeMillis() + DEVTOOLS_START_TIMEOUT_MILLIS
        var port: Int? = null
        while (process.isAlive && System.currentTimeMillis() < deadline) {
            port = runCatching {
                Files.readAllLines(activePortFile).firstOrNull()?.toIntOrNull()
            }.getOrNull()
            if (port != null) {
                break
            }
            delay(50)
        }
        val devToolsPort = port ?: throw IOException("Chromium DevTools did not start")

        while (process.isAlive && System.currentTimeMillis() < deadline) {
            fetchPageWebSocket(devToolsPort)?.let { return it }
            delay(50)
        }
        throw IOException("Chromium did not expose a page target")
    }

    private fun fetchPageWebSocket(port: Int): String? {
        val request = Request.Builder()
            .url("http://127.0.0.1:$port/json/list")
            .build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use null
                }
                val targets = json.parseToJsonElement(response.body.string()).jsonArray
                targets.firstNotNullOfOrNull { target ->
                    val item = target.jsonObject
                    if (item["type"]?.jsonPrimitive?.contentOrNull == "page") {
                        item["webSocketDebuggerUrl"]?.jsonPrimitive?.contentOrNull
                    } else {
                        null
                    }
                }
            }
        }.getOrNull()
    }

    private suspend fun denyBrowserDownloads(cdp: CdpClient) {
        val params = buildJsonObject {
            put("behavior", "deny")
            put("eventsEnabled", true)
        }
        try {
            cdp.command("Browser.setDownloadBehavior", params)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            cdp.command(
                "Page.setDownloadBehavior",
                buildJsonObject { put("behavior", "deny") },
            )
        }
    }

    private suspend fun readUserAgent(cdp: CdpClient): String? {
        val result = cdp.command(
            "Runtime.evaluate",
            buildJsonObject {
                put("expression", "navigator.userAgent")
                put("returnByValue", true)
            },
        )
        return result["result"]?.jsonObject
            ?.get("value")?.jsonPrimitive?.contentOrNull
    }

    private suspend fun readCookies(cdp: CdpClient): List<CdpCookie> {
        val result = cdp.command("Network.getAllCookies")
        val now = System.currentTimeMillis()
        return result["cookies"]?.jsonArray.orEmpty().mapNotNull { element ->
            val cookie = element.jsonObject
            val name = cookie["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val value = cookie["value"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val domain = cookie["domain"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val expiresSeconds = cookie["expires"]?.jsonPrimitive?.doubleOrNull
            val expiresAt = expiresSeconds
                ?.takeIf { it > 0.0 }
                ?.let { (it * 1_000.0).toLong() }
            if (expiresAt != null && expiresAt <= now) {
                return@mapNotNull null
            }
            CdpCookie(
                name = name,
                value = value,
                domain = domain,
                path = cookie["path"]?.jsonPrimitive?.contentOrNull ?: "/",
                expiresAt = expiresAt,
            )
        }
    }

    private fun findChromiumExecutable(): File? {
        return listOf(
            ChromeBrowser to "Google Chrome",
            EdgeBrowser to "Microsoft Edge",
        ).firstNotNullOfOrNull { (browser, macExecutableName) ->
            val installed = browser.getExecutablePath() ?: return@firstNotNullOfOrNull null
            val executable = if (installed.isDirectory) {
                File(installed, "Contents/MacOS/$macExecutableName")
            } else {
                installed
            }
            executable.takeIf { it.isFile }
        }
    }

    private fun stopProcess(process: Process) {
        if (!process.isAlive) {
            return
        }
        process.destroy()
        runCatching { process.waitFor(2, TimeUnit.SECONDS) }
        if (process.isAlive) {
            process.destroyForcibly()
        }
    }

    private fun cleanupProfile(root: Path, profile: Path) {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val normalizedProfile = profile.toAbsolutePath().normalize()
        if (normalizedProfile.parent != normalizedRoot) {
            return
        }
        runCatching { normalizedProfile.toFile().deleteRecursively() }
    }

    private data class CdpCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long?,
    ) {
        fun matches(host: String, requestPath: String): Boolean {
            val normalizedDomain = domain.removePrefix(".").lowercase()
            val domainMatches = host.equals(normalizedDomain, ignoreCase = true) ||
                host.lowercase().endsWith(".$normalizedDomain")
            return domainMatches && requestPath.startsWith(path)
        }
    }

    companion object {
        private const val DEVTOOLS_START_TIMEOUT_MILLIS = 15_000L
        private const val SOLVE_TIMEOUT_MILLIS = 5 * 60_000L
        private const val COOKIE_POLL_INTERVAL_MILLIS = 500L
    }
}

private class CdpClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val url: String,
) : WebSocketListener(), Closeable {
    private val nextId = AtomicLong(0)
    private val opened = CompletableDeferred<Unit>()
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonObject>>()
    private lateinit var webSocket: WebSocket

    private suspend fun open() {
        webSocket = okHttpClient.newWebSocket(
            Request.Builder().url(url).build(),
            this,
        )
        withTimeout(COMMAND_TIMEOUT_MILLIS) { opened.await() }
    }

    suspend fun command(
        method: String,
        params: JsonObject = buildJsonObject {},
    ): JsonObject {
        val id = nextId.incrementAndGet()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        val message = buildJsonObject {
            put("id", id)
            put("method", method)
            put("params", params)
        }
        if (!webSocket.send(message.toString())) {
            pending.remove(id)
            throw IOException("Unable to send DevTools command $method")
        }
        val response = try {
            withTimeout(COMMAND_TIMEOUT_MILLIS) { deferred.await() }
        } finally {
            pending.remove(id)
        }
        response["error"]?.let {
            throw IOException("DevTools command $method failed: $it")
        }
        return response["result"]?.jsonObject ?: buildJsonObject {}
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        opened.complete(Unit)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val message = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return
        val id = message["id"]?.jsonPrimitive?.longOrNull ?: return
        pending.remove(id)?.complete(message)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        opened.completeExceptionally(t)
        pending.values.forEach { it.completeExceptionally(t) }
        pending.clear()
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        val exception = IOException("DevTools connection closed: $reason")
        pending.values.forEach { it.completeExceptionally(exception) }
        pending.clear()
    }

    override fun close() {
        if (::webSocket.isInitialized) {
            webSocket.close(1000, "Challenge finished")
        }
    }

    companion object {
        private const val COMMAND_TIMEOUT_MILLIS = 10_000L

        suspend fun connect(
            okHttpClient: OkHttpClient,
            json: Json,
            url: String,
        ): CdpClient {
            return CdpClient(okHttpClient, json, url).also { it.open() }
        }
    }
}
