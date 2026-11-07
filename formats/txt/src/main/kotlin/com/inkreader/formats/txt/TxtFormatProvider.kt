// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.formats.txt

import com.inkreader.kernel.api.BookDocument
import com.inkreader.kernel.api.BookMetadata
import com.inkreader.kernel.api.Chapter
import com.inkreader.kernel.api.FormatMatch
import com.inkreader.kernel.api.FormatProvider
import com.inkreader.kernel.api.LayoutKind
import com.inkreader.kernel.api.OpenContext
import com.inkreader.kernel.api.BookSource
import com.inkreader.kernel.api.TocEntry

/**
 * TXT 格式：编码探测、单章节正文和章节标题索引。
 *
 * M2 先提供正文章节定位；大文件按偏移读取、分页索引留到后续。
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
        val decoded = TxtDecoder.decode(bytes, context.encoding)
        if (decoded.text.isBlank()) {
            throw IllegalArgumentException("TXT 文件为空或无法解码")
        }
        val title = context.displayName.substringBeforeLast('.').ifBlank { "未命名书籍" }
        return BookDocument(
            metadata = BookMetadata(title = title),
            formatId = id,
            layout = LayoutKind.REFLOWABLE,
            chapters = listOf(Chapter(id = "txt-0", title = title, text = decoded.text)),
            toc = buildToc(decoded.text),
        )
    }

    private fun buildToc(text: String): List<TocEntry> {
        val matches = chapterPattern.findAll(text).take(500).toList()
        if (matches.isEmpty()) return emptyList()
        return matches.map { match ->
            TocEntry(
                title = match.value.trim().take(80).ifBlank { "未命名章节" },
                charOffset = match.range.first,
            )
        }
    }

    private companion object {
        val chapterPattern = Regex(
            pattern = "(?m)^(?:第[0-9一二三四五六七八九十百千万零〇]+[章节回卷部篇].*|序章.*|楔子.*|尾声.*|后记.*|Chapter\\s+\\d+.*)$",
        )
    }
}
