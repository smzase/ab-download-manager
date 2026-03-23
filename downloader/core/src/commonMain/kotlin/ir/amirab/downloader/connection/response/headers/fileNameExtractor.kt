package ir.amirab.downloader.connection.response.headers

import ir.amirab.util.FilenameDecoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun extractFileNameFromContentDisposition(contentDispositionValue: String): String? {
    utf8FileNameRegex.find(contentDispositionValue)
        ?.groups?.get("fileName")
        ?.value?.let {
            runCatching { decodeUrlEncodedFileName(it) }
                .getOrNull()
        }?.let {
            return it
        }
    asciiFileNameRegex.find(contentDispositionValue)
        ?.groups
        ?.get("fileName")
        ?.value?.let {
            var fileName = it
            fileName = runCatching {
                EmailMimeWordDecoder.decode(fileName)
            }.getOrNull() ?: fileName
            runCatching { decodeUrlEncodedFileName(fileName) }
                .getOrNull()
        }?.let {
            return it
        }
    return null
}

/**
 * 解码 URL 编码的文件名，支持双重 URL 编码
 * 例如：CRACK%25E2%2589%25A1TRICK%25EF%25BC%2581.rar
 * 第一次解码：CRACK%E2%89%A1TRICK%EF%BC%81.rar
 * 第二次解码：CRACK≡TRICK！.rar
 */
private fun decodeUrlEncodedFileName(encoded: String): String {
    var decoded = FilenameDecoder.decode(encoded, Charsets.UTF_8)
    // 检查是否还有 URL 编码的字符（双重编码的情况）
    // 如果解码后的字符串仍然包含 %XX 格式的编码，再次解码
    if (decoded.contains('%') && containsPercentEncoding(decoded)) {
        val secondDecode = runCatching {
            FilenameDecoder.decode(decoded, Charsets.UTF_8)
        }.getOrNull()
        if (secondDecode != null && secondDecode != decoded) {
            decoded = secondDecode
        }
    }
    return decoded
}

/**
 * 检查字符串是否包含有效的百分号编码序列
 */
private fun containsPercentEncoding(str: String): Boolean {
    val percentRegex = """%[0-9A-Fa-f]{2}""".toRegex()
    return percentRegex.containsMatchIn(str)
}

private val asciiFileNameRegex = """filename=(["']?)(?<fileName>.*?[^\\])\1(?:; ?|$)"""
    .toRegex(RegexOption.IGNORE_CASE)

private val utf8FileNameRegex = """filename\*=UTF-8''(?<fileName>[^;\s]+)(?:; ?|$)"""
    .toRegex(RegexOption.IGNORE_CASE)

/**
 * we use this class to decode the filename in content-disposition header in mail servers
 * RFC 2047
 */
private object EmailMimeWordDecoder {
    fun decode(string: String): String {
        return decodeMimeEncodedFilename(string)
    }

    private val regex by lazy {
        """=\?(?<charset>[^?]+)\?(?<encoding>[BQ])\?(?<encodedText>[^?]+)\?="""
            .toRegex(RegexOption.IGNORE_CASE)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeMimeEncodedFilename(input: String): String {
        return regex.replace(input) {
            runCatching {
                val match = it.groups

                val charset = match.requireName("charset").value
                val encoding = match.requireName("encoding").value.uppercase()
                val encodedText = match.requireName("encodedText").value

                val bytes = when (encoding) {
                    "B" -> Base64.decode(encodedText)
                    "Q" -> decodeMimeQuotedPrintable(encodedText)
                    else -> return@replace input
                }
                String(bytes, charset(charset))
            }.getOrNull() ?: it.value
        }
    }

    private fun decodeMimeQuotedPrintable(encoded: String): ByteArray {
        val sb = StringBuilder()

        var i = 0
        while (i < encoded.length) {
            val c = encoded[i]
            when {
                c == '=' && i + 2 < encoded.length -> {
                    val hex = encoded.substring(i + 1, i + 3)
                    val byte = hex.toIntOrNull(16)?.toChar()
                    if (byte != null) {
                        sb.append(byte)
                        i += 3
                    } else {
                        sb.append(c)
                        i++
                    }
                }

                c == '_' -> {
                    sb.append(' ') // _ represents space in Q encoding
                    i++
                }

                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private fun MatchGroupCollection.requireName(name: String): MatchGroup {
        return requireNotNull(this[name]) {
            "Group $name not found"
        }
    }

}
