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

    /**
     * 检查字符串是否看起来像文件名（包含文件扩展名）
     */
    private fun looksLikeFileName(name: String): Boolean {
        // 检查是否包含文件扩展名（至少2个字符的扩展名）
        // 例如: file.zip, document.pdf, image.png
        val lastDotIndex = name.lastIndexOf('.')
        if (lastDotIndex <= 0 || lastDotIndex >= name.length - 1) {
            return false
        }
        val extension = name.substring(lastDotIndex + 1)
        // 扩展名应该在2-10个字符之间，且只包含字母数字
        return extension.length in 2..10 && extension.all { it.isLetterOrDigit() }
    }

    fun extractNameFromLink(link: String): String? {
        return runCatching {
            createURL(link)
        }.map { url ->
            // 从路径段中查找看起来像文件名的段
            val fileNameFromPath = url.pathSegments
                .asReversed()
                .firstOrNull { segment ->
                    segment.isNotBlank() && looksLikeFileName(segment)
                }
                ?.let {
                    kotlin.runCatching {
                        FilenameDecoder.decode(it, Charsets.UTF_8)
                    }.getOrNull()
                }

            if (fileNameFromPath != null) {
                return@map fileNameFromPath
            }

            // 如果没有找到文件名，尝试从查询参数中提取
            // 例如: ?file=example.zip 或 ?download=file.pdf
            val queryParams = listOf("file", "filename", "name", "download", "f")
            for (param in queryParams) {
                url.queryParameter(param)?.let { value ->
                    if (looksLikeFileName(value)) {
                        return@map value
                    }
                }
            }

            // 最后尝试使用最后一个路径段（即使它不像文件名）
            val lastSegment = url.pathSegments.lastOrNull { it.isNotBlank() }
            if (lastSegment != null && lastSegment != "/") {
                return@map FilenameDecoder.decode(lastSegment, Charsets.UTF_8)
            }

            // 如果都没有，返回 null（让调用者处理）
            null
        }.getOrNull()
    }

    fun getHost(url: String): String? {
        return kotlin.runCatching {
            createURL(url).host
        }.getOrNull()
    }

}
