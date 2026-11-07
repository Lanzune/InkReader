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

    override fun readAll(): ByteArray = file.readBytes()

    override fun close() = Unit
}
