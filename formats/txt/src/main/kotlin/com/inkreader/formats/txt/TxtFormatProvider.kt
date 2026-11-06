package com.inkreader.formats.txt

import com.inkreader.kernel.api.BookDocument
import com.inkreader.kernel.api.BookMetadata
import com.inkreader.kernel.api.Chapter
import com.inkreader.kernel.api.FormatMatch
import com.inkreader.kernel.api.FormatProvider
import com.inkreader.kernel.api.LayoutKind
import com.inkreader.kernel.api.OpenContext
import com.inkreader.kernel.api.BookSource
import java.nio.charset.StandardCharsets

/**
 * M1 的最小 TXT 格式插件：UTF-8、BOM 处理、单章节。
 *
 * M2 会补编码探测、章节索引、按偏移读取和大文件支持。
 */
class TxtFormatProvider : FormatProvider {
    override val id: String = "txt"

    override fun probe(source: BookSource): FormatMatch? {
        val name = source.displayName.lowercase()
        val mime = source.mimeType?.lowercase().orEmpty()
        val looksLikeTxt = name.endsWith(".txt") || name.endsWith(".text") || mime.startsWith("text/")
        return if (looksLikeTxt) FormatMatch(id, 60) else null
    }

    override fun open(source: BookSource, context: OpenContext): BookDocument {
        val bytes = source.readAll()
        val text = decodeUtf8(bytes)
        if (text.isBlank()) {
            throw IllegalArgumentException("TXT 文件为空或无法解码为 UTF-8")
        }
        val title = context.displayName.substringBeforeLast('.').ifBlank { "未命名书籍" }
        return BookDocument(
            metadata = BookMetadata(title = title),
            formatId = id,
            layout = LayoutKind.REFLOWABLE,
            chapters = listOf(Chapter(id = "txt-0", title = title, text = text)),
        )
    }

    private fun decodeUtf8(bytes: ByteArray): String {
        val start = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) 3 else 0
        return String(bytes, start, bytes.size - start, StandardCharsets.UTF_8)
    }
}
