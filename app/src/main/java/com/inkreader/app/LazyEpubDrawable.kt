package com.inkreader.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * EPUB image drawable that reads only image bounds up front and decodes the
 * bitmap when the page is actually drawn. Large illustrated books otherwise
 * decode every image during opening and become slow / memory hungry.
 */
class LazyEpubDrawable(
    private val bytes: ByteArray,
    maxWidth: Int,
    maxHeight: Int,
) : Drawable() {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val sourceWidth: Int
    private val sourceHeight: Int
    private val drawWidth: Int
    private val drawHeight: Int
    private val sampleSize: Int
    private var bitmap: Bitmap? = null
    private var failed: Boolean = false

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

    override fun getIntrinsicWidth(): Int = drawWidth

    override fun getIntrinsicHeight(): Int = drawHeight

    override fun draw(canvas: Canvas) {
        val image = bitmap ?: decodeBitmap() ?: return
        canvas.drawBitmap(image, null, bounds, paint)
    }

    private fun decodeBitmap(): Bitmap? {
        if (failed) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (image == null) {
            failed = true
            return null
        }
        bitmap = image
        return image
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
