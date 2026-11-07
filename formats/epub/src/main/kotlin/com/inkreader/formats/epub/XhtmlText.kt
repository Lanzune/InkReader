package com.inkreader.formats.epub

/**
 * Deliberately small XHTML-to-text conversion for the M3 text reader.
 *
 * The reader currently consumes plain text. Rich blocks, images and CSS are a
 * later M3 increment; this extractor keeps paragraph boundaries and removes
 * scripts/styles so nothing executable reaches the reader.
 */
internal object XhtmlText {
    private val headPattern = Regex("(?is)<head\\b.*?</head\\s*>")
    private val hiddenPattern = Regex(
        "(?is)<(script|style|noscript|svg|iframe|object|audio|video)\\b.*?</\\1\\s*>",
    )
    private val commentPattern = Regex("(?s)<!--.*?-->")
    private val breakPattern = Regex("(?i)<br\\s*/?>")
    private val blockEndPattern = Regex(
        "(?i)</(p|div|h[1-6]|li|blockquote|tr|section|article|header|footer|figcaption|pre)\\s*>",
    )
    private val tagPattern = Regex("(?s)<[^>]+>")
    private val titlePattern = Regex("(?is)<title[^>]*>(.*?)</title\\s*>")

    fun extract(html: String): String {
        var text = html
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
        text = text.replace(Regex("\\n{3,}"), "\n\n")
        return text.trim()
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
