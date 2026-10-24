package ir.amirab.util

import kotlin.test.Test
import kotlin.test.assertEquals

class ContentDispositionUtilsTest {
    @Test
    fun extendedFilenameWinsOverAsciiFallback() {
        assertEquals(
            "游戏.zip",
            ContentDispositionUtils.extractFileName(
                "attachment; filename=fallback.zip; filename*=UTF-8''%E6%B8%B8%E6%88%8F.zip",
            ),
        )
    }

    @Test
    fun doubleEncodedHeaderIsDecodedWithoutReplacingPlus() {
        assertEquals(
            "游戏+C++.zip",
            ContentDispositionUtils.extractFileName(
                "attachment; filename*=UTF-8''%25E6%25B8%25B8%25E6%2588%258F+C++.zip",
            ),
        )
    }

    @Test
    fun mimeEncodedFilenameRemainsSupported() {
        assertEquals(
            "test file.zip",
            ContentDispositionUtils.extractFileName("attachment; filename=\"=?UTF-8?Q?test_file.zip?=\""),
        )
    }
}
