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

    data class RichText(
        val text: String,
        val images: List<RichImage>,
        val styles: List<RichStyle> = emptyList(),
    )

    private val headPattern = Regex("(?is)<head\\b.*?</head\\s*>")
    private val hiddenPattern = Regex(
        "(?is)<(script|style|noscript|svg|iframe|object|audio|video)\\b.*?</\\1\\s*>",
    )
    private val commentPattern = Regex("(?s)<!--.*?-->")
    private val breakPattern = Regex("(?i)<br\\s*/?>")
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
    private const val IMAGE_PLACEHOLDER = '\uFFFC'
    private const val BOLD_OPEN = '\uE000'
    private const val BOLD_CLOSE = '\uE001'
    private const val ITALIC_OPEN = '\uE002'
    private const val ITALIC_CLOSE = '\uE003'

    fun extract(html: String): String = extractRich(html).text

    fun extractRich(html: String): RichText {
        val images = mutableListOf<Pair<String, String>>()
        var text = markStyles(html)
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
        text = breakPattern.replace(text, "\n")
        text = blockEndPattern.replace(text, "\n\n")
        text = tagPattern.replace(text, "")
        text = decodeEntities(text)
        text = text.replace('\u00A0', ' ')
        text = text.replace("\r\n", "\n").replace('\r', '\n')
        text = text.lines().joinToString("\n") { line ->
            line.replace(Regex("[\\t ]+"), " ").trim()
        }
        text = text.replace(Regex("\\n{3,}"), "\n\n").trim()

        val clean = StringBuilder(text.length)
        val styles = mutableListOf<RichStyle>()
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
                else -> clean.append(character)
            }
        }
        val cleanText = clean.toString()

        val richImages = mutableListOf<RichImage>()
        var imageIndex = 0
        cleanText.forEachIndexed { offset, character ->
            if (character == IMAGE_PLACEHOLDER && imageIndex < images.size) {
                val (src, alt) = images[imageIndex]
                richImages.add(RichImage(src = src, alt = alt, charOffset = offset))
                imageIndex += 1
            }
        }
        return RichText(text = cleanText, images = richImages, styles = styles)
    }

    private fun markStyles(html: String): String {
        var text = boldTagPattern.replace(html) { match ->
            if (match.value.startsWith("</")) BOLD_CLOSE.toString() else BOLD_OPEN.toString()
        }
        text = italicTagPattern.replace(text) { match ->
            if (match.value.startsWith("</")) ITALIC_CLOSE.toString() else ITALIC_OPEN.toString()
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
