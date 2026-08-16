package ir.amirab.downloader.connection.clearance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClearanceStoreTest {
    @Test
    fun aClearanceIsServedForTheExactHostItWasStoredFor() {
        val store = store()
        store.put("files.example.com", clearance("exact"))

        assertEquals("exact", store.cookieFor("https://files.example.com/a.zip"))
    }

    @Test
    fun aLeadingDotMakesTheClearanceCoverSubdomains() {
        // this is how Cloudflare scopes cf_clearance, and it's what lets one solved
        // challenge cover a gateway that hands out randomised download subdomains
        val store = store()
        store.put(".touchgaldownload.xyz", clearance("wildcard"))

        assertEquals("wildcard", store.cookieFor("https://tqkzm1ocop.touchgaldownload.xyz/a.zip"))
        assertEquals("wildcard", store.cookieFor("https://8w1ip6bygq.touchgaldownload.xyz/a.zip"))
        assertEquals("wildcard", store.cookieFor("https://touchgaldownload.xyz/a.zip"))
    }

    @Test
    fun withoutALeadingDotSubdomainsAreNotCovered() {
        val store = store()
        store.put("touchgaldownload.xyz", clearance("host-only"))

        assertNull(store.cookieFor("https://tqkzm1ocop.touchgaldownload.xyz/a.zip"))
    }

    @Test
    fun aSiblingDomainIsNeverMatched() {
        val store = store()
        store.put(".example.com", clearance("c"))

        assertNull(store.cookieFor("https://notexample.com/a.zip"))
        assertNull(store.cookieFor("https://example.com.evil.test/a.zip"))
    }

    @Test
    fun theMostSpecificDomainWins() {
        val store = store()
        store.put(".example.com", clearance("broad"))
        store.put(".files.example.com", clearance("specific"))

        assertEquals("specific", store.cookieFor("https://a.files.example.com/x.zip"))
        assertEquals("broad", store.cookieFor("https://other.example.com/x.zip"))
    }

    @Test
    fun anExpiredClearanceIsNotServed() {
        val store = store()
        store.put("files.example.com", clearance("stale", expiresAt = -1L))

        assertNull(store.cookieFor("https://files.example.com/a.zip"))
    }

    @Test
    fun aClearanceAboutToExpireIsTreatedAsAlreadySpent() {
        // a segmented download opening a fresh connection on the expiry boundary would
        // otherwise get challenged mid-flight, so there is a safety margin
        val store = store()
        store.put("files.example.com", clearance("expiring", expiresAt = 10_000L))

        assertNull(store.cookieFor("https://files.example.com/a.zip"))
    }

    @Test
    fun aClearanceComfortablyInTheFutureIsServed() {
        val store = store()
        store.put("files.example.com", clearance("fresh", expiresAt = 10 * 60_000L))

        assertEquals("fresh", store.cookieFor("https://files.example.com/a.zip"))
    }

    @Test
    fun anExpiredSpecificEntryFallsBackToAStillValidBroaderOne() {
        val store = store()
        store.put(".example.com", clearance("broad", expiresAt = 10 * 60_000L))
        store.put(".files.example.com", clearance("specific", expiresAt = -1L))

        assertEquals("broad", store.cookieFor("https://a.files.example.com/x.zip"))
    }

    @Test
    fun domainsAndHostsAreMatchedCaseInsensitively() {
        val store = store()
        store.put(".Example.COM", clearance("c"))

        assertEquals("c", store.cookieFor("https://FILES.example.com/a.zip"))
    }

    @Test
    fun aMalformedUrlYieldsNothingRatherThanThrowing() {
        val store = store()
        store.put(".example.com", clearance("c"))

        assertNull(store.cookieFor("not a url"))
        assertNull(store.cookieFor(""))
    }

    @Test
    fun entriesCanBeRemovedIndividuallyAndInBulk() {
        val store = store()
        store.put(".example.com", clearance("a"))
        store.put(".other.test", clearance("b"))

        store.remove(".example.com")
        assertNull(store.cookieFor("https://x.example.com/a.zip"))
        assertEquals("b", store.cookieFor("https://x.other.test/a.zip"))

        store.clear()
        assertNull(store.cookieFor("https://x.other.test/a.zip"))
    }

    /** a store whose clock is pinned to 0, so expiry cases are exact */
    private fun store() = ClearanceStore(now = { 0L })

    private fun ClearanceStore.cookieFor(url: String): String? {
        return getClearanceFor(url)?.cookie
    }

    private fun clearance(
        cookie: String,
        expiresAt: Long? = null,
    ) = Clearance(
        cookie = cookie,
        userAgent = "ua",
        expiresAt = expiresAt,
    )
}
