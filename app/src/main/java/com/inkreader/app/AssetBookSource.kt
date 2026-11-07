package com.inkreader.app

import android.content.res.AssetManager
import com.inkreader.kernel.api.BookSource

/** Debug-only asset source used to validate EPUB on a device without touching user files. */
class AssetBookSource(
    private val assets: AssetManager,
    private val assetPath: String,
    override val displayName: String,
    private val assetMimeType: String = "application/epub+zip",
) : BookSource {
    override val mimeType: String = assetMimeType
    override val size: Long = 0L

    override fun readAll(): ByteArray =
        assets.open(assetPath).use { it.readBytes() }

    override fun close() = Unit
}
