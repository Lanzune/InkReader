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
    private val emphasisPattern = Regex("""(\*\*|__|\*|_|~~)(.+?)\1""")

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
                val title = cleanInline(heading.groupValues[2])
                if (builder.isNotEmpty()) builder.append("\n\n")
                val start = builder.length
                builder.append(title)
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
                if (level <= 3) toc.add(TocEntry(title, start))
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
            text = cleanInline(text)
            if (text.isNotBlank()) appendParagraph(builder, text)
        }

        val text = builder.toString().replace(Regex("\\n{3,}"), "\n\n").trim()
        return RenderedText(text, toc, styles)
    }

    private fun appendParagraph(builder: StringBuilder, text: String) {
        if (builder.isEmpty()) {
            builder.append(text)
        } else {
            if (!builder.endsWith("\n\n")) builder.append("\n\n")
            builder.append(text)
        }
    }

    private fun cleanInline(value: String): String {
        var text = value
        text = imageOrLink(text)
        text = inlineCodePattern.replace(text, "$1")
        text = emphasisPattern.replace(text, "$2")
        return decodeEntities(text).replace(Regex("[\\t ]+"), " ").trim()
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
