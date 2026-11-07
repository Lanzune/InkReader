package com.inkreader.formats.htmlmd

import com.inkreader.formats.txt.TxtDecoder
import com.inkreader.kernel.api.BookDocument
import com.inkreader.kernel.api.BookMetadata
import com.inkreader.kernel.api.BookReadException
import com.inkreader.kernel.api.BookSource
import com.inkreader.kernel.api.Chapter
import com.inkreader.kernel.api.ChapterStyle
import com.inkreader.kernel.api.FormatMatch
import com.inkreader.kernel.api.FormatProvider
import com.inkreader.kernel.api.InlineStyleKind
import com.inkreader.kernel.api.LayoutKind
import com.inkreader.kernel.api.OpenContext
import com.inkreader.kernel.api.TocEntry

/**
 * Single-file HTML/Markdown baseline.
 *
 * Scripts/styles/iframes and remote resources are stripped. Markdown headings
 * become TOC entries and heading spans; HTML is reduced to readable text.
 */
class HtmlMarkdownFormatProvider : FormatProvider {
    override val id: String = "htmlmd"

    override fun probe(source: BookSource): FormatMatch? {
        val name = source.displayName.lowercase()
        val mime = source.mimeType?.lowercase().orEmpty()
        return when {
            name.endsWith(".md") || name.endsWith(".markdown") -> FormatMatch(id, 95)
            name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".xhtml") ->
                FormatMatch(id, 90)
            mime == "text/markdown" || mime == "text/html" -> FormatMatch(id, 80)
            else -> null
        }
    }

    override fun open(source: BookSource, context: OpenContext): BookDocument {
        val decoded = TxtDecoder.decode(source.readAll(), context.encoding)
        if (decoded.text.isBlank()) throw BookReadException("文档为空")
        val name = context.displayName.lowercase()
        val isMarkdown = name.endsWith(".md") || name.endsWith(".markdown") ||
            context.mimeType?.equals("text/markdown", ignoreCase = true) == true
        val title = context.displayName.substringBeforeLast('.').ifBlank { "未命名文档" }

        val rendered = if (isMarkdown) {
            MarkdownText.render(decoded.text)
        } else {
            HtmlText.render(decoded.text)
        }
        if (rendered.text.isBlank()) throw BookReadException("文档没有可显示的正文")

        return BookDocument(
            metadata = BookMetadata(title = title),
            formatId = if (isMarkdown) "markdown" else "html",
            layout = LayoutKind.REFLOWABLE,
            chapters = listOf(
                Chapter(
                    id = "htmlmd-0",
                    title = title,
                    text = rendered.text,
                    styles = rendered.styles,
                ),
            ),
            toc = rendered.toc,
        )
    }
}

private data class RenderedText(
    val text: String,
    val toc: List<TocEntry> = emptyList(),
    val styles: List<ChapterStyle> = emptyList(),
)

private object MarkdownText {
    private val headingPattern = Regex("""^(#{1,6})\s+(.+?)\s*#*\s*$""")
    private val linkPattern = Regex("""!?\[([^\]]*)]\([^)]*\)""")
    private val inlineCodePattern = Regex("`([^`]*)`")
    private val boldPattern = Regex("""(\*\*|__)(.+?)\1""")
    private val italicPattern = Regex("""(\*|_)(.+?)\1""")
    private const val BOLD_OPEN = '\uE100'
    private const val BOLD_CLOSE = '\uE101'
    private const val ITALIC_OPEN = '\uE102'
    private const val ITALIC_CLOSE = '\uE103'

    private data class InlineText(
        val text: String,
        val styles: List<ChapterStyle> = emptyList(),
    )

    fun render(source: String): RenderedText {
        val builder = StringBuilder()
        val toc = mutableListOf<TocEntry>()
        val styles = mutableListOf<ChapterStyle>()
        var inCode = false

        source.replace("\r\n", "\n").replace('\r', '\n').lines().forEach { raw ->
            val line = raw.trimEnd()
            if (line.trimStart().startsWith("```")) {
                inCode = !inCode
                if (builder.isNotEmpty() && !builder.endsWith("\n\n")) builder.append("\n\n")
                return@forEach
            }
            if (inCode) {
                appendParagraph(builder, line)
                return@forEach
            }
            val heading = headingPattern.matchEntire(line)
            if (heading != null) {
                val level = heading.groupValues[1].length.coerceAtMost(3)
                val inline = parseInline(heading.groupValues[2])
                if (builder.isNotEmpty()) builder.append("\n\n")
                val start = builder.length
                builder.append(inline.text)
                val end = builder.length
                styles.add(
                    ChapterStyle(
                        start = start,
                        endExclusive = end,
                        kind = when (level) {
                            1 -> InlineStyleKind.HEADING1
                            2 -> InlineStyleKind.HEADING2
                            else -> InlineStyleKind.HEADING3
                        },
                    ),
                )
                inline.styles.forEach { style ->
                    styles.add(
                        style.copy(
                            start = start + style.start,
                            endExclusive = start + style.endExclusive,
                        ),
                    )
                }
                if (level <= 3) toc.add(TocEntry(inline.text, start))
                builder.append("\n\n")
                return@forEach
            }
            if (line.matches(Regex("""^\s*([-*_])\s*\1\s*\1[\s\S]*$"""))) {
                if (builder.isNotEmpty()) builder.append("\n\n")
                return@forEach
            }
            var text = line
            text = text.replace(Regex("""^\s*>\s?"""), "")
            text = text.replace(Regex("""^\s*[-*+]\s+"""), "• ")
            text = text.replace(Regex("""^\s*\d+[.)]\s+"""), "")
            val inline = parseInline(text)
            if (inline.text.isNotBlank()) {
                val start = appendParagraph(builder, inline.text)
                inline.styles.forEach { style ->
                    styles.add(
                        style.copy(
                            start = start + style.start,
                            endExclusive = start + style.endExclusive,
                        ),
                    )
                }
            }
        }

        val text = builder.toString().replace(Regex("\\n{3,}"), "\n\n").trim()
        return RenderedText(text, toc, styles)
    }

