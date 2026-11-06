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

/**
 * M1 的最小阅读视图：用 StaticLayout 做行级分页，Canvas 绘制一页。
 *
 * 只支持重排文本、左右翻页/点击翻页和明暗主题；图片、选择和动画在后续模块补。
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
    private var pageStarts: IntArray = IntArray(0)
    private var currentPage: Int = 0
    private var pendingCharOffset: Int? = null
    private var ready = false

    var onPageChanged: ((PageInfo) -> Unit)? = null
    var onCenterTap: (() -> Unit)? = null

    private val contentLeft get() = paddingLeft
    private val contentTop get() = paddingTop
    private val contentWidth get() = (width - paddingLeft - paddingRight).coerceAtLeast(1)
    private val contentHeight get() = (height - paddingTop - paddingBottom).coerceAtLeast(1)

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
        // 顶部预留控制栏/状态栏空间，避免显示控制栏时改变阅读视图尺寸而重新分页。
        setPadding(dp(style.horizontalPaddingDp), dp(88), dp(style.horizontalPaddingDp), dp(40))
        textPaint.textSize = sp(style.fontSizeSp)
        setBackgroundColor(theme.background)
        isClickable = true
        isFocusable = true
    }

    fun applyStyle(newStyle: ReaderStyle) {
        if (style == newStyle) return
        val restoreOffset = currentCharOffset()
        style = newStyle
        textPaint.textSize = sp(style.fontSizeSp)
        setPadding(dp(style.horizontalPaddingDp), dp(88), dp(style.horizontalPaddingDp), dp(40))
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
        ready = true
        pendingCharOffset = initialCharOffset.coerceAtLeast(0)
        if (width > 0 && height > 0) {
            rebuildLayout(pendingCharOffset)
        }
    }

    fun goToCharOffset(charOffset: Int) {
        if (textLayout == null || pageStarts.isEmpty()) return
        currentPage = pageForOffset(charOffset)
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
        invalidate()
    }

    fun nextPage(): Boolean {
        if (currentPage >= pageStarts.size - 1) return false
        currentPage += 1
        invalidate()
        notifyPage()
        return true
    }

    fun previousPage(): Boolean {
        if (currentPage <= 0) return false
        currentPage -= 1
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
        val layout = textLayout ?: return
        if (pageStarts.isEmpty()) return

        val startLine = pageStarts[currentPage.coerceIn(0, pageStarts.size - 1)]
        canvas.save()
        canvas.clipRect(
            contentLeft,
            contentTop,
            contentLeft + contentWidth,
            contentTop + contentHeight,
        )
        canvas.translate(
            contentLeft.toFloat(),
            (contentTop - layout.getLineTop(startLine)).toFloat(),
        )
        layout.draw(canvas)
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
                layout.getLineBottom(end) - top <= contentHeight
            ) {
                end += 1
            }
            if (end == start) end = start + 1
            starts.add(start)
            start = end
        }

        textLayout = layout
        pageStarts = starts.toIntArray()
        currentPage = pageForOffset(restoreOffset)
        notifyPage()
        invalidate()
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
