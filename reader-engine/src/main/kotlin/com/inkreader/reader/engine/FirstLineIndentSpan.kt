// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.reader.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.style.LeadingMarginSpan

/**
 * First-line indent that remembers the paragraph start.
 *
 * A page can start in the middle of a paragraph. In that case the leading
 * margin must not be applied again to the continuation line, so the reader
 * removes spans whose [paragraphStart] is before the page slice start.
 */
class FirstLineIndentSpan(
    private val indentPx: Int,
    val paragraphStart: Int,
) : LeadingMarginSpan {
    override fun getLeadingMargin(first: Boolean): Int = if (first) indentPx else 0

    override fun drawLeadingMargin(
        canvas: Canvas,
        paint: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout,
    ) = Unit
}
