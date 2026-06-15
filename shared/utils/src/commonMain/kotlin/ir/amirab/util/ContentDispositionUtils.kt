package ir.amirab.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object ContentDispositionUtils {
    fun extractFileName(contentDispositionValue: String): String? {
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

    fun decodeUrlEncodedFileName(encoded: String): String {
        var decoded = FilenameDecoder.decode(encoded, Charsets.UTF_8)
        if (decoded.containsPercentEncoding()) {
            val secondDecode = runCatching {
                FilenameDecoder.decode(decoded, Charsets.UTF_8)
            }.getOrNull()
            if (secondDecode != null && secondDecode != decoded) {
                decoded = secondDecode
            }
        }
        return decoded
    }

    private fun String.containsPercentEncoding(): Boolean {
        return percentEncodingRegex.containsMatchIn(this)
    }

    private val asciiFileNameRegex = """filename=(["']?)(?<fileName>.*?[^\\])\1(?:; ?|$)"""
        .toRegex(RegexOption.IGNORE_CASE)

    private val utf8FileNameRegex = """filename\*=UTF-8''(?<fileName>[^;\s]+)(?:; ?|$)"""
        .toRegex(RegexOption.IGNORE_CASE)

    private val percentEncodingRegex = """%[0-9A-Fa-f]{2}""".toRegex()

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
                        sb.append(' ')
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
}
