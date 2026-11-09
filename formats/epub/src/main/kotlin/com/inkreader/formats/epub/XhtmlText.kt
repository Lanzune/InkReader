// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.formats.epub

import com.inkreader.kernel.api.InlineStyleKind

/**
 * Deliberately small XHTML-to-text conversion for the M3 text reader.
 *
 * Supports paragraph boundaries, images and basic bold/italic ranges. Rich CSS
 * layout and links are later M3b increments.
 */
internal object XhtmlText {
    data class RichImage(
        val src: String,
        val alt: String,
        val charOffset: Int,
    )

    data class RichStyle(
        val start: Int,
        val endExclusive: Int,
        val kind: InlineStyleKind,
    )

    data class RichLink(
        val start: Int,
        val endExclusive: Int,
        val target: String,
    )

    data class RichAnchor(
        val id: String,
        val charOffset: Int,
    )

    data class RichHeading(
        val level: Int,
        val start: Int,
        val endExclusive: Int,
        val id: String? = null,
    )

    data class RichParagraph(
        val start: Int,
        val endExclusive: Int,
        val tag: String,
        val classes: List<String>,
        val align: String?,
        val firstLineIndent: Boolean,
        val indentSpecified: Boolean,
        val leftMarginEm: Float,
        val rightMarginEm: Float,
        val fontScale: Float,
    )

    data class RichText(
        val text: String,
        val images: List<RichImage>,
        val styles: List<RichStyle> = emptyList(),
        val links: List<RichLink> = emptyList(),
        val anchors: List<RichAnchor> = emptyList(),
        val headings: List<RichHeading> = emptyList(),
        val paragraphs: List<RichParagraph> = emptyList(),
    )

