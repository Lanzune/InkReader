// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.reader.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Spanned
import android.text.style.LineBackgroundSpan

/**
 * Thin rule above the first line of a note group.
 *
 * The span carries the absolute note start so a page slice that begins inside
 * the note group can remove the rule instead of drawing it mid-page.
 */
class FootnoteSeparatorSpan(
    val noteStart: Int,
    private val insetPx: Float,
) : LineBackgroundSpan {
    override fun drawBackground(
        canvas: Canvas,
        paint: Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int,
    ) {
        val spanStart = (text as? Spanned)?.getSpanStart(this) ?: 0
        if (start > spanStart) return
        val oldColor = paint.color
        val oldStrokeWidth = paint.strokeWidth
        val oldStyle = paint.style
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        val y = top + 2f
        canvas.drawLine(
            left + insetPx,
            y,
            right - insetPx,
            y,
            paint,
        )
        paint.style = oldStyle
        paint.strokeWidth = oldStrokeWidth
        paint.color = oldColor
    }
}
