package ir.amirab.downloader.connection

import ir.amirab.downloader.connection.proxy.AutoConfigurableProxyProvider
import ir.amirab.downloader.connection.proxy.ProxyStrategy
import ir.amirab.downloader.connection.proxy.ProxyStrategyProvider
import ir.amirab.downloader.connection.proxy.ProxyType
import ir.amirab.downloader.connection.proxy.SystemProxySelectorProvider
import java.net.InetSocketAddress
import java.net.Proxy as JavaProxy
import java.net.ProxySelector
import java.net.URI

/** A proxy configuration that can be passed to curl or a Chromium process. */
sealed interface ExternalProxy {
    data object Direct : ExternalProxy
    data object Unsupported : ExternalProxy

    data class Endpoint(
        val type: ProxyType,
        val host: String,
        val port: Int,
        val username: String? = null,
        val password: String? = null,
    ) : ExternalProxy
}

/** Resolves AB Download Manager's per-URL proxy strategy for external network clients. */
class ExternalProxyResolver(
    private val proxyStrategyProvider: ProxyStrategyProvider,
    private val systemProxySelectorProvider: SystemProxySelectorProvider,
    private val autoConfigurableProxyProvider: AutoConfigurableProxyProvider,
) {
    fun resolve(url: String): ExternalProxy {
        return when (val strategy = proxyStrategyProvider.getProxyStrategyFor(url)) {
            ProxyStrategy.Direct -> ExternalProxy.Direct
            ProxyStrategy.UseSystem -> resolveWithSelector(
                systemProxySelectorProvider.getSystemProxySelector() ?: ProxySelector.getDefault(),
                url,
            )

            is ProxyStrategy.ByScript -> resolveWithSelector(
                autoConfigurableProxyProvider.getAutoConfigurableProxy(strategy.scriptPath),
                url,
            )

            is ProxyStrategy.ManualProxy -> strategy.proxy.let {
                ExternalProxy.Endpoint(
                    type = it.type,
                    host = it.host,
                    port = it.port,
                    username = it.username,
                    password = it.password,
                )
            }

            // A worker rewrites the HTTP request rather than acting as a forward proxy.
            // The normal OkHttp backend remains responsible for this strategy.
            is ProxyStrategy.CloudflareWorker -> ExternalProxy.Unsupported
        }
    }

    private fun resolveWithSelector(selector: ProxySelector?, url: String): ExternalProxy {
        if (selector == null) {
            return ExternalProxy.Direct
        }
        val selected = runCatching { selector.select(URI(url)) }.getOrNull()
            ?: return ExternalProxy.Unsupported
        val proxy = selected.firstOrNull { it.type() != JavaProxy.Type.DIRECT }
            ?: return ExternalProxy.Direct
        val address = proxy.address() as? InetSocketAddress
            ?: return ExternalProxy.Unsupported
        val type = when (proxy.type()) {
            JavaProxy.Type.HTTP -> ProxyType.HTTP
            JavaProxy.Type.SOCKS -> ProxyType.SOCKS
            JavaProxy.Type.DIRECT -> return ExternalProxy.Direct
        }
        return ExternalProxy.Endpoint(
            type = type,
            host = address.hostString,
            port = address.port,
        )
    }
}
