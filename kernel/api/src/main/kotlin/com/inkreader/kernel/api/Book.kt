// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.kernel.api

/**
 * 只读书籍源。
 *
 * M1 先提供整本书读取；M2 会扩展为按偏移读取，以支持大文件而不整本入内存。
 * 这里刻意不暴露 Android Uri/File，便于格式模块保持纯 Kotlin。
 */
interface BookSource {
    val displayName: String
    val mimeType: String?
    val size: Long
    fun readAll(): ByteArray

    /** First [maxBytes] bytes; formats use this for a cheap first screen. */
    fun readPrefix(maxBytes: Int): ByteArray {
        if (maxBytes <= 0) return ByteArray(0)
        val all = readAll()
        return if (all.size <= maxBytes) all else all.copyOf(maxBytes)
    }

    fun close()

    /** Optional local file for formats that can use random access (EPUB preview). */
    fun localFile(): java.io.File? = null
}

data class BookMetadata(
    val title: String,
    val author: String? = null,
    val language: String? = null,
)

data class ChapterImage(
    val charOffset: Int,
    val resource: String,
    val alt: String = "",
)

enum class InlineStyleKind {
    BOLD,
    ITALIC,
    HEADING1,
    HEADING2,
    HEADING3,
    INDENT,
    ALIGN_CENTER,
    ALIGN_RIGHT,
    ALIGN_LEFT,
    FIRST_LINE_INDENT,
    EPIGRAPH,
    EPIGRAPH_SOURCE,
    NOTE,
    NOTE_SEPARATOR,
}

data class ChapterStyle(
    val start: Int,
    val endExclusive: Int,
    val kind: InlineStyleKind,
)

data class ChapterLink(
    val start: Int,
    val endExclusive: Int,
    val targetOffset: Int,
    /** Stable EPUB path#fragment; used when the target is outside the loaded window. */
    val targetKey: String? = null,
)

data class Chapter(
    val id: String,
    val title: String,
    val text: String,
    val images: List<ChapterImage> = emptyList(),
    val styles: List<ChapterStyle> = emptyList(),
    val links: List<ChapterLink> = emptyList(),
    /**
     * Global character offset of [text] inside the whole book. A partial
     * preview that starts at a later chapter carries the skipped prefix here
     * so reading positions stay stable when the full book is loaded.
     */
    val baseOffset: Int = 0,
    /**
     * Chapter-local offsets that must start a new reading page. These come
     * from the EPUB navigation/spine/heading structure, not from heuristics.
     */
    val pageBreaks: List<Int> = emptyList(),
)

/** Spine-level offset map used to reopen near the saved position. */
data class ChapterMark(
    val path: String,
    val charOffset: Int,
    val length: Int,
    /** Position in the EPUB spine; used to append the next window. */
    val spineIndex: Int = 0,
)

data class TocEntry(
    val title: String,
    val charOffset: Int,
    /** 0 = top level; descendants keep the EPUB navigation depth. */
    val depth: Int = 0,
    /** EPUB path#fragment; used to resolve entries outside the preview window. */
    val targetKey: String? = null,
)

enum class LayoutKind {
    REFLOWABLE,
    FIXED_PAGE,
}

data class BookDocument(
    val metadata: BookMetadata,
    val formatId: String,
    val layout: LayoutKind,
    val chapters: List<Chapter>,
    val toc: List<TocEntry> = emptyList(),
    val resources: Map<String, ByteArray> = emptyMap(),
    /** Global spine offsets; empty for formats without a chapter map. */
    val chapterMarks: List<ChapterMark> = emptyList(),
    /** Stable EPUB path#fragment -> global character offset. */
    val internalTargets: Map<String, Int> = emptyMap(),
)

data class FormatMatch(
    val formatId: String,
    val confidence: Int,
)

data class OpenContext(
    val displayName: String,
    val mimeType: String? = null,
    val encoding: String? = null,
)

interface FormatProvider {
    val id: String

    fun probe(source: BookSource): FormatMatch?

    fun open(source: BookSource, context: OpenContext): BookDocument
}

class UnsupportedBookFormatException(message: String) : Exception(message)

class BookReadException(message: String, cause: Throwable? = null) : Exception(message, cause)
