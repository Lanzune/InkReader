package com.inkreader.reader.engine

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View

/**
 * Link span used by the reader's Canvas layout. ReaderSurfaceView hit-tests
 * this span and jumps to [targetOffset]; the framework's onClick is unused.
 */
class ReaderLinkSpan(
    val targetOffset: Int,
) : ClickableSpan() {
    override fun updateDrawState(ds: TextPaint) {
        ds.isUnderlineText = true
        ds.color = 0xFF6A4C93.toInt()
    }

    override fun onClick(widget: View) = Unit
}
