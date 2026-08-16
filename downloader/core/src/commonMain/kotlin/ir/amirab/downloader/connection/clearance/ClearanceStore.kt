package ir.amirab.downloader.connection.clearance

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds the clearances obtained by solving interactive Cloudflare challenges, scoped the way
 * cookies actually are, so a single solved challenge covers every download from that site —
 * including each connection of a segmented download.
 *
 * Deliberately in-memory only. A clearance is bound to the exit IP it was solved on and
 * typically lives for about half an hour, so persisting it across restarts would mostly
 * resurrect entries that are guaranteed to be rejected.
 */
class ClearanceStore(
    private val now: () -> Long = System::currentTimeMillis,
) : ClearanceProvider {
    private val entries = ConcurrentHashMap<String, Entry>()

    override fun getClearanceFor(url: String): Clearance? {
        val host = url.toHttpUrlOrNull()?.host?.lowercase() ?: return null
        // treat a clearance as spent slightly early: a long download opening a fresh
        // connection right on the expiry boundary would otherwise be challenged mid-flight
        val deadline = now() + EXPIRY_MARGIN_MILLIS
        return entries.values
            .filter { it.matches(host) }
            // most specific domain first, mirroring how a browser orders cookies
            .sortedByDescending { it.domain.length }
            .firstOrNull { !it.clearance.isExpired(deadline) }
            ?.clearance
    }

    /**
     * @param domain the cookie domain as the browser reported it. A leading dot means the
     * cookie also applies to subdomains, which is how Cloudflare scopes `cf_clearance`.
     */
    fun put(domain: String, clearance: Clearance) {
        val normalized = domain.removePrefix(".").lowercase()
        if (normalized.isEmpty()) {
            return
        }
        entries[normalized] = Entry(
            domain = normalized,
            includeSubdomains = domain.startsWith("."),
            clearance = clearance,
        )
    }

    fun remove(domain: String) {
        entries.remove(domain.removePrefix(".").lowercase())
    }

    fun clear() {
        entries.clear()
    }

    private data class Entry(
        val domain: String,
        val includeSubdomains: Boolean,
        val clearance: Clearance,
    ) {
        fun matches(host: String): Boolean {
            if (host == domain) {
                return true
            }
            return includeSubdomains && host.endsWith(".$domain")
        }
    }

    companion object {
        private const val EXPIRY_MARGIN_MILLIS = 30_000L
    }
}
