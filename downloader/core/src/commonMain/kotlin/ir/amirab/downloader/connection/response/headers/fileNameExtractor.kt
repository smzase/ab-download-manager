package ir.amirab.downloader.connection.response.headers

import ir.amirab.util.ContentDispositionUtils

fun extractFileNameFromContentDisposition(contentDispositionValue: String): String? {
    return ContentDispositionUtils.extractFileName(contentDispositionValue)
}
