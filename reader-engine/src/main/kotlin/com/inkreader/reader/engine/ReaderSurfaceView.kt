// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

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
import android.text.style.ImageSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.inkreader.kernel.api.BuiltInThemes
import com.inkreader.kernel.api.PageInfo
import com.inkreader.kernel.api.ReaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var contentBaseOffset: Int = 0
    private var ready = false
    private var pageBitmap: Bitmap? = null
    private var pageSnapshotCache: Bitmap? = null
    private var pageAnimator: ValueAnimator? = null
    private var animationProgress = 0f
    private var animationDirection = 1
    private var layoutJob: Job? = null
    private var layoutGeneration = 0
    private var localPageLayout: StaticLayout? = null
    private var localPageStart = 0
    private var localPageEnd = 0
    private var localPageNumber = 0
    private val layoutScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val LOCAL_WINDOW_CHARS = 16_000

    var pageTurnAnimationEnabled: Boolean = true

    var onPageChanged: ((PageInfo) -> Unit)? = null
    var onCenterTap: (() -> Unit)? = null
    var onLinkTap: ((Int) -> Unit)? = null
    var onPageReady: (() -> Unit)? = null
    private var pageDrawn = false

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
        val restoreOffset = currentCharOffset() - contentBaseOffset
        style = newStyle
        textPaint.textSize = sp(style.fontSizeSp)
        setPadding(dp(style.horizontalPaddingDp), dp(76), dp(style.horizontalPaddingDp), dp(52))
        renderedPageKey = -1
        if (ready) {
            startLayout(restoreOffset)
        }
    }

    fun setContent(
        text: CharSequence,
        readerTheme: ReaderTheme = theme,
        initialCharOffset: Int = 0,
        baseOffset: Int = 0,
    ) {
        cancelPageAnimation()
        releasePageSnapshot()
        content = text
        contentBaseOffset = baseOffset.coerceAtLeast(0)
        theme = readerTheme
        textPaint.color = theme.foreground
        setBackgroundColor(theme.background)
        currentPage = 0
        renderedPageKey = -1
        ready = true
        pageDrawn = false
        pendingCharOffset = (initialCharOffset - contentBaseOffset).coerceIn(0, text.length)
        if (width > 0 && height > 0) {
            startLayout(pendingCharOffset)
        }
    }

    fun goToCharOffset(charOffset: Int) {
        if (content.isBlank()) return
        val localOffset = (charOffset - contentBaseOffset).coerceIn(0, content.length)
        cancelPageAnimation()
        if (textLayout == null || pageStarts.isEmpty()) {
            buildLocalPage(localOffset)
            localPageNumber = 0
            currentPage = 0
            renderedPageKey = -1
            preloadPageImages(localPageStart, localPageEnd)
            notifyPage()
            invalidate()
            return
        }
        currentPage = pageForOffset(localOffset)
        renderedPageKey = -1
        preloadPageImages(pageStart(currentPage), pageEnd(currentPage))
        invalidate()
        notifyPage()
    }

    fun currentCharOffset(): Int {
        if (textLayout == null || pageStarts.isEmpty()) return contentBaseOffset + localPageStart
        val layout = textLayout ?: return contentBaseOffset + localPageStart
        val line = pageStarts[currentPage.coerceIn(0, pageStarts.size - 1)]
        return contentBaseOffset + layout.getLineStart(line)
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
        if (textLayout == null || pageStarts.isEmpty()) {
            if (localPageEnd >= content.length) return false
            cancelPageAnimation()
            buildLocalPage(localPageEnd)
            localPageNumber += 1
            currentPage = 0
            renderedPageKey = -1
            preloadPageImages(localPageStart, localPageEnd)
            preloadPageImages(localPageEnd, localPageEndFor(localPageEnd))
            notifyPage()
            invalidate()
            return true
        }
        if (currentPage >= pageStarts.size - 1) return false
        cancelPageAnimation()
        val snapshot = if (pageTurnAnimationEnabled) capturePage() else null
        currentPage += 1
        renderedPageKey = -1
        preloadPageImages(pageStart(currentPage), pageEnd(currentPage))
        preloadPageImages(pageEnd(currentPage), pageStart(currentPage + 1))
        notifyPage()
        if (snapshot != null) startPageAnimation(snapshot, 1) else invalidate()
        return true
    }

    fun previousPage(): Boolean {
        if (textLayout == null || pageStarts.isEmpty()) {
            if (localPageStart <= 0) return false
            cancelPageAnimation()
            if (!buildLocalPageBefore(localPageStart)) return false
            localPageNumber = (localPageNumber - 1).coerceAtLeast(0)
            currentPage = 0
            renderedPageKey = -1
            preloadPageImages(localPageStart, localPageEnd)
            notifyPage()
            invalidate()
            return true
        }
        if (currentPage <= 0) return false
        cancelPageAnimation()
        val snapshot = if (pageTurnAnimationEnabled) capturePage() else null
        currentPage -= 1
        renderedPageKey = -1
        preloadPageImages(pageStart(currentPage), pageEnd(currentPage))
        notifyPage()
        if (snapshot != null) startPageAnimation(snapshot, -1) else invalidate()
        return true
    }

    fun pageInfo(): PageInfo = PageInfo(
        page = if (textLayout == null) localPageNumber else currentPage,
        pageCount = pageStarts.size,
        charOffset = currentCharOffset(),
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cancelPageAnimation()
        releasePageSnapshot()
        if (ready) {
            val restoreOffset = pendingCharOffset
            startLayout(restoreOffset)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (textLayout == null && localPageLayout == null) return
        val start = if (textLayout == null) localPageStart else pageStart(currentPage)
        val end = if (textLayout == null) localPageEnd else pageEnd(currentPage)
        if (hasPendingPageImages(start, end)) return
        drawCurrentPage(canvas)
        if (!pageDrawn) {
            pageDrawn = true
            post { onPageReady?.invoke() }
        }
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
        layoutJob?.cancel()
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

    private fun startLayout(initialCharOffset: Int? = null) {
        val restoreOffset = initialCharOffset ?: pendingCharOffset
            ?: (currentCharOffset() - contentBaseOffset)
        pendingCharOffset = null
        layoutJob?.cancel()
        layoutGeneration += 1
        val generation = layoutGeneration
        localPageNumber = 0
        if (content.isBlank() || contentWidth <= 1 || contentHeight <= 1) {
            textLayout = null
            pageStarts = IntArray(0)
            localPageLayout = null
            currentPage = 0
            notifyPage()
            return
        }

        textLayout = null
        pageStarts = IntArray(0)
        renderedPageLayout = null
        renderedPageKey = -1
        currentPage = 0
        buildLocalPage(restoreOffset.coerceIn(0, content.length))
        preloadPageImages(localPageStart, localPageEnd)
        val nextEnd = localPageEndFor(localPageEnd)
        preloadPageImages(localPageEnd, nextEnd)
        notifyPage()
        invalidate()

        val paint = TextPaint(textPaint)
        val text = content
        val width = contentWidth
        val lineSpacing = style.lineSpacingMultiplier
        val pageHeight = paginationHeight
        layoutJob = layoutScope.launch {
            val layout = buildStaticLayout(text, paint, width, lineSpacing)
            val starts = mutableListOf<Int>()
            var start = 0
            while (start < layout.lineCount) {
                val top = layout.getLineTop(start)
                var end = start
                while (end < layout.lineCount &&
                    layout.getLineBottom(end) - top <= pageHeight
                ) {
                    end += 1
                }
                if (end == start) end = start + 1
                starts.add(start)
                start = end
            }
            withContext(Dispatchers.Main) {
                if (generation != layoutGeneration || text !== content) return@withContext
                val anchor = localPageStart
                textLayout = layout
                pageStarts = starts.toIntArray()
                localPageLayout = null
                renderedPageLayout = null
                renderedPageKey = -1
                currentPage = pageForOffset(anchor)
                preloadPageImages(pageStart(currentPage), pageEnd(currentPage))
                preloadPageImages(pageEnd(currentPage), pageStart(currentPage + 1))
                notifyPage()
                invalidate()
            }
        }
    }

    private fun buildStaticLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        spacing: Float,
    ): StaticLayout = StaticLayout.Builder
        .obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, spacing)
        .setIncludePad(false)
        .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
        .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
        .build()

    /**
     * Lays out one page directly instead of laying out the whole book.
     * This is the first-screen path; the full page index refreshes later.
     */
    private fun buildLocalPage(offset: Int) {
        val safeOffset = offset.coerceIn(0, content.length)
        val windowEnd = (safeOffset + LOCAL_WINDOW_CHARS).coerceAtMost(content.length)
        if (windowEnd <= safeOffset) {
            localPageLayout = null
            localPageStart = safeOffset
            localPageEnd = safeOffset
            return
        }
        val end = localPageEndFor(safeOffset)
        val page = content.subSequence(safeOffset, end)
        localPageLayout = buildStaticLayout(page, textPaint, contentWidth, style.lineSpacingMultiplier)
        localPageStart = safeOffset
        localPageEnd = end
    }

    private fun localPageEndFor(offset: Int): Int {
        val safeOffset = offset.coerceIn(0, content.length)
        val windowEnd = (safeOffset + LOCAL_WINDOW_CHARS).coerceAtMost(content.length)
        if (windowEnd <= safeOffset) return safeOffset
        val window = content.subSequence(safeOffset, windowEnd)
        val layout = buildStaticLayout(window, textPaint, contentWidth, style.lineSpacingMultiplier)
        var endLine = 0
        while (endLine < layout.lineCount &&
            layout.getLineBottom(endLine) - layout.getLineTop(0) <= paginationHeight
        ) {
            endLine += 1
        }
        if (endLine == 0) endLine = 1
        return if (endLine < layout.lineCount) {
            (safeOffset + layout.getLineStart(endLine)).coerceAtMost(content.length)
        } else {
            windowEnd
        }.coerceAtLeast(safeOffset + 1)
    }

    private fun pageStart(page: Int): Int {
        val full = textLayout
        if (full != null && pageStarts.isNotEmpty()) {
            val line = pageStarts[page.coerceIn(0, pageStarts.size - 1)]
            return full.getLineStart(line).coerceIn(0, content.length)
        }
        return if (page <= localPageNumber) localPageStart else localPageEnd
    }

    private fun pageEnd(page: Int): Int {
        val full = textLayout
        if (full != null && pageStarts.isNotEmpty()) {
            val next = page + 1
            if (next < pageStarts.size) {
                return full.getLineStart(pageStarts[next]).coerceIn(0, content.length)
            }
            return content.length
        }
        return localPageEnd
    }

    private fun preloadPageImages(start: Int, end: Int) {
        if (start >= end) return
        val spanned = content as? Spanned ?: return
        val spans = spanned.getSpans(start, end, ImageSpan::class.java)
        if (spans.isEmpty()) return
        for (span in spans) {
            val image = span.drawable as? AsyncPageImage ?: continue
            if (!image.isReady) {
                image.preload { postInvalidateOnAnimation() }
            }
        }
    }

    private fun hasPendingPageImages(start: Int, end: Int): Boolean {
        if (start >= end) return false
        val spanned = content as? Spanned ?: return false
        return spanned.getSpans(start, end, ImageSpan::class.java).any { span ->
            val image = span.drawable as? AsyncPageImage
            image != null && !image.isReady
        }
    }

    private fun buildLocalPageBefore(offset: Int): Boolean {
        val current = offset.coerceIn(0, content.length)
        if (current <= 0) return false
        val anchor = (current - LOCAL_WINDOW_CHARS).coerceAtLeast(0)
        val window = content.subSequence(anchor, current)
        val layout = buildStaticLayout(window, textPaint, contentWidth, style.lineSpacingMultiplier)
        val lineAtCurrent = layout.getLineCount() - 1
        var startLine = lineAtCurrent
        val bottom = layout.getLineBottom(lineAtCurrent)
        while (startLine > 0 &&
            bottom - layout.getLineTop(startLine - 1) <= paginationHeight
        ) {
            startLine -= 1
        }
        val start = (anchor + layout.getLineStart(startLine)).coerceAtMost(current - 1)
        buildLocalPage(start)
        return true
    }

    private fun obtainPageLayout(): StaticLayout? {
        val fullLayout = textLayout ?: return localPageLayout
        if (pageStarts.isEmpty()) return localPageLayout
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
