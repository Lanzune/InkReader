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
    fun close()
}

data class BookMetadata(
    val title: String,
    val author: String? = null,
    val language: String? = null,
)

data class Chapter(
    val id: String,
    val title: String,
    val text: String,
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
)

data class FormatMatch(
    val formatId: String,
    val confidence: Int,
)

data class OpenContext(
    val displayName: String,
    val mimeType: String? = null,
)

interface FormatProvider {
    val id: String

    fun probe(source: BookSource): FormatMatch?

    fun open(source: BookSource, context: OpenContext): BookDocument
}

class UnsupportedBookFormatException(message: String) : Exception(message)

class BookReadException(message: String, cause: Throwable? = null) : Exception(message, cause)