    private val headPattern = Regex("(?is)<head\\b.*?</head\\s*>")
    private val hiddenPattern = Regex(
        "(?is)<(script|style|noscript|svg|iframe|object|audio|video)\\b.*?</\\1\\s*>",
    )
    private val commentPattern = Regex("(?s)<!--.*?-->")
    private val breakPattern = Regex("(?i)<br\\s*/?>")
    private val horizontalRulePattern = Regex("(?i)<hr\\b[^>]*>")
    private val blockEndPattern = Regex(
        "(?i)</(p|div|h[1-6]|li|blockquote|tr|section|article|header|footer|figcaption|pre)\\s*>",
    )
    private val imagePattern = Regex("(?is)<img\\b[^>]*>")
    private val imageSrcPattern = Regex("""(?i)\bsrc\s*=\s*["']([^"']+)["']""")
    private val imageAltPattern = Regex("""(?i)\balt\s*=\s*["']([^"']*)["']""")
    private val tagPattern = Regex("(?s)<[^>]+>")
    private val titlePattern = Regex("(?is)<title[^>]*>(.*?)</title\\s*>")
    private val boldTagPattern = Regex("(?i)<(/?)b\\b[^>]*>|</?strong\\b[^>]*>")
    private val italicTagPattern = Regex("(?i)<(/?)i\\b[^>]*>|</?em\\b[^>]*>")
    private val h1TagPattern = Regex("(?i)<(/?)h1\\b[^>]*>")
    private val h2TagPattern = Regex("(?i)<(/?)h2\\b[^>]*>")
    private val h3TagPattern = Regex("(?i)<(/?)h3\\b[^>]*>")
    private val h4TagPattern = Regex("(?i)<(/?)h[4-6]\\b[^>]*>")
    private val blockquoteTagPattern = Regex("(?i)<(/?)blockquote\\b[^>]*>")
    private val listTagPattern = Regex("(?i)<(/?)(ul|ol)\\b[^>]*>")
    private val listItemPattern = Regex("(?i)<li\\b[^>]*>|</li\\s*>")
    private val paragraphOpenPattern = Regex("(?i)<p\\b([^>]*)>")
    private val paragraphClosePattern = Regex("(?i)</p\\s*>\\s*")
    private val headingOpenPattern = Regex("(?i)<h([1-6])\\b([^>]*)>")
    private val headingClosePattern = Regex("(?i)</h[1-6]\\s*>")
    private val alignAttributePattern = Regex("""(?i)\balign\s*=\s*["']?(left|center|right)""")
    private val textAlignPattern = Regex(
        """(?i)\btext-align\s*:\s*(left|center|right)""",
    )
    private val anchorOpenPattern = Regex("(?i)<a\\b([^>]*)>")
    private val anchorClosePattern = Regex("(?i)</a\\s*>")
    private val hrefPattern = Regex("""(?i)\bhref\s*=\s*["']([^"']*)["']""")
    private val idPattern = Regex("""(?i)\bid\s*=\s*["']([^"']*)["']""")
    private val classPattern = Regex("""(?i)\bclass\s*=\s*["']([^"']*)["']""")
    private val taggedIdPattern = Regex(
        """(?i)<(?!(?:a)\b)([a-z][a-z0-9:_-]*)\b([^>]*)>""",
    )
    private val styleBlockPattern = Regex("(?is)<style\\b[^>]*>(.*?)</style\\s*>")
    private val cssRulePattern = Regex("(?is)([^{}]+)\\{([^{}]*)\\}")
    private val textIndentPattern = Regex("""(?i)\btext-indent\s*:\s*([^;]+)""")
    private val marginLeftPattern = Regex("""(?i)\bmargin-left\s*:\s*([^;]+)""")
    private val marginRightPattern = Regex("""(?i)\bmargin-right\s*:\s*([^;]+)""")
    private val fontSizePattern = Regex("""(?i)\bfont-size\s*:\s*([^;]+)""")
    private val horizontalSpacePattern = Regex("[\\t ]+")
    private val excessiveNewlinesPattern = Regex("\\n{3,}")
    private const val ALIGN_CENTER_OPEN = '\uE010'
    private const val ALIGN_RIGHT_OPEN = '\uE011'
    private const val ALIGN_CLOSE = '\uE012'
    private const val ALIGN_LEFT_OPEN = '\uE013'
    private const val LINK_OPEN = '\uE020'
    private const val LINK_CLOSE = '\uE021'
    private const val ANCHOR = '\uE022'
    private const val ANCHOR_EXTRA = '\uE023'
    private const val FIRST_INDENT_OPEN = '\uE030'
    private const val FIRST_INDENT_CLOSE = '\uE031'
    private const val PARAGRAPH_CLOSE = '\uE040'
    private const val PARAGRAPH_OPEN = '\uE041'
    private const val BLOCKQUOTE_OPEN = '\uE042'
    private const val BLOCKQUOTE_CLOSE = '\uE043'
    private const val LIST_OPEN = '\uE044'
    private const val LIST_CLOSE = '\uE045'
    private const val LIST_ITEM_OPEN = '\uE046'
    private const val LIST_ITEM_CLOSE = '\uE047'
    private const val HARD_BREAK = '\uE050'
    private const val IMAGE_PLACEHOLDER = '\uFFFC'
    private const val BOLD_OPEN = '\uE000'
    private const val BOLD_CLOSE = '\uE001'
    private const val ITALIC_OPEN = '\uE002'
    private const val ITALIC_CLOSE = '\uE003'
    private const val H1_OPEN = '\uE004'
    private const val H1_CLOSE = '\uE005'
    private const val H2_OPEN = '\uE006'
    private const val H2_CLOSE = '\uE007'
    private const val H3_OPEN = '\uE008'
    private const val H3_CLOSE = '\uE009'
    private const val INDENT_OPEN = '\uE00A'
    private const val INDENT_CLOSE = '\uE00B'

    fun extract(html: String, compactParagraphs: Boolean = false): String =
        extractRich(html, compactParagraphs).text

