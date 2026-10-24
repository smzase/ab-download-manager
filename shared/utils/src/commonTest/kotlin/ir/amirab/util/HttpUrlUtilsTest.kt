package ir.amirab.util

import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpUrlUtilsTest {
    @Test
    fun explicitFilenameWinsOverPathAndContentDisposition() {
        val link = "https://example.com/path.zip".toHttpUrl().newBuilder()
            .addQueryParameter("FileName", "chosen name")
            .addQueryParameter("response-content-disposition", "attachment; filename=other.zip")
            .build()

        assertEquals("chosen name", HttpUrlUtils.extractNameFromLink(link.toString()))
    }

    @Test
    fun blankFilenameFallsBackToThePath() {
        assertEquals("path.zip", HttpUrlUtils.extractNameFromLink("https://example.com/path.zip?filename="))
    }

    @Test
    fun signedGatewayLinkUsesContentDisposition() {
        val link = "https://example.com/object/opaque-id".toHttpUrl().newBuilder()
            .addQueryParameter("response-content-disposition", "attachment; filename*=UTF-8''%E6%B8%B8%E6%88%8F.zip")
            .build()

        assertEquals("游戏.zip", HttpUrlUtils.extractNameFromLink(link.toString()))
    }

    @Test
    fun gatewayTokenAfterFilenameDoesNotBecomeTheName() {
        assertEquals(
            "游戏.zip",
            HttpUrlUtils.extractNameFromLink("https://example.com/download/%E6%B8%B8%E6%88%8F.zip/opaque-token"),
        )
    }

    @Test
    fun gatewayQueryNamesRemainSupported() {
        for (parameter in listOf("file", "name", "download", "f")) {
            assertEquals(
                "archive.zip",
                HttpUrlUtils.extractNameFromLink("https://example.com/download?$parameter=archive.zip"),
            )
        }
    }

    @Test
    fun doubleEncodedUnicodeFilenameIsDecoded() {
        assertEquals(
            "游戏.zip",
            HttpUrlUtils.extractNameFromLink("https://example.com/file?filename=%25E6%25B8%25B8%25E6%2588%258F.zip"),
        )
    }

    @Test
    fun encodedLiteralPlusIsPreserved() {
        assertEquals("C++.zip", HttpUrlUtils.extractNameFromLink("https://example.com/C%2B%2B.zip"))
    }

    @Test
    fun trailingSlashUsesTheLastNonemptyPathSegment() {
        assertEquals("download", HttpUrlUtils.extractNameFromLink("https://example.com/download/"))
    }

    @Test
    fun hostOnlyUrlUsesTheUpstreamFallback() {
        assertEquals("example_com", HttpUrlUtils.extractNameFromLink("https://example.com"))
    }

    @Test
    fun invalidUrlReturnsNull() {
        assertNull(HttpUrlUtils.extractNameFromLink("not a url"))
    }
}