    private fun appendParagraph(builder: StringBuilder, text: String): Int {
        val start = if (builder.isEmpty()) {
            0
        } else {
            if (!builder.endsWith("\n\n")) builder.append("\n\n")
            builder.length
        }
        builder.append(text)
        return start
    }

    private fun parseInline(value: String): InlineText {
        var text = imageOrLink(value)
        text = inlineCodePattern.replace(text, "$1")

        text = boldPattern.replace(text) { match ->
            "$BOLD_OPEN${match.groupValues[2]}$BOLD_CLOSE"
        }
        text = italicPattern.replace(text) { match ->
            "$ITALIC_OPEN${match.groupValues[2]}$ITALIC_CLOSE"
        }

        val clean = StringBuilder(text.length)
        val styles = mutableListOf<ChapterStyle>()
        var boldDepth = 0
        var boldStart = -1
        var italicDepth = 0
        var italicStart = -1
        for (character in text) {
            when (character) {
                BOLD_OPEN -> {
                    if (boldDepth == 0) boldStart = clean.length
                    boldDepth += 1
                }
                BOLD_CLOSE -> {
                    if (boldDepth > 0) {
                        boldDepth -= 1
                        if (boldDepth == 0 && boldStart >= 0) {
                            if (boldStart < clean.length) {
                                styles.add(
                                    ChapterStyle(boldStart, clean.length, InlineStyleKind.BOLD),
                                )
                            }
                            boldStart = -1
                        }
                    }
                }
                ITALIC_OPEN -> {
                    if (italicDepth == 0) italicStart = clean.length
                    italicDepth += 1
                }
                ITALIC_CLOSE -> {
                    if (italicDepth > 0) {
                        italicDepth -= 1
                        if (italicDepth == 0 && italicStart >= 0) {
                            if (italicStart < clean.length) {
                                styles.add(
                                    ChapterStyle(
                                        italicStart,
                                        clean.length,
                                        InlineStyleKind.ITALIC,
                                    ),
                                )
                            }
                            italicStart = -1
                        }
                    }
                }
                else -> clean.append(character)
            }
        }
        return InlineText(
            text = decodeEntities(clean.toString()).replace(Regex("[\\t ]+"), " ").trim(),
            styles = styles,
        )
    }

    private fun imageOrLink(value: String): String =
        linkPattern.replace(value) { match -> match.groupValues[1] }

    private fun decodeEntities(value: String): String = value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
}

private object HtmlText {
    private val headPattern = Regex("(?is)<head\\b.*?</head\\s*>")
    private val hiddenPattern = Regex(
        "(?is)<(script|style|noscript|iframe|object|audio|video)\\b.*?</\\1\\s*>",
    )
    private val commentPattern = Regex("(?s)<!--.*?-->")
    private val breakPattern = Regex("(?i)<br\\s*/?>")
    private val blockEndPattern = Regex(
        "(?i)</(p|div|h[1-6]|li|blockquote|tr|section|article|header|footer|pre)\\s*>",
    )
    private val tagPattern = Regex("(?s)<[^>]+>")

    fun render(source: String): RenderedText {
        var text = source
        text = headPattern.replace(text, " ")
        text = hiddenPattern.replace(text, " ")
        text = commentPattern.replace(text, " ")
        text = breakPattern.replace(text, "\n")
        text = blockEndPattern.replace(text, "\n\n")
        text = tagPattern.replace(text, "")
        text = decodeEntities(text)
        text = text.replace('\u00A0', ' ')
        text = text.replace("\r\n", "\n").replace('\r', '\n')
        text = text.lines().joinToString("\n") { it.replace(Regex("[\\t ]+"), " ").trim() }
        text = text.replace(Regex("\\n{3,}"), "\n\n").trim()
        return RenderedText(text)
    }

    private fun decodeEntities(value: String): String = value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
}
