package com.inkreader.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Owns one PdfRenderer/FD and serializes page rendering.
 *
 * A non-seekable content provider is copied to the app cache first; the
 * temporary file is removed on close. Only the current page bitmap is kept.
 */
class PdfDocumentController(
    context: Context,
    uri: Uri,
) : AutoCloseable {
    private val lock = Any()
    private var temporaryFile: File? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    val pageCount: Int

    init {
        val opened = openRenderer(context, uri)
        renderer = opened.first
        descriptor = opened.second
        temporaryFile = opened.third
        pageCount = opened.first.pageCount
    }

    fun renderPage(index: Int, targetWidth: Int): Bitmap? = synchronized(lock) {
        val pdfRenderer = renderer ?: return null
        if (index !in 0 until pdfRenderer.pageCount) return null
        val width = targetWidth.coerceIn(240, 2_400)
        pdfRenderer.openPage(index).use { page ->
            val height = ((width.toFloat() * page.height / page.width).toInt()).coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { renderer?.close() }
            renderer = null
            runCatching { descriptor?.close() }
            descriptor = null
            temporaryFile?.delete()
            temporaryFile = null
        }
    }

    private fun openRenderer(
        context: Context,
        uri: Uri,
    ): Triple<PdfRenderer, ParcelFileDescriptor, File?> {
        if (uri.scheme == "asset") {
            val assetPath = uri.path?.trimStart('/').orEmpty()
            val temp = File.createTempFile("inkreader-pdf-", ".pdf", context.cacheDir)
            try {
                context.assets.open(assetPath).use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                val pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                val opened = PdfRenderer(pfd)
                return Triple(opened, pfd, temp)
            } catch (failure: Exception) {
                temp.delete()
                throw IllegalStateException(
                    "无法打开示例 PDF：${failure.message ?: "文件损坏"}",
                    failure,
                )
            }
        }

        val direct = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")
        }.getOrNull()
        if (direct != null) {
            val opened = runCatching { PdfRenderer(direct) }.getOrNull()
            if (opened != null) {
                return Triple(opened, direct, null)
            }
            runCatching { direct.close() }
        }

        val temp = File.createTempFile("inkreader-pdf-", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取 PDF" }
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            val pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
            val opened = PdfRenderer(pfd)
            return Triple(opened, pfd, temp)
        } catch (failure: Exception) {
            temp.delete()
            throw IllegalStateException(
                "无法打开 PDF：${failure.message ?: "文件损坏或加密"}",
                failure,
            )
        }
    }
}
