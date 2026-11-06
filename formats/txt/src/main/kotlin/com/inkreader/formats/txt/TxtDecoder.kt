package com.inkreader.formats.txt

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class DecodedText(
    val text: String,
    val charsetName: String,
)

/**
 * TXT 编码探测。
 *
 * 顺序：BOM → 明显的 UTF-16 无 BOM → 严格 UTF-8 → GB18030/Big5 评分 → UTF-8 兜底。
 * 这是 M2 的最小实现；后续会加入用户手动切换和更强的语言模型评分。
 */
object TxtDecoder {
    private val UTF8 = Charsets.UTF_8
    private val UTF16LE = Charsets.UTF_16LE
    private val UTF16BE = Charsets.UTF_16BE
    private val GB18030 = runCatching { Charset.forName("GB18030") }.getOrNull()
    private val BIG5 = runCatching { Charset.forName("Big5") }.getOrNull()

    private val simplifiedHints = "这说国们时会为对开发经过还样么书读时间学习".toSet()
    private val traditionalHints = "這說國們時會為對開發經過還樣麼書讀時間學習".toSet()

    fun decode(bytes: ByteArray, overrideCharset: String? = null): DecodedText {
        if (overrideCharset != null) {
            val charset = runCatching { Charset.forName(overrideCharset) }.getOrNull()
            if (charset != null) return DecodedText(String(bytes, charset), charset.name())
        }

        detectBom(bytes)?.let { return it }

        detectUtf16WithoutBom(bytes)?.let { return it }

        strictDecode(bytes, UTF8)?.let { return DecodedText(it, "UTF-8") }

        val candidates = buildList {
            GB18030?.let { charset -> strictDecode(bytes, charset)?.let { add("GB18030" to it) } }
            BIG5?.let { charset -> strictDecode(bytes, charset)?.let { add("Big5" to it) } }
        }
        if (candidates.isNotEmpty()) {
            val best = candidates.maxWithOrNull(
                compareBy<Pair<String, String>> { score(it.second) }
                    .thenBy { if (it.first == "GB18030") 1 else 0 },
            )
            if (best != null) return DecodedText(best.second, best.first)
        }

        return DecodedText(String(bytes, UTF8), "UTF-8")
    }

    private fun detectBom(bytes: ByteArray): DecodedText? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return DecodedText(String(bytes, 3, bytes.size - 3, UTF8), "UTF-8")
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return DecodedText(String(bytes, 2, bytes.size - 2, UTF16LE), "UTF-16LE")
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return DecodedText(String(bytes, 2, bytes.size - 2, UTF16BE), "UTF-16BE")
        }
        return null
    }

    private fun detectUtf16WithoutBom(bytes: ByteArray): DecodedText? {
        if (bytes.size < 8) return null
        var evenNulls = 0
        var oddNulls = 0
        var pairs = 0
        var index = 0
        while (index + 1 < bytes.size) {
            if (bytes[index] == 0.toByte()) evenNulls += 1
            if (bytes[index + 1] == 0.toByte()) oddNulls += 1
            pairs += 1
            index += 2
        }
        if (pairs == 0) return null
        val evenRatio = evenNulls.toDouble() / pairs
        val oddRatio = oddNulls.toDouble() / pairs
        return when {
            oddRatio > 0.30 && evenRatio < 0.05 -> DecodedText(String(bytes, UTF16LE), "UTF-16LE")
            evenRatio > 0.30 && oddRatio < 0.05 -> DecodedText(String(bytes, UTF16BE), "UTF-16BE")
            else -> null
        }
    }

    private fun strictDecode(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun score(text: String): Double {
        if (text.isEmpty()) return Double.NEGATIVE_INFINITY
        var score = 0.0
        for (ch in text) {
            score += when {
                ch == '\uFFFD' -> -100.0
                ch in simplifiedHints || ch in traditionalHints -> 2.0
                ch.code in 0x4E00..0x9FFF -> 1.0
                ch in "，。！？；：、“”‘’（）《》【】…—" -> 1.0
                ch.isWhitespace() -> 0.1
                ch.isLetterOrDigit() -> 0.2
                ch.code < 0x20 && ch !in "\n\r\t" -> -30.0
                ch.code in 0x80..0x9F -> -30.0
                else -> 0.0
            }
            score += if (ch in simplifiedHints) 0.6 else 0.0
            score += if (ch in traditionalHints) 0.6 else 0.0
        }
        return score / text.length.coerceAtLeast(1)
    }
}
