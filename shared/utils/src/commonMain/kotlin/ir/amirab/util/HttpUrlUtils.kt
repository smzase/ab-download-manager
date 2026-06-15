package ir.amirab.util

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

object HttpUrlUtils {
    fun createURL(url: String): HttpUrl {
        return url.toHttpUrl()
    }

    fun isValidUrl(link: String): Boolean {
        return runCatching { createURL(link) }.isSuccess
    }

    fun extractNameFromLink(link: String): String? {
        return runCatching {
            createURL(link)
        }.map { url ->
            url.extractResponseContentDispositionFileName()?.let {
                return@map it
            }

            val fileNameFromPath = url.pathSegments
                .asReversed()
                .firstNotNullOfOrNull { segment ->
                    segment
                        .takeIf { it.isNotBlank() }
                        ?.let(::decodeFileName)
                        ?.takeIf(::looksLikeFileName)
                }

            if (fileNameFromPath != null) {
                return@map fileNameFromPath
            }

            for (param in fileNameQueryParams) {
                url.queryParameterIgnoreCase(param)?.let { value ->
                    val fileName = decodeFileName(value)
                    if (looksLikeFileName(fileName)) {
                        return@map fileName
                    }
                }
            }

            url.pathSegments
                .lastOrNull { it.isNotBlank() }
                ?.takeIf { it != "/" }
                ?.let(::decodeFileName)
        }.getOrNull()
    }

    fun getHost(url: String): String? {
        return kotlin.runCatching {
            createURL(url).host
        }.getOrNull()
    }

    private fun HttpUrl.extractResponseContentDispositionFileName(): String? {
        return queryParameterIgnoreCase(CONTENT_DISPOSITION_QUERY)
            ?.let(ContentDispositionUtils::extractFileName)
            ?.takeIf { it.isNotBlank() }
    }

    private fun HttpUrl.queryParameterIgnoreCase(name: String): String? {
        return queryParameterNames
            .firstOrNull { it.equals(name, ignoreCase = true) }
            ?.let(::queryParameter)
    }

    private fun looksLikeFileName(name: String): Boolean {
        val lastDotIndex = name.lastIndexOf('.')
        if (lastDotIndex <= 0 || lastDotIndex >= name.length - 1) {
            return false
        }
        val extension = name.substring(lastDotIndex + 1)
        return extension.length in 2..10 && extension.all { it.isLetterOrDigit() }
    }

    private fun decodeFileName(name: String): String {
        return runCatching {
            ContentDispositionUtils.decodeUrlEncodedFileName(name)
        }.getOrDefault(name)
    }

    private const val CONTENT_DISPOSITION_QUERY = "response-content-disposition"

    private val fileNameQueryParams = listOf("file", "filename", "name", "download", "f")
}