    fun extractRich(
        html: String,
        compactParagraphs: Boolean = false,
    ): RichText {
        val images = mutableListOf<Pair<String, String>>()
        val linkTargets = mutableListOf<String>()
        val anchorIds = mutableListOf<String>()
        val extraAnchorIds = mutableListOf<String>()
        val headingIds = Regex("(?i)<h[1-6]\\b([^>]*)>").findAll(html)
            .map { match -> idPattern.find(match.groupValues[1])?.groupValues?.get(1)?.trim() }
            .toList()
        var headingSequence = 0
        val css = parseCss(html)
        val paragraphMetas = mutableListOf<BlockMeta>()
        var text = markLinks(html, linkTargets, anchorIds)
        text = markExtraAnchors(text, extraAnchorIds)
        text = markAlignment(text, css, paragraphMetas)
        text = markStyles(text)
        text = imagePattern.replace(text) { match ->
            val tag = match.value
            val src = imageSrcPattern.find(tag)?.groupValues?.get(1).orEmpty()
            val alt = imageAltPattern.find(tag)?.groupValues?.get(1).orEmpty()
            if (src.isBlank()) {
                ""
            } else {
                images.add(src to decodeEntities(alt))
                IMAGE_PLACEHOLDER.toString()
            }
        }
        text = headPattern.replace(text, " ")
        text = hiddenPattern.replace(text, " ")
        text = commentPattern.replace(text, " ")
        text = breakPattern.replace(text, HARD_BREAK.toString())
        text = horizontalRulePattern.replace(text, "\n\n")
        text = blockEndPattern.replace(text, "\n\n")
        text = tagPattern.replace(text, "")
        text = decodeEntities(text)
        text = text.replace('\u00A0', ' ')
        text = text.replace("\r\n", "\n").replace('\r', '\n')
        // Source line breaks inside a paragraph are just HTML whitespace. Only
        // block ends and explicit <br> create line breaks.
        text = text.replace(Regex("(?<!\\n)\\n(?!\\n)"), " ")
        text = text.replace(horizontalSpacePattern, " ")
        text = text.replace(Regex(" *\\n *"), "\n")
        text = text.replace(HARD_BREAK, '\n')
        text = text.replace(excessiveNewlinesPattern, "\n\n").trim()

        val clean = StringBuilder(text.length)
        val styles = mutableListOf<RichStyle>()
        val headings = mutableListOf<RichHeading>()
        var boldDepth = 0
        var boldStart = -1
        var italicDepth = 0
        var italicStart = -1
        var h1Depth = 0
        var h1Start = -1
        var h2Depth = 0
        var h2Start = -1
        var h3Depth = 0
        var h3Start = -1
        var indentDepth = 0
        var indentStart = -1
        var alignStart = -1
        var alignKind: InlineStyleKind? = null
        var firstIndentDepth = 0
        var firstIndentStart = -1
        var paragraphCompact = false
        var paragraphStart = 0
        var currentParagraphMeta: BlockMeta? = null
        var blockquoteDepth = 0
        var listDepth = 0
        var listItemStart = -1
        var linkIndex = 0
        var anchorIndex = 0
        var extraAnchorIndex = 0
        val linkStack = ArrayDeque<Pair<String, Int>>()
        val richLinks = mutableListOf<RichLink>()
        val richAnchors = mutableListOf<RichAnchor>()
        val richParagraphs = mutableListOf<RichParagraph>()
        var paragraphMetaIndex = 0

        fun closeAlignment() {
            if (alignStart >= 0 && alignStart < clean.length) {
                alignKind?.let { kind ->
                    styles.add(RichStyle(alignStart, clean.length, kind))
                }
            }
            alignStart = -1
            alignKind = null
        }
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
                                styles.add(RichStyle(boldStart, clean.length, InlineStyleKind.BOLD))
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
                                    RichStyle(italicStart, clean.length, InlineStyleKind.ITALIC),
                                )
                            }
                            italicStart = -1
                        }
                    }
                }
                H1_OPEN -> {
                    if (h1Depth == 0) h1Start = clean.length
                    h1Depth += 1
                }
                H1_CLOSE -> {
                    if (h1Depth > 0) {
                        h1Depth -= 1
                        if (h1Depth == 0 && h1Start >= 0) {
                            if (h1Start < clean.length) {
                                styles.add(
                                    RichStyle(h1Start, clean.length, InlineStyleKind.HEADING1),
                                )
                                headings.add(
                                    RichHeading(
                                        1,
                                        h1Start,
                                        clean.length,
                                        headingIds.getOrNull(headingSequence),
                                    ),
                                )
                            }
                            h1Start = -1
                        }
                        headingSequence += 1
                    }
                }
                H2_OPEN -> {
                    if (h2Depth == 0) h2Start = clean.length
                    h2Depth += 1
                }
                H2_CLOSE -> {
                    if (h2Depth > 0) {
                        h2Depth -= 1
                        if (h2Depth == 0 && h2Start >= 0) {
                            if (h2Start < clean.length) {
                                styles.add(
                                    RichStyle(h2Start, clean.length, InlineStyleKind.HEADING2),
                                )
                                headings.add(
                                    RichHeading(
                                        2,
                                        h2Start,
                                        clean.length,
                                        headingIds.getOrNull(headingSequence),
                                    ),
                                )
                            }
                            h2Start = -1
                        }
                        headingSequence += 1
                    }
                }
                H3_OPEN -> {
                    if (h3Depth == 0) h3Start = clean.length
                    h3Depth += 1
                }
                H3_CLOSE -> {
                    if (h3Depth > 0) {
                        h3Depth -= 1
                        if (h3Depth == 0 && h3Start >= 0) {
                            if (h3Start < clean.length) {
                                styles.add(
                                    RichStyle(h3Start, clean.length, InlineStyleKind.HEADING3),
                                )
                                headings.add(
                                    RichHeading(
                                        3,
                                        h3Start,
                                        clean.length,
                                        headingIds.getOrNull(headingSequence),
                                    ),
                                )
                            }
                            h3Start = -1
                        }
                        headingSequence += 1
                    }
                }
                INDENT_OPEN -> {
                    if (indentDepth == 0) indentStart = clean.length
                    indentDepth += 1
                }
                INDENT_CLOSE -> {
                    if (indentDepth > 0) {
                        indentDepth -= 1
                        if (indentDepth == 0 && indentStart >= 0) {
                            if (indentStart < clean.length) {
                                styles.add(
                                    RichStyle(indentStart, clean.length, InlineStyleKind.INDENT),
                                )
                            }
                            indentStart = -1
                        }
                    }
                }
                BLOCKQUOTE_OPEN -> blockquoteDepth += 1
                BLOCKQUOTE_CLOSE -> if (blockquoteDepth > 0) blockquoteDepth -= 1
                LIST_OPEN -> {
                    if (listDepth > 0 && clean.lastOrNull() != '\n') clean.append('\n')
                    listDepth += 1
                }
                LIST_CLOSE -> if (listDepth > 0) listDepth -= 1
                LIST_ITEM_OPEN -> {
                    listItemStart = clean.length
                    clean.append("\u3000".repeat((listDepth - 1).coerceIn(0, 3)))
                    clean.append("• ")
                }
                LIST_ITEM_CLOSE -> {
                    if (clean.length > listItemStart && clean.lastOrNull() != '\n') {
                        clean.append('\n')
                    }
                    listItemStart = -1
                }
                ALIGN_CENTER_OPEN -> {
                    closeAlignment()
                    alignStart = clean.length
                    alignKind = InlineStyleKind.ALIGN_CENTER
                }
                ALIGN_RIGHT_OPEN -> {
                    closeAlignment()
                    alignStart = clean.length
                    alignKind = InlineStyleKind.ALIGN_RIGHT
                }
                ALIGN_LEFT_OPEN -> {
                    closeAlignment()
                    alignStart = clean.length
                    alignKind = InlineStyleKind.ALIGN_LEFT
                }
                ALIGN_CLOSE -> closeAlignment()
                PARAGRAPH_OPEN -> {
                    paragraphStart = clean.length
                    val meta = paragraphMetas.getOrNull(paragraphMetaIndex)
                    currentParagraphMeta = if (blockquoteDepth > 0) {
                        meta?.copy(tag = "blockquote")
                    } else {
                        meta
                    }
                    if (currentParagraphMeta?.classes?.any {
                            it == "note" || it == "footnote" || it == "endnote" ||
                                it == "footnotes" || it == "endnotes"
                        } == true
                    ) {
                        paragraphCompact = false
                    }
                    paragraphMetaIndex += 1
                }
                PARAGRAPH_CLOSE -> {
                    if (clean.length > paragraphStart) {
                        currentParagraphMeta?.let { meta ->
                            richParagraphs.add(
                                RichParagraph(
                                    start = paragraphStart,
                                    endExclusive = clean.length,
                                    tag = meta.tag,
                                    classes = meta.classes,
                                    align = meta.align,
                                    firstLineIndent = meta.firstLineIndent,
                                    indentSpecified = meta.indentSpecified,
                                    leftMarginEm = meta.leftMarginEm,
                                    rightMarginEm = meta.rightMarginEm,
                                    fontScale = meta.fontScale,
                                ),
                            )
                        }
                        clean.append(
                            if ((paragraphCompact || compactParagraphs) &&
                                currentParagraphMeta?.classes?.any {
                                    it == "note" || it == "footnote" || it == "endnote" ||
                                        it == "footnotes" || it == "endnotes"
                                } != true
                            ) {
                                "\n"
                            } else {
                                "\n\n"
                            },
                        )
                    }
                    currentParagraphMeta = null
                    paragraphCompact = false
                    paragraphStart = clean.length
                }
                FIRST_INDENT_OPEN -> {
                    if (firstIndentDepth == 0) {
                        firstIndentStart = clean.length
                        paragraphCompact = true
                    }
                    firstIndentDepth += 1
                }
                FIRST_INDENT_CLOSE -> {
                    if (firstIndentDepth > 0) {
                        firstIndentDepth -= 1
                        if (firstIndentDepth == 0 && firstIndentStart >= 0) {
                            if (firstIndentStart < clean.length) {
                                styles.add(
                                    RichStyle(
                                        firstIndentStart,
                                        clean.length,
                                        InlineStyleKind.FIRST_LINE_INDENT,
                                    ),
                                )
                            }
                            firstIndentStart = -1
                        }
                    }
                }
                LINK_OPEN -> {
                    if (linkIndex < linkTargets.size) {
                        linkStack.addLast(linkTargets[linkIndex] to clean.length)
                        linkIndex += 1
                    }
                }
                LINK_CLOSE -> {
                    linkStack.removeLastOrNull()?.let { (target, start) ->
                        if (start < clean.length) {
                            richLinks.add(RichLink(start, clean.length, target))
                        }
                    }
                }
                ANCHOR -> {
                    if (anchorIndex < anchorIds.size) {
                        richAnchors.add(RichAnchor(anchorIds[anchorIndex], clean.length))
                        anchorIndex += 1
                    }
                }
                ANCHOR_EXTRA -> {
                    if (extraAnchorIndex < extraAnchorIds.size) {
                        richAnchors.add(
                            RichAnchor(extraAnchorIds[extraAnchorIndex], clean.length),
                        )
                        extraAnchorIndex += 1
                    }
                }
                else -> clean.append(character)
            }
        }
        closeAlignment()
        if (firstIndentStart >= 0 && firstIndentStart < clean.length) {
            styles.add(
                RichStyle(firstIndentStart, clean.length, InlineStyleKind.FIRST_LINE_INDENT),
            )
        }
        val cleanText = clean.toString()
        var trimStart = 0
        while (trimStart < cleanText.length && cleanText[trimStart].isWhitespace()) trimStart += 1
        var trimEnd = cleanText.length
        while (trimEnd > trimStart && cleanText[trimEnd - 1].isWhitespace()) trimEnd -= 1
        val trimmedText = cleanText.substring(trimStart, trimEnd)
        fun shift(offset: Int): Int = (offset - trimStart).coerceIn(0, trimmedText.length)
        fun inside(offset: Int): Boolean = offset in trimStart..trimEnd

        val richImages = mutableListOf<RichImage>()
        var imageIndex = 0
        cleanText.forEachIndexed { offset, character ->
            if (character == IMAGE_PLACEHOLDER && imageIndex < images.size) {
                val (src, alt) = images[imageIndex]
                if (inside(offset)) {
                    richImages.add(
                        RichImage(src = src, alt = alt, charOffset = shift(offset)),
                    )
                }
                imageIndex += 1
            }
        }
        val adjustedStyles = styles.mapNotNull { style ->
            val start = shift(style.start)
            val end = shift(style.endExclusive)
            if (start >= end) null else style.copy(start = start, endExclusive = end)
        }
        val adjustedLinks = richLinks.mapNotNull { link ->
            if (!inside(link.start) || !inside(link.endExclusive)) {
                null
            } else {
                link.copy(start = shift(link.start), endExclusive = shift(link.endExclusive))
            }
        }
        val adjustedAnchors = richAnchors
            .filter { inside(it.charOffset) }
            .map { it.copy(charOffset = shift(it.charOffset)) }
        val adjustedHeadings = headings
            .filter { inside(it.start) && inside(it.endExclusive) }
            .map {
                it.copy(start = shift(it.start), endExclusive = shift(it.endExclusive))
            }
        val adjustedParagraphs = richParagraphs
            .filter { inside(it.start) && inside(it.endExclusive) }
            .map {
                it.copy(start = shift(it.start), endExclusive = shift(it.endExclusive))
            }
        return RichText(
            text = trimmedText,
            images = richImages,
            styles = adjustedStyles,
            links = adjustedLinks,
            anchors = adjustedAnchors,
            headings = adjustedHeadings,
            paragraphs = adjustedParagraphs,
        )
    }

    private fun markLinks(
        html: String,
        linkTargets: MutableList<String>,
        anchorIds: MutableList<String>,
    ): String {
        var text = anchorOpenPattern.replace(html) { match ->
            val attributes = match.groupValues[1]
            val id = idPattern.find(attributes)?.groupValues?.get(1)?.trim()
            val href = hrefPattern.find(attributes)?.groupValues?.get(1)?.trim()
            val prefix = StringBuilder()
            if (!id.isNullOrBlank()) {
                anchorIds.add(id)
                prefix.append(ANCHOR)
            }
            if (!href.isNullOrBlank()) {
                linkTargets.add(decodeEntities(href))
                prefix.append(LINK_OPEN)
            }
            if (prefix.isEmpty()) match.value else "$prefix${match.value}"
        }
        text = anchorClosePattern.replace(text) { match ->
            "$LINK_CLOSE${match.value}"
        }
        return text
    }

    private fun markExtraAnchors(
        html: String,
        anchorIds: MutableList<String>,
    ): String = taggedIdPattern.replace(html) { match ->
        val attributes = match.groupValues[2]
        val id = idPattern.find(attributes)?.groupValues?.get(1)?.trim()
        if (id.isNullOrBlank()) {
            match.value
        } else {
            anchorIds.add(id)
            "$ANCHOR_EXTRA${match.value}"
        }
    }

    private fun markAlignment(
        html: String,
        css: Map<String, CssStyle>,
        paragraphMetas: MutableList<BlockMeta>,
    ): String {
        var text = paragraphOpenPattern.replace(html) { match ->
            val meta = blockMeta(match.groupValues[1], "p", css)
            paragraphMetas.add(meta)
            val marker = StringBuilder()
            when (meta.align?.lowercase()) {
                "center" -> marker.append(ALIGN_CENTER_OPEN)
                "right" -> marker.append(ALIGN_RIGHT_OPEN)
                "left" -> marker.append(ALIGN_LEFT_OPEN)
            }
            if (meta.firstLineIndent) marker.append(FIRST_INDENT_OPEN)
            "$marker$PARAGRAPH_OPEN${match.value}"
        }
        text = paragraphClosePattern.replace(text) { match ->
            "$ALIGN_CLOSE$FIRST_INDENT_CLOSE$PARAGRAPH_CLOSE"
        }
        text = listTagPattern.replace(text) { match ->
            if (match.groupValues[1] == "/") LIST_CLOSE.toString() else LIST_OPEN.toString()
        }
        text = listItemPattern.replace(text) { match ->
            if (match.value.startsWith("</", ignoreCase = true)) {
                LIST_ITEM_CLOSE.toString()
            } else {
                LIST_ITEM_OPEN.toString()
            }
        }
        text = headingOpenPattern.replace(text) { match ->
            val marker = blockMarkers(
                match.groupValues[2],
                "h${match.groupValues[1]}",
                css,
            )
            if (marker.isEmpty()) match.value else "$marker${match.value}"
        }
        text = headingClosePattern.replace(text) { match ->
            "$ALIGN_CLOSE$FIRST_INDENT_CLOSE${match.value}"
        }
        return text
    }

    private fun blockMarkers(
        attributes: String,
        tag: String,
        css: Map<String, CssStyle>,
    ): String {
        val meta = blockMeta(attributes, tag, css)
        val marker = StringBuilder()
        when (meta.align?.lowercase()) {
            "center" -> marker.append(ALIGN_CENTER_OPEN)
            "right" -> marker.append(ALIGN_RIGHT_OPEN)
            "left" -> marker.append(ALIGN_LEFT_OPEN)
        }
        if (meta.firstLineIndent && !tag.startsWith("h")) marker.append(FIRST_INDENT_OPEN)
        return marker.toString()
    }

    private data class BlockMeta(
        val tag: String,
        val classes: List<String>,
        val align: String?,
        val firstLineIndent: Boolean,
        val indentSpecified: Boolean,
        val leftMarginEm: Float,
        val rightMarginEm: Float,
        val fontScale: Float,
    )

    private fun blockMeta(
        attributes: String,
        tag: String,
        css: Map<String, CssStyle>,
    ): BlockMeta {
        val inlineAlign = alignAttributePattern.find(attributes)?.groupValues?.get(1)
            ?: textAlignPattern.find(attributes)?.groupValues?.get(1)
        val classes = classPattern.find(attributes)
            ?.groupValues?.get(1)
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val classStyles = classes.mapNotNull { css[".${it.lowercase()}"] }
        val tagStyle = css[tag.lowercase()]
        val align = inlineAlign
            ?: classStyles.firstNotNullOfOrNull { it.align }
            ?: tagStyle?.align
        val inlineIndent = textIndentPattern.find(attributes)?.groupValues?.get(1)
        val indentSpecified = inlineIndent != null ||
            classStyles.any { it.textIndentSpecified } ||
            (tagStyle?.textIndentSpecified == true)
        val indent = when {
            inlineIndent != null -> isNonZeroIndent(inlineIndent)
            else -> classStyles.firstOrNull { it.textIndentSpecified }?.firstLineIndent
                ?: tagStyle?.takeIf { it.textIndentSpecified }?.firstLineIndent
                ?: false
        }
        val leftMargin = maxOf(
            parseEm(marginLeftPattern.find(attributes)?.groupValues?.get(1).orEmpty()),
            classStyles.maxOfOrNull { it.leftMarginEm } ?: 0f,
            tagStyle?.leftMarginEm ?: 0f,
        )
        val rightMargin = maxOf(
            parseEm(marginRightPattern.find(attributes)?.groupValues?.get(1).orEmpty()),
            classStyles.maxOfOrNull { it.rightMarginEm } ?: 0f,
            tagStyle?.rightMarginEm ?: 0f,
        )
        val fontScale = classStyles.firstOrNull { it.fontScale != 1f }?.fontScale
            ?: tagStyle?.fontScale
            ?: 1f
        return BlockMeta(
            tag = tag.lowercase(),
            classes = classes.map { it.lowercase() },
            align = align,
            firstLineIndent = indent,
            indentSpecified = indentSpecified,
            leftMarginEm = leftMargin,
            rightMarginEm = rightMargin,
            fontScale = fontScale,
        )
    }

    private fun isNonZeroIndent(value: String): Boolean {
        val clean = value.trim().lowercase()
        if (clean.isEmpty() || clean == "none") return false
        return !Regex("""0(?:\.0+)?(?:px|em|rem|%|pt|pc|in|cm|mm)?""").matches(clean)
    }

    private fun parseEm(value: String): Float {
        val clean = value.trim().lowercase()
        val number = clean.removeSuffix("em").removeSuffix("px").removeSuffix("%")
            .toFloatOrNull() ?: return 0f
        return when {
            clean.endsWith("px") -> number / 16f
            clean.endsWith("%") -> number / 100f
            else -> number
        }.coerceIn(0f, 6f)
    }

    private fun parseFontScale(value: String): Float {
        val clean = value.trim().lowercase()
        val number = clean.removeSuffix("em").removeSuffix("%").toFloatOrNull() ?: return 1f
        val scale = if (clean.endsWith("%")) number / 100f else number
        return scale.takeIf { it in 0.6f..1.6f } ?: 1f
    }

    private fun parseCss(html: String): Map<String, CssStyle> {
        val result = LinkedHashMap<String, CssStyle>()
        for (block in styleBlockPattern.findAll(html)) {
            for (rule in cssRulePattern.findAll(block.groupValues[1])) {
                val selectors = rule.groupValues[1]
                    .split(',')
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                if (selectors.isEmpty()) continue
                val body = rule.groupValues[2]
                val align = textAlignPattern.find(body)?.groupValues?.get(1)
                val textIndentValue = textIndentPattern.find(body)?.groupValues?.get(1)
                val indent = textIndentValue?.let(::isNonZeroIndent) ?: false
                val leftMargin = marginLeftPattern.find(body)?.groupValues?.get(1)
                    ?.let(::parseEm) ?: 0f
                val rightMargin = marginRightPattern.find(body)?.groupValues?.get(1)
                    ?.let(::parseEm) ?: 0f
                val fontScale = fontSizePattern.find(body)?.groupValues?.get(1)
                    ?.let(::parseFontScale) ?: 1f
                if (align == null && textIndentValue == null && leftMargin == 0f &&
                    rightMargin == 0f && fontScale == 1f
                ) {
                    continue
                }
                val style = CssStyle(
                    align = align,
                    textIndentSpecified = textIndentValue != null,
                    firstLineIndent = indent,
                    leftMarginEm = leftMargin,
                    rightMarginEm = rightMargin,
                    fontScale = fontScale,
                )
                for (selector in selectors) {
                    result[selector] = style
                }
            }
        }
        return result
    }

    private data class CssStyle(
        val align: String?,
        val textIndentSpecified: Boolean,
        val firstLineIndent: Boolean,
        val leftMarginEm: Float,
        val rightMarginEm: Float,
        val fontScale: Float,
    )

    private fun markStyles(html: String): String {
        var text = boldTagPattern.replace(html) { match ->
            if (match.value.startsWith("</")) BOLD_CLOSE.toString() else BOLD_OPEN.toString()
        }
        text = italicTagPattern.replace(text) { match ->
            if (match.value.startsWith("</")) ITALIC_CLOSE.toString() else ITALIC_OPEN.toString()
        }
        text = h1TagPattern.replace(text) { match ->
            if (match.value.startsWith("</")) "$H1_CLOSE\n\n" else "\n\n$H1_OPEN"
        }
        text = h2TagPattern.replace(text) { match ->
            if (match.value.startsWith("</")) "$H2_CLOSE\n\n" else "\n\n$H2_OPEN"
        }
        text = h3TagPattern.replace(text) { match ->
            if (match.value.startsWith("</")) "$H3_CLOSE\n\n" else "\n\n$H3_OPEN"
        }
        text = h4TagPattern.replace(text) { match ->
            if (match.value.startsWith("</")) "$H3_CLOSE\n\n" else "\n\n$H3_OPEN"
        }
        text = blockquoteTagPattern.replace(text) { match ->
            if (match.value.startsWith("</")) {
                "$INDENT_CLOSE$BLOCKQUOTE_CLOSE"
            } else {
                "$INDENT_OPEN$BLOCKQUOTE_OPEN"
            }
        }
        return text
    }

    fun title(html: String): String? = titlePattern.find(html)
        ?.groupValues?.get(1)
        ?.let { decodeEntities(tagPattern.replace(it, "")).trim() }
        ?.takeIf { it.isNotBlank() }

    fun stripTags(fragment: String): String =
        decodeEntities(tagPattern.replace(fragment, " ")).trim()

    fun decodeEntities(value: String): String {
        if ('&' !in value) return value
        val builder = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val ampersand = value.indexOf('&', index)
            if (ampersand < 0) {
                builder.append(value, index, value.length)
                break
            }
            builder.append(value, index, ampersand)
            val semicolon = value.indexOf(';', ampersand + 1)
            if (semicolon < 0 || semicolon - ampersand > 12) {
                builder.append('&')
                index = ampersand + 1
                continue
            }
            val entity = value.substring(ampersand + 1, semicolon)
            val decoded = when {
                entity == "amp" -> "&"
                entity == "lt" -> "<"
                entity == "gt" -> ">"
                entity == "quot" -> "\""
                entity == "apos" -> "'"
                entity == "nbsp" -> " "
                entity == "mdash" -> "—"
                entity == "ndash" -> "–"
                entity == "hellip" -> "…"
                entity == "ldquo" -> "“"
                entity == "rdquo" -> "”"
                entity == "lsquo" -> "‘"
                entity == "rsquo" -> "’"
                entity == "emsp" -> " "
                entity == "ensp" -> " "
                entity == "thinsp" -> " "
                entity.startsWith("#x", ignoreCase = true) ->
                    entity.drop(2).toIntOrNull(16)?.takeIf(::isValidCodePoint)?.let(::codePointToString)
                entity.startsWith("#") ->
                    entity.drop(1).toIntOrNull()?.takeIf(::isValidCodePoint)?.let(::codePointToString)
                else -> null
            }
            if (decoded == null) {
                builder.append(value, ampersand, semicolon + 1)
            } else {
                builder.append(decoded)
            }
            index = semicolon + 1
        }
        return builder.toString()
    }

    private fun isValidCodePoint(value: Int): Boolean =
        value in 0..0x10FFFF && value !in 0xD800..0xDFFF

    private fun codePointToString(value: Int): String =
        String(Character.toChars(value))
}
