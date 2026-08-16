package ir.amirab.downloader.connection.clearance

/**
 * Credentials captured from a real browser after it solved an interactive
 * Cloudflare challenge (a `cf_mitigated: challenge` response) for some host.
 *
 * Cloudflare binds the `cf_clearance` cookie to the exact User-Agent that solved
 * the challenge (and to the client IP), so [cookie] and [userAgent] must always
 * travel together: replaying the cookie under a different User-Agent invalidates it.
 */
data class Clearance(
    /**
     * A ready to send `Cookie` header value, e.g. `cf_clearance=abc; foo=bar`.
     * `cf_clearance` itself is `HttpOnly`, so this can only be obtained from a
     * browser's cookie store, never by reading `document.cookie`.
     */
    val cookie: String,
    /**
     * Exact User-Agent of the browser that solved the challenge,
     * or null when it could not be determined.
     */
    val userAgent: String?,
    /**
     * Epoch millis after which this clearance is no longer usable,
     * or null when the lifetime is unknown.
     */
    val expiresAt: Long?,
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
        return expiresAt != null && now >= expiresAt
    }
}

/**
 * Supplies a previously obtained [Clearance] at request time.
 *
 * This is consulted for every request, including each part of a segmented download,
 * so a single solved challenge serves the whole download without having to be
 * written into the persisted download item (where it would quickly go stale —
 * a clearance typically lives for about half an hour).
 */
interface ClearanceProvider {
    /**
     * The usable clearance for [url]'s host, or null when there is none.
     * Implementations are responsible for not returning expired entries.
     */
    fun getClearanceFor(url: String): Clearance?

    class NoOp : ClearanceProvider {
        override fun getClearanceFor(url: String): Clearance? = null
    }
}
