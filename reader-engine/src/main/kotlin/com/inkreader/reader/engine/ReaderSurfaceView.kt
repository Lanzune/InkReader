package com.inkreader.reader.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextDirectionHeuristics
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.inkreader.kernel.api.BuiltInThemes
import com.inkreader.kernel.api.PageInfo
import com.inkreader.kernel.api.ReaderTheme
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Reader surface: line-level pagination with StaticLayout and Canvas.
 * Supports reflowable text, horizontal page turns, tap turns and themes.
 */
class ReaderSurfaceView(context: Context) : View(context) {

    private val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(20f)
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    }

    private var theme: ReaderTheme = BuiltInThemes.Light
    private var style: ReaderStyle = ReaderStyle()
    private var content: String = ""
    private var textLayout: StaticLayout? = null
    private var renderedPageLayout: StaticLayout? = null
    private var renderedPageKey: Int = -1
    private var pageStarts: IntArray = IntArray(0)
    private var currentPage: Int = 0
    private var pendingCharOffset: Int? = null
    private var ready = false

    var onPageChanged: ((PageInfo) -> Unit)? = null
    var onCenterTap: (() -> Unit)? = null

    private val contentLeft get() = paddingLeft
    private val contentTop get() = paddingTop
    private val contentWidth get() = (width - paddingLeft - paddingRight).coerceAtLeast(1)
    private val contentHeight get() = (height - paddingTop - paddingBottom - dp(8)).coerceAtLeast(1)

    /** Per-page ascent/descent safety insets. */
    private val pageTopInset get() = maxOf(dp(16), ceil(-textPaint.fontMetrics.top).toInt())
    private val pageBottomInset get() = maxOf(dp(20), ceil(textPaint.fontMetrics.bottom).toInt())
    private val paginationHeight get() = (contentHeight - pageTopInset - pageBottomInset).coerceAtLeast(1)

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                val first = e1 ?: return false
                val dx = first.x - e2.x
                val dy = first.y - e2.y
                if (abs(dx) < 80f || abs(dx) < abs(dy)) return false
                if (dx > 0) nextPage() else previousPage()
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                when {
                    e.x < width * 0.28f -> previousPage()
                    e.x > width * 0.72f -> nextPage()
                    else -> onCenterTap?.invoke()
                }
                return true
            }
        },
    )

    init {
        // Reserve top control/status area and bottom safe area.
        setPadding(dp(style.horizontalPaddingDp), dp(76), dp(style.horizontalPaddingDp), dp(52))
        textPaint.textSize = sp(style.fontSizeSp)
        setBackgroundColor(theme.background)
        renderedPageKey = -1
        isClickable = true
        isFocusable = true
    }

    fun applyStyle(newStyle: ReaderStyle) {
        if (style == newStyle) return
        val restoreOffset = currentCharOffset()
        style = newStyle
        textPaint.textSize = sp(style.fontSizeSp)
        setPadding(dp(style.horizontalPaddingDp), dp(76), dp(style.horizontalPaddingDp), dp(52))
        renderedPageKey = -1
        if (ready) {
            rebuildLayout(restoreOffset)
        }
    }

    fun setContent(text: String, readerTheme: ReaderTheme = theme, initialCharOffset: Int = 0) {
        content = text
        theme = readerTheme
        textPaint.color = theme.foreground
        setBackgroundColor(theme.background)
        currentPage = 0
        renderedPageKey = -1
        ready = true
        pendingCharOffset = initialCharOffset.coerceAtLeast(0)
        if (width > 0 && height > 0) {
            rebuildLayout(pendingCharOffset)
        }
    }

    fun goToCharOffset(charOffset: Int) {
        if (textLayout == null || pageStarts.isEmpty()) return
        currentPage = pageForOffset(charOffset)
        renderedPageKey = -1
        invalidate()
        notifyPage()
    }

    fun currentCharOffset(): Int {
        val layout = textLayout ?: return 0
        if (pageStarts.isEmpty()) return 0
        val line = pageStarts[currentPage.coerceIn(0, pageStarts.size - 1)]
        return layout.getLineStart(line)
    }

    fun setTheme(readerTheme: ReaderTheme) {
        theme = readerTheme
        textPaint.color = theme.foreground
        setBackgroundColor(theme.background)
        renderedPageKey = -1
        invalidate()
    }

    fun nextPage(): Boolean {
        if (currentPage >= pageStarts.size - 1) return false
        currentPage += 1
        renderedPageKey = -1
        invalidate()
        notifyPage()
        return true
    }

    fun previousPage(): Boolean {
        if (currentPage <= 0) return false
        currentPage -= 1
        renderedPageKey = -1
        invalidate()
        notifyPage()
        return true
    }

    fun pageInfo(): PageInfo = PageInfo(
        page = currentPage,
        pageCount = pageStarts.size.coerceAtLeast(1),
        charOffset = currentCharOffset(),
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (ready) {
            val restoreOffset = pendingCharOffset
            rebuildLayout(restoreOffset)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pageLayout = obtainPageLayout() ?: return

        // Draw only the current page's own layout, so previous lines cannot enter the top safe area.
        canvas.save()
        canvas.clipRect(
            contentLeft,
            contentTop,
            contentLeft + contentWidth,
            contentTop + contentHeight,
        )
        canvas.translate(contentLeft.toFloat(), (contentTop + pageTopInset).toFloat())
        pageLayout.draw(canvas)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gestureDetector.onTouchEvent(event)) return true
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun rebuildLayout(initialCharOffset: Int? = null) {
        val restoreOffset = initialCharOffset ?: pendingCharOffset ?: currentCharOffset()
        pendingCharOffset = null
        if (content.isBlank() || contentWidth <= 1 || contentHeight <= 1) {
            textLayout = null
            pageStarts = IntArray(0)
            currentPage = 0
            notifyPage()
            return
        }

        val layout = StaticLayout.Builder
            .obtain(content, 0, content.length, textPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, style.lineSpacingMultiplier)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .build()

        val starts = mutableListOf<Int>()
        var start = 0
        while (start < layout.lineCount) {
            val top = layout.getLineTop(start)
            var end = start
            while (end < layout.lineCount &&
                layout.getLineBottom(end) - top <= paginationHeight
            ) {
                end += 1
            }
            if (end == start) end = start + 1
            starts.add(start)
            start = end
        }

        textLayout = layout
        pageStarts = starts.toIntArray()
        renderedPageLayout = null
        renderedPageKey = -1
        currentPage = pageForOffset(restoreOffset)
        notifyPage()
        invalidate()
    }

    private fun obtainPageLayout(): StaticLayout? {
        val fullLayout = textLayout ?: return null
        if (pageStarts.isEmpty()) return null
        if (renderedPageKey == currentPage && renderedPageLayout != null) {
            return renderedPageLayout
        }

        val startLine = pageStarts[currentPage.coerceIn(0, pageStarts.size - 1)]
        val endLine = if (currentPage + 1 < pageStarts.size) {
            pageStarts[currentPage + 1]
        } else {
            fullLayout.lineCount
        }
        val start = fullLayout.getLineStart(startLine).coerceIn(0, content.length)
        val end = if (endLine < fullLayout.lineCount) {
            fullLayout.getLineStart(endLine)
        } else {
            content.length
        }.coerceIn(start, content.length)

        val slice = content.substring(start, end)
        if (slice.isEmpty()) return null

        val pageLayout = StaticLayout.Builder
            .obtain(slice, 0, slice.length, textPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, style.lineSpacingMultiplier)
            .setIncludePad(true)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .build()

        renderedPageLayout = pageLayout
        renderedPageKey = currentPage
        return pageLayout
    }

    private fun pageForOffset(charOffset: Int): Int {
        val layout = textLayout ?: return 0
        if (pageStarts.isEmpty()) return 0
        val safeOffset = charOffset.coerceIn(0, content.length.coerceAtLeast(0))
        val line = layout.getLineForOffset(safeOffset)
        var page = 0
        for (index in pageStarts.indices) {
            if (pageStarts[index] <= line) page = index else break
        }
        return page
    }

    private fun notifyPage() {
        onPageChanged?.invoke(pageInfo())
    }

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics,
    )

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics,
    ).toInt()
}
