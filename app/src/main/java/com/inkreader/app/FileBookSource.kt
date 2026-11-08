// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.app

import com.inkreader.kernel.api.BookSource
import java.io.File

/** Reads a book restored from an InkReader backup into app-private storage. */
class FileBookSource(
    private val file: File,
    override val displayName: String,
    override val mimeType: String?,
) : BookSource {
    override val size: Long = file.length()

    override fun localFile(): File = file

    override fun readAll(): ByteArray = file.readBytes()

    override fun readPrefix(maxBytes: Int): ByteArray {
        if (maxBytes <= 0) return ByteArray(0)
        return file.inputStream().use { input ->
            val buffer = ByteArray(maxBytes)
            var offset = 0
            while (offset < maxBytes) {
                val read = input.read(buffer, offset, maxBytes - offset)
                if (read <= 0) break
                offset += read
            }
            if (offset == buffer.size) buffer else buffer.copyOf(offset)
        }
    }

    override fun close() = Unit
}
