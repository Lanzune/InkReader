// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import com.inkreader.reader.engine.AsyncPageImage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * EPUB image drawable that reads only image bounds up front and decodes the
 * bitmap on a worker before the page is painted. ReaderSurfaceView calls
 * preload() for the current/next page, so images never pop in during drawing.
 */
class LazyEpubDrawable(
    private val bytes: ByteArray,
    maxWidth: Int,
    maxHeight: Int,
) : Drawable(), AsyncPageImage {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val sourceWidth: Int
    private val sourceHeight: Int
    private val drawWidth: Int
    private val drawHeight: Int
    private val sampleSize: Int
    @Volatile private var bitmap: Bitmap? = null
    @Volatile private var failed: Boolean = false
    @Volatile private var loading: Boolean = false
    private val callbacks = CopyOnWriteArrayList<() -> Unit>()

    init {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        sourceWidth = bounds.outWidth
        sourceHeight = bounds.outHeight
        val scale = if (sourceWidth > 0 && sourceHeight > 0) {
            min(
                1f,
                min(
                    maxWidth.toFloat() / sourceWidth,
                    maxHeight.toFloat() / sourceHeight,
                ),
            )
        } else {
            0f
        }
        drawWidth = (sourceWidth * scale).toInt().coerceAtLeast(1)
        drawHeight = (sourceHeight * scale).toInt().coerceAtLeast(1)
        var sample = 1
        while (sourceWidth / sample > maxWidth * 2) sample *= 2
        sampleSize = sample
    }

    val isValid: Boolean get() = sourceWidth > 0 && sourceHeight > 0

    override val isReady: Boolean
        get() = bitmap != null || failed

    override fun preload(onReady: () -> Unit) {
        if (isReady) {
            onReady()
            return
        }
        callbacks.add(onReady)
        if (loading) return
        loading = true
        IMAGE_EXECUTOR.execute {
            val image = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            )
            bitmap = image
            failed = image == null
            loading = false
            val pending = callbacks.toList()
            callbacks.clear()
            pending.forEach { it() }
        }
    }

    override fun getIntrinsicWidth(): Int = drawWidth

    override fun getIntrinsicHeight(): Int = drawHeight

    override fun draw(canvas: Canvas) {
        val image = bitmap ?: return
        canvas.drawBitmap(image, null, bounds, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        val IMAGE_EXECUTOR = Executors.newFixedThreadPool(2)
    }
}
