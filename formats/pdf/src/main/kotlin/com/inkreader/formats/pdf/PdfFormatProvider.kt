// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.formats.pdf

import com.inkreader.kernel.api.BookDocument
import com.inkreader.kernel.api.BookMetadata
import com.inkreader.kernel.api.BookSource
import com.inkreader.kernel.api.FormatMatch
import com.inkreader.kernel.api.FormatProvider
import com.inkreader.kernel.api.LayoutKind
import com.inkreader.kernel.api.OpenContext

/**
 * PDF is a fixed-page format handled by the platform PdfRenderer in the app.
 * The provider only identifies the book and supplies metadata; page rendering
 * stays in the Android UI layer to keep this module pure Kotlin.
 */
class PdfFormatProvider : FormatProvider {
    override val id: String = "pdf"

    override fun probe(source: BookSource): FormatMatch? {
        val name = source.displayName.lowercase()
        val mime = source.mimeType?.lowercase().orEmpty()
        return when {
            name.endsWith(".pdf") -> FormatMatch(id, 99)
            mime == "application/pdf" -> FormatMatch(id, 98)
            else -> null
        }
    }

    override fun open(source: BookSource, context: OpenContext): BookDocument {
        val title = source.displayName
            .substringBeforeLast('.')
            .ifBlank { "未命名 PDF" }
        return BookDocument(
            metadata = BookMetadata(title = title),
            formatId = id,
            layout = LayoutKind.FIXED_PAGE,
            chapters = emptyList(),
            toc = emptyList(),
        )
    }
}
