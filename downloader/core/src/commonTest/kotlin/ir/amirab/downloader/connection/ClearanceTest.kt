package ir.amirab.downloader.connection

import ir.amirab.downloader.connection.clearance.Clearance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClearanceTest {
    @Test
    fun aClearanceWithoutAKnownLifetimeNeverExpires() {
        val clearance = clearance(expiresAt = null)
        assertFalse(clearance.isExpired(now = Long.MAX_VALUE))
    }

    @Test
    fun expiryIsInclusiveSoAClearanceIsNotUsedOnItsDeadline() {
        val clearance = clearance(expiresAt = 1_000L)
        assertFalse(clearance.isExpired(now = 999L))
        assertTrue(clearance.isExpired(now = 1_000L))
        assertTrue(clearance.isExpired(now = 1_001L))
    }

    private fun clearance(expiresAt: Long?) = Clearance(
        cookie = "cf_clearance=abc",
        userAgent = null,
        expiresAt = expiresAt,
    )
}

class MergeCookiesTest {
    @Test
    fun withoutAnExistingCookieHeaderTheClearanceCookieIsUsedAsIs() {
        assertEquals("cf_clearance=abc", mergeCookies(null, "cf_clearance=abc"))
        assertEquals("cf_clearance=abc", mergeCookies(emptyMap(), "cf_clearance=abc"))
        assertEquals(
            "cf_clearance=abc",
            mergeCookies(mapOf("Cookie" to "   "), "cf_clearance=abc"),
        )
    }

    @Test
    fun unrelatedPerDownloadCookiesAreKept() {
        assertEquals(
            "session=xyz; locale=fa; cf_clearance=abc",
            mergeCookies(mapOf("Cookie" to "session=xyz; locale=fa"), "cf_clearance=abc"),
        )
    }

    @Test
    fun theCookieHeaderIsFoundRegardlessOfItsCasing() {
        assertEquals(
            "session=xyz; cf_clearance=abc",
            mergeCookies(mapOf("cookie" to "session=xyz"), "cf_clearance=abc"),
        )
    }

    @Test
    fun aStaleClearanceInThePerDownloadHeadersIsDroppedNotShadowed() {
        // this is the whole point of merging by name: servers honour the *first* occurrence
        // of a duplicated cookie name, so simply appending would let the stale value win
        val merged = mergeCookies(
            mapOf("Cookie" to "session=xyz; cf_clearance=STALE"),
            "cf_clearance=FRESH",
        )
        assertEquals("session=xyz; cf_clearance=FRESH", merged)
        assertFalse(merged.contains("STALE"))
    }

    @Test
    fun cookieValuesContainingEqualsSignsSurviveIntact() {
        // cf_clearance is base64 and routinely ends in padding
        val clearanceCookie = "cf_clearance=aGVsbG8=.abc-def_123"
        val merged = mergeCookies(mapOf("Cookie" to "tok=YWJj=="), clearanceCookie)
        assertEquals("tok=YWJj==; cf_clearance=aGVsbG8=.abc-def_123", merged)
    }

    @Test
    fun cookieNamesAreComparedCaseSensitively() {
        // per RFC 6265 cookie names are case sensitive, so these are two different cookies
        val merged = mergeCookies(mapOf("Cookie" to "CF_Clearance=other"), "cf_clearance=abc")
        assertEquals("CF_Clearance=other; cf_clearance=abc", merged)
    }

    @Test
    fun blankSegmentsAndStraySpacingAreNormalised() {
        val merged = mergeCookies(mapOf("Cookie" to " a=1 ;; b=2 ; "), "c=3")
        assertEquals("a=1; b=2; c=3", merged)
    }
}
