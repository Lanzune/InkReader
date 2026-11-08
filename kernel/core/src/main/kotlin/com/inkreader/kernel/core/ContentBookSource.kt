// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.kernel.core

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.inkreader.kernel.api.BookReadException
import com.inkreader.kernel.api.BookSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * 通过 SAF content URI 读取书籍。
 *
 * M1 一次性读入内存，方便先打通链路；M2 会改成按偏移读取，避免大文件 OOM。
 */
class ContentBookSource(
    private val context: Context,
    private val uri: Uri,
) : BookSource {

    private val resolver: ContentResolver = context.contentResolver
    @Volatile private var preparedFile: File? = null

    override val displayName: String by lazy {
        queryName() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "未命名书籍"
    }

    override val mimeType: String? by lazy { resolver.getType(uri) }

    override val size: Long by lazy { querySize() ?: -1L }

    private val bytes: ByteArray by lazy {
        try {
            resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw BookReadException("无法打开文件：$displayName")
        } catch (error: IOException) {
            throw BookReadException("读取文件失败：$displayName", error)
        } catch (error: SecurityException) {
            throw BookReadException("没有读取该文件的权限：$displayName", error)
        }
    }

    override fun readAll(): ByteArray {
        // The preview path already materialised a local copy; reuse it instead
        // of asking the provider for the whole file a second time. Formats that
        // never asked for random access still stream once, without a cache copy.
        val cached = preparedFile
        if (cached != null) return cached.readBytes()
        return bytes
    }

    override fun readPrefix(maxBytes: Int): ByteArray {
        if (maxBytes <= 0) return ByteArray(0)
        preparedFile?.let { file ->
            return file.inputStream().use { input -> readUpTo(input, maxBytes) }
        }
        return try {
            resolver.openInputStream(uri)?.use { input -> readUpTo(input, maxBytes) }
                ?: ByteArray(0)
        } catch (_: IOException) {
            ByteArray(0)
        } catch (_: SecurityException) {
            ByteArray(0)
        }
    }

    private fun readUpTo(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var remaining = maxBytes
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read <= 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
        return output.toByteArray()
    }

    /**
     * SAF gives us a stream, while EPUB random access needs a real file.
     * Materialise one cached copy per URI so previews and reopens can seek
     * without reading the whole archive into memory.
     */
    override fun localFile(): File? {
        preparedFile?.let { return it }
        val file = runCatching { cachedFile() }.getOrNull() ?: return null
        preparedFile = file
        return file
    }

    @Synchronized
    private fun cachedFile(): File? {
        val expected = size
        val directory = File(context.cacheDir, "book-source").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uri.toString().toByteArray())
            .joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
        val target = File(directory, "$digest.book")
        if (target.isFile && target.length() > 0 && (expected <= 0 || target.length() == expected)) {
            return target
        }

        val temporary = File(directory, "$digest.tmp")
        temporary.delete()
        try {
            resolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            } ?: return null
            if (expected > 0 && temporary.length() != expected) {
                temporary.delete()
                return null
            }
            if (!temporary.renameTo(target)) {
                target.delete()
                if (!temporary.renameTo(target)) {
                    temporary.delete()
                    return null
                }
            }
            pruneCache(directory, target)
            return target
        } catch (error: IOException) {
            temporary.delete()
            throw error
        }
    }

    private fun pruneCache(directory: File, keep: File) {
        val files = directory.listFiles { file -> file.extension == "book" }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        var total = 0L
        files.forEachIndexed { index, file ->
            total += file.length()
            if (file != keep && (index >= MAX_CACHED_BOOKS || total > MAX_CACHE_BYTES)) {
                file.delete()
            }
        }
    }

    private fun queryName(): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
    }.getOrNull()

    private fun querySize(): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
            } else {
                null
            }
        }
    }.getOrNull()

    override fun close() = Unit

    private companion object {
        const val MAX_CACHED_BOOKS = 3
        const val MAX_CACHE_BYTES = 64L * 1024 * 1024
    }
}
