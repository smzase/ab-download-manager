package ir.amirab.downloader.connection

import ir.amirab.downloader.connection.clearance.ClearanceProvider
import ir.amirab.downloader.connection.proxy.*
import ir.amirab.downloader.connection.response.HttpResponseInfo
import ir.amirab.downloader.downloaditem.http.IHttpBasedDownloadCredentials
import ir.amirab.downloader.downloaditem.http.IHttpDownloadCredentials
import ir.amirab.downloader.utils.await
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector

class OkHttpHttpDownloaderClient(
    private val okHttpClient: OkHttpClient,
    private val customUserAgentProvider: UserAgentProvider,
    private val proxyStrategyProvider: ProxyStrategyProvider,
    private val systemProxySelectorProvider: SystemProxySelectorProvider,
    private val autoConfigurableProxyProvider: AutoConfigurableProxyProvider,
    private val clearanceProvider: ClearanceProvider = ClearanceProvider.NoOp(),
) : HttpDownloaderClient() {
    private fun newCall(
        downloadCredentials: IHttpBasedDownloadCredentials,
        start: Long?,
        end: Long?,
        extraBuilder: Request.Builder.() -> Unit,
    ): Call {
        val rangeHeader = start?.let {
            createRangeHeader(start, end)
        }
        return okHttpClient
            .applyProxy(downloadCredentials)
            .withAntiLeechRetry()
            .newCall(
                Request.Builder()
                    .url(downloadCredentials.link)
                    .apply {
                        val clearance = clearanceProvider
                            .getClearanceFor(downloadCredentials.link)
                            ?.takeUnless { it.isExpired() }
                        val credentialsReferer = downloadCredentials.headers
                            ?.entries
                            ?.firstOrNull { it.key.equals("Referer", true) }
                            ?.value
                        val credentialsUserAgent = downloadCredentials.userAgent
                            ?: downloadCredentials.headers
                                ?.entries
                                ?.firstOrNull { it.key.equals("User-Agent", true) }
                                ?.value
                        // Referer: the explicit one, else downloadPage, else the download URL origin.
                        // Many download gateways use Referer-based anti-leech protection;
                        // without it they return 403 Access Denied.
                        val effectiveReferer = credentialsReferer
                            ?: downloadCredentials.downloadPage?.takeIf { it.isNotBlank() }
                            ?: downloadCredentials.link.toHttpUrlOrNull()
                                ?.let { "${it.scheme}://${it.host}/" }
                        // a clearance is bound to the User-Agent that solved the challenge,
                        // so it takes precedence over the configured/default one
                        val effectiveUserAgent = clearance?.userAgent
                            ?: credentialsUserAgent
                            ?: customUserAgentProvider.getUserAgent()
                            ?: getDefaultUserAgent()

                        defaultHeadersInFirst().forEach { (k, v) ->
                            header(k, v)
                        }
                        // we don't to add something that we sure that it will be overridden later
                        if (downloadCredentials.userAgent == null) {
                            // only add default user agent if we don't specify it
                            header("User-Agent", effectiveUserAgent)
                        }
                        downloadCredentials.headers
                            ?.filter {
                                //OkHttp handles this header and if we override it,
                                //makes redirected links to have this "Host" instead of their own!, and cause error
                                !it.key.equals("Host", true)
                            }
                            ?.forEach { (k, v) ->
                                header(k, v)
                            }
                        // Merge the clearance cookie with any per-download Cookie header instead of
                        // replacing it, so an explicitly configured cookie and the challenge cookie
                        // can coexist. Note we deliberately don't install an OkHttp CookieJar here:
                        // a non-empty jar makes BridgeInterceptor overwrite the Cookie header we set,
                        // silently dropping downloadCredentials.headers["Cookie"].
                        clearance?.let {
                            header("Cookie", mergeCookies(downloadCredentials.headers, it.cookie))
                        }
                        if (credentialsReferer == null) {
                            effectiveReferer?.let { header("Referer", it) }
                        }
                        defaultHeadersInLast().forEach { (k, v) ->
                            header(k, v)
                        }
                        val username = downloadCredentials.username
                        val password = downloadCredentials.password
                        if (username?.isNotBlank() == true && password?.isNotBlank() == true) {
                            header("Authorization", Credentials.basic(username, password))
                        }
                        header("User-Agent", effectiveUserAgent)
                    }
                    .apply(extraBuilder)
                    .apply {
                        if (rangeHeader != null) {
                            header(rangeHeader.first, rangeHeader.second)
                        }
                    }
                    .build()
            )
    }

    private fun OkHttpClient.applyProxy(
        downloadCredentials: IHttpBasedDownloadCredentials,
    ): OkHttpClient {
        return when (
            val strategy = proxyStrategyProvider.getProxyStrategyFor(downloadCredentials.link)
        ) {
            ProxyStrategy.Direct -> return this
            ProxyStrategy.UseSystem -> {
                newBuilder()
                    .proxySelector(
                        systemProxySelectorProvider.getSystemProxySelector()
                            ?: ProxySelector.getDefault()
                    )
                    .build()
            }

            is ProxyStrategy.ByScript -> {
                val proxySelector = autoConfigurableProxyProvider.getAutoConfigurableProxy(strategy.scriptPath)
                if (proxySelector != null) {
                    newBuilder()
                        .proxySelector(proxySelector)
                        .build()
                } else {
                    this
                }
            }

            is ProxyStrategy.ManualProxy -> {
                val proxy = strategy.proxy
                return newBuilder()
                    .proxy(
                        Proxy(
                            when (proxy.type) {
                                ProxyType.HTTP -> Proxy.Type.HTTP
                                ProxyType.SOCKS -> Proxy.Type.SOCKS
                            },
                            InetSocketAddress(proxy.host, proxy.port)
                        )
                    ).let {
                        if (proxy.username != null && proxy.type == ProxyType.HTTP) {
                            it.proxyAuthenticator { _, r ->
                                val credentials = Credentials.basic(
                                    proxy.username,
                                    proxy.password.orEmpty()
                                )
                                r.request
                                    .newBuilder()
                                    .header("Proxy-Authorization", credentials)
                                    .build()
                            }
                        } else {
                            it
                        }
                    }.build()
            }

            is ProxyStrategy.CloudflareWorker -> {
                return newBuilder()
                    .addInterceptor { chain ->
                        val originalRequest = chain.request()
                        val originalUrl = originalRequest.url.toString()

                        // Build worker URL with target parameter
                        val workerUrl = strategy.workerUrl
                            .let {
                                if (it.endsWith("/")) it else "$it/"
                            }
                            .let {
                                it.toHttpUrlOrNull()
                                    ?.newBuilder()
                                    ?.addQueryParameter("target", originalUrl)
                                    ?.build()
                            }

                        val newRequest = if (workerUrl != null) {
                            originalRequest.newBuilder()
                                .url(workerUrl)
                                .apply {
                                    // Add API token if provided
                                    strategy.apiToken?.let { token ->
                                        header("X-Worker-Token", token)
                                    }
                                    // Add original URL as header for reference
                                    header("X-Original-URL", originalUrl)
                                }
                                .build()
                        } else {
                            originalRequest
                        }

                        chain.proceed(newRequest)
                    }
                    .build()
            }
        }
    }

    /**
     * Anti-leech (防盗链) retry interceptor.
     *
     * Some download gateways (e.g. touchgaldownload.xyz behind Cloudflare) reject
     * requests whose Referer is not in their whitelist, returning 403 Access Denied.
     * When the gateway also sends an `Access-Control-Allow-Origin` header (which
     * reveals the expected origin), we automatically retry the request with that
     * origin as the Referer.
     *
     * This complements the explicit Referer logic in [newCall] (which uses
     * downloadPage or URL origin). When the explicit Referer is wrong, this
     * interceptor recovers automatically by learning the correct origin from the
     * 403 response itself.
     *
     * The retry runs at most once: the retried request already carries
     * `Referer == allowOrigin`, so the `currentReferer != allowOrigin` guard
     * prevents a second retry.
     *
     * Interactive Cloudflare challenges are marked with `Cf-Mitigated: challenge`.
     * They cannot be solved by changing Referer, so they are left untouched for
     * the response layer to report with an actionable error.
     */
    private fun OkHttpClient.withAntiLeechRetry(): OkHttpClient {
        return newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                val retryReferer = getAntiLeechRetryReferer(request, response)
                if (retryReferer != null) {
                    response.close()
                    val retryRequest = request.newBuilder()
                        .header("Referer", retryReferer)
                        .build()
                    return@addInterceptor chain.proceed(retryRequest)
                }
                response
            }
            .build()
    }


    override suspend fun actualHead(
        credentials: IHttpDownloadCredentials,
        start: Long?,
        end: Long?,
    ): HttpResponseInfo {
        newCall(
            downloadCredentials = credentials,
            start = start,
            end = end,
            extraBuilder = {
//                head()
            }
        ).await().use { response ->
//            println(response.headers)
            return createFileInfo(response)
        }
    }

    private fun createFileInfo(response: Response): HttpResponseInfo {
        // Get the original URL from X-Original-URL header (set by Cloudflare Worker proxy)
        // or fall back to the actual request URL
        val originalUrl = response.request.header("X-Original-URL")
            ?: response.request.url.toString()

        return HttpResponseInfo(
            statusCode = response.code,
            message = response.message,
            requestUrl = originalUrl,
            requestHeaders = response.request.headers.associate { (key, value) ->
                key.lowercase() to value
            },
            responseHeaders = response.headers.associate { (key, value) ->
                key.lowercase() to value
            },
        )
    }

    override suspend fun actualConnect(
        credentials: IHttpBasedDownloadCredentials,
        start: Long?,
        end: Long?,
    ): Connection<HttpResponseInfo> {
        val response = newCall(
            downloadCredentials = credentials,
            start = start,
            end = end,
            extraBuilder = {
                get()
            }
        ).await()
        val body = runCatching {
            requireNotNull(response.body) {
                "body is null"
            }
        }.onFailure {
            response.close()
        }.getOrThrow()
        return Connection(
            source = body.source(),
            contentLength = body.contentLength(),
            responseInfo = createFileInfo(response)
        )
    }
}

