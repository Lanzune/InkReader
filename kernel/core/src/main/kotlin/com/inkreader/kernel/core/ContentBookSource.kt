// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.kernel.core

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.inkreader.kernel.api.BookReadException
import com.inkreader.kernel.api.BookSource
import java.io.IOException

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

    override fun readAll(): ByteArray = bytes

    override fun close() = Unit

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
}
