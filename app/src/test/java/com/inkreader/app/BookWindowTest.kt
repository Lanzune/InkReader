// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.app

import com.inkreader.kernel.api.BookDocument
import com.inkreader.kernel.api.BookMetadata
import com.inkreader.kernel.api.Chapter
import com.inkreader.kernel.api.ChapterImage
import com.inkreader.kernel.api.ChapterLink
import com.inkreader.kernel.api.ChapterMark
import com.inkreader.kernel.api.ChapterStyle
import com.inkreader.kernel.api.InlineStyleKind
import com.inkreader.kernel.api.LayoutKind
import com.inkreader.kernel.api.TocEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BookWindowTest {
    @Test
    fun appendsWindowAtTheExpectedGlobalBase() {
        val current = document(
            text = "A",
            base = 0,
            mark = ChapterMark("a.xhtml", 0, 1, 0),
        )
        val window = document(
            text = "B",
            base = 3,
            mark = ChapterMark("b.xhtml", 3, 1, 1),
        )

        val merged = appendWindow(current, window)
        assertNotNull(merged)
        val chapter = checkNotNull(merged).chapters.single()
        assertEquals("A\n\nB", chapter.text)
        assertEquals(3, chapter.images[1].charOffset)
        assertEquals(3, chapter.styles[1].start)
        assertEquals(4, chapter.styles[1].endExclusive)
        assertEquals(3, chapter.links[1].start)
        assertEquals(4, chapter.links[1].endExclusive)
        assertEquals(listOf(0, 3), merged.toc.map { it.charOffset })
        assertEquals(2, merged.chapterMarks.size)
    }

    @Test
    fun rejectsWindowWithWrongBase() {
        val current = document(
            text = "A",
            base = 0,
            mark = ChapterMark("a.xhtml", 0, 1, 0),
        )
        val window = document(
            text = "B",
            base = 99,
            mark = ChapterMark("b.xhtml", 99, 1, 1),
        )

        assertEquals(null, appendWindow(current, window))
    }

    private fun document(
        text: String,
        base: Int,
        mark: ChapterMark,
    ): BookDocument = BookDocument(
        metadata = BookMetadata(title = "test"),
        formatId = "epub",
        layout = LayoutKind.REFLOWABLE,
        chapters = listOf(
            Chapter(
                id = "chapter",
                title = "test",
                text = text,
                baseOffset = base,
                images = listOf(ChapterImage(0, "a.jpg")),
                styles = listOf(ChapterStyle(0, 1, InlineStyleKind.BOLD)),
                links = listOf(ChapterLink(0, 1, targetOffset = 0)),
            ),
        ),
        toc = listOf(TocEntry("chapter", base)),
        chapterMarks = listOf(mark),
    )
}