internal fun getAntiLeechRetryReferer(
    request: Request,
    response: Response,
): String? {
    if (response.code != 403) return null
    if (response.header("Cf-Mitigated").equals("challenge", ignoreCase = true)) return null

    val allowOrigin = response.header("Access-Control-Allow-Origin")
        ?.takeIf { it.isNotBlank() && it != "*" }
        ?: return null
    return allowOrigin.takeIf { request.header("Referer") != it }
}

/**
 * Combines an explicitly configured `Cookie` header with a clearance cookie.
 *
 * Cookies the clearance defines replace same-named ones from [headers] rather than being
 * appended after them: a stale `cf_clearance` may well be sitting in the per-download
 * headers, and servers typically honour the *first* occurrence of a duplicated cookie
 * name, which would shadow the freshly obtained one.
 *
 * Raw segments are preserved verbatim so values that themselves contain `=`
 * (base64, as `cf_clearance` is) are never mangled.
 */
internal fun mergeCookies(
    headers: Map<String, String>?,
    clearanceCookie: String,
): String {
    val existing = headers
        ?.entries
        ?.firstOrNull { it.key.equals("Cookie", ignoreCase = true) }
        ?.value
        ?.takeIf { it.isNotBlank() }
        ?: return clearanceCookie
    val clearanceNames = clearanceCookie.splitCookieSegments()
        .map { it.cookieName() }
        .filter { it.isNotEmpty() }
        .toSet()
    val kept = existing.splitCookieSegments()
        .filterNot { it.cookieName() in clearanceNames }
    return (kept + clearanceCookie).joinToString("; ")
}

private fun String.splitCookieSegments(): List<String> {
    return split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

/** cookie names are case sensitive, so this is compared exactly */
private fun String.cookieName(): String {
    return substringBefore('=').trim()
}
