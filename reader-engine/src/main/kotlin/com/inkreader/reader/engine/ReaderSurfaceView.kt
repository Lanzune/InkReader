package com.inkreader.reader.engine

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.Spanned
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
    private var content: CharSequence = ""
    private var textLayout: StaticLayout? = null
    private var renderedPageLayout: StaticLayout? = null
    private var renderedPageKey: Int = -1
    private var pageStarts: IntArray = IntArray(0)
    private var currentPage: Int = 0
    private var pendingCharOffset: Int? = null
    private var ready = false
    private var pageBitmap: Bitmap? = null
    private var pageSnapshotCache: Bitmap? = null
    private var pageAnimator: ValueAnimator? = null
    private var animationProgress = 0f
    private var animationDirection = 1

    var pageTurnAnimationEnabled: Boolean = true

    var onPageChanged: ((PageInfo) -> Unit)? = null
    var onCenterTap: (() -> Unit)? = null
    var onLinkTap: ((Int) -> Unit)? = null

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
                if (handleLinkTap(e)) return true
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
        cancelPageAnimation()
        releasePageSnapshot()
        val restoreOffset = currentCharOffset()
        style = newStyle
        textPaint.textSize = sp(style.fontSizeSp)
        setPadding(dp(style.horizontalPaddingDp), dp(76), dp(style.horizontalPaddingDp), dp(52))
        renderedPageKey = -1
        if (ready) {
            rebuildLayout(restoreOffset)
        }
    }

    fun setContent(text: CharSequence, readerTheme: ReaderTheme = theme, initialCharOffset: Int = 0) {
        cancelPageAnimation()
        releasePageSnapshot()
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
        cancelPageAnimation()
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
        cancelPageAnimation()
        releasePageSnapshot()
        renderedPageKey = -1
        invalidate()
    }

    private fun handleLinkTap(event: MotionEvent): Boolean {
        val layout = textLayout ?: return false
        if (pageStarts.isEmpty()) return false
        val startLine = pageStarts[currentPage.coerceIn(0, pageStarts.size - 1)]
        val layoutY = event.y - contentTop - pageTopInset + layout.getLineTop(startLine)
        if (layoutY < 0f) return false
        val line = layout.getLineForVertical(layoutY.toInt())
        val x = event.x - contentLeft
        if (x < 0f || x > contentWidth) return false
        val offset = layout.getOffsetForHorizontal(line, x)
        val spanned = content as? Spanned ?: return false
        val link = spanned.getSpans(offset, offset, ReaderLinkSpan::class.java).firstOrNull()
            ?: return false
        onLinkTap?.invoke(link.targetOffset)
        return true
    }

    fun nextPage(): Boolean {
        if (currentPage >= pageStarts.size - 1) return false
        cancelPageAnimation()
        val snapshot = if (pageTurnAnimationEnabled) capturePage() else null
        currentPage += 1
        renderedPageKey = -1
        notifyPage()
        if (snapshot != null) startPageAnimation(snapshot, 1) else invalidate()
        return true
    }

    fun previousPage(): Boolean {
        if (currentPage <= 0) return false
        cancelPageAnimation()
        val snapshot = if (pageTurnAnimationEnabled) capturePage() else null
        currentPage -= 1
        renderedPageKey = -1
        notifyPage()
        if (snapshot != null) startPageAnimation(snapshot, -1) else invalidate()
        return true
    }

    fun pageInfo(): PageInfo = PageInfo(
        page = currentPage,
        pageCount = pageStarts.size.coerceAtLeast(1),
        charOffset = currentCharOffset(),
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cancelPageAnimation()
        releasePageSnapshot()
        if (ready) {
            val restoreOffset = pendingCharOffset
            rebuildLayout(restoreOffset)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawCurrentPage(canvas)
        val bitmap = pageBitmap ?: return
        val offset = if (animationDirection > 0) {
            -animationProgress * width
        } else {
            animationProgress * width
        }
        canvas.drawBitmap(bitmap, offset, 0f, null)
    }

    private fun drawCurrentPage(canvas: Canvas) {
        val pageLayout = obtainPageLayout() ?: return
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

    private fun capturePage(): Bitmap? {
        if (width <= 0 || height <= 0 || obtainPageLayout() == null) return null
        val existing = pageSnapshotCache
        val bitmap = if (existing != null &&
            !existing.isRecycled &&
            existing.width == width &&
            existing.height == height
        ) {
            existing
        } else {
            existing?.recycle()
            Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).also {
                pageSnapshotCache = it
            }
        }
        val canvas = Canvas(bitmap)
        canvas.drawColor(theme.background)
        drawCurrentPage(canvas)
        return bitmap
    }

    private fun startPageAnimation(snapshot: Bitmap, direction: Int) {
        cancelPageAnimation()
        pageBitmap = snapshot
        animationDirection = direction
        animationProgress = 0f
        pageAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220L
            addUpdateListener {
                animationProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    pageBitmap = null
                    pageAnimator = null
                    animationProgress = 0f
                    invalidate()
                }
            })
            start()
        }
    }

    private fun cancelPageAnimation() {
        pageAnimator?.cancel()
        pageAnimator = null
        pageBitmap = null
        animationProgress = 0f
    }

    private fun releasePageSnapshot() {
        pageBitmap = null
        pageSnapshotCache?.recycle()
        pageSnapshotCache = null
    }

    override fun onDetachedFromWindow() {
        cancelPageAnimation()
        releasePageSnapshot()
        super.onDetachedFromWindow()
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

        val slice = content.subSequence(start, end)
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
