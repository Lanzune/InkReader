// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.formats.epub

import com.inkreader.kernel.api.OpenContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubFormatProviderTest {
    private val provider = EpubFormatProvider()

    @Test
    fun opensEpub3WithNavAndMetadata() {
        val source = FakeEpubSource(
            displayName = "sample.epub",
            mimeType = "application/epub+zip",
            bytes = EpubFixture.create(),
        )
        val document = provider.open(source, OpenContext(source.displayName, source.mimeType))

        assertEquals("Sample Book", document.metadata.title)
        assertEquals("Writer", document.metadata.author)
        assertEquals("epub", document.formatId)
        assertEquals(listOf("开始", "继续"), document.toc.map { it.title })
        assertEquals(0, document.toc.first().charOffset)
        assertTrue(document.toc[1].charOffset > document.toc[0].charOffset)

        val text = document.chapters.single().text
        assertTrue(text.contains("这是&第一段。"))
        assertTrue(text.contains("第二段 内容。"))
        assertTrue(text.contains("Another paragraph."))
        assertTrue(text.contains("[1]"))

        val image = document.chapters.single().images.single()
        assertEquals("图", image.alt)
        assertEquals(1, document.resources.size)
        assertTrue(document.resources.containsKey("OEBPS/images/sample.png"))

        assertTrue(document.chapters.single().styles.any { it.kind.name == "BOLD" })
        assertTrue(document.chapters.single().styles.any { it.kind.name == "ITALIC" })
        assertTrue(document.chapters.single().styles.any { it.kind.name == "HEADING1" })
        assertTrue(document.chapters.single().styles.any { it.kind.name == "ALIGN_CENTER" })
        assertTrue(document.chapters.single().styles.any { it.kind.name == "FIRST_LINE_INDENT" })

        val link = document.chapters.single().links.first()
        assertTrue(link.targetOffset > link.start)
        assertTrue(document.chapters.single().text.contains("注释内容。"))
        assertTrue(document.chapters.single().links.size == 2)
        assertTrue(document.chapters.single().text.contains("第二条注释。"))
        assertEquals(listOf(0, document.toc[1].charOffset), document.chapters.single().pageBreaks)
    }

    @Test
    fun coalescesAdjacentPartAndChapterHeadingsInOneFile() {
        val rich = XhtmlText.extractRich(
            "<html><body><h1 id=\"part\">第一部</h1><h2 id=\"ch1\">第一章</h2><p>第一章正文。</p><h2 id=\"ch2\">第二章</h2><p>第二章正文。</p></body></html>",
        )
        assertEquals(listOf("part", "ch1", "ch2"), rich.headings.map { it.id })
        val source = FakeEpubSource(
            displayName = "single.epub",
            mimeType = "application/epub+zip",
            bytes = EpubFixture.createSingleFileChapters(),
        )
        val document = provider.open(source, OpenContext(source.displayName, source.mimeType))
        val chapter = document.chapters.single()
        val second = chapter.text.indexOf("第二章")

        assertEquals(listOf(0, second), chapter.pageBreaks)
        assertTrue(chapter.styles.any { it.kind.name == "ALIGN_CENTER" })
        assertTrue(chapter.styles.any { it.kind.name == "FIRST_LINE_INDENT" })
        assertTrue(chapter.styles.any { it.kind.name == "NOTE" })
        assertTrue(chapter.styles.any { it.kind.name == "NOTE_SEPARATOR" })
        assertEquals(0, document.toc.first { it.title == "第一部" }.charOffset)
        assertEquals(listOf(0, 1, 1), document.toc.map { it.depth })
        assertEquals(0, document.toc.first { it.title == "第一章" }.charOffset)
        assertEquals(second, document.toc.first { it.title == "第二章" }.charOffset)
        assertTrue(chapter.pageBreaks[1] < chapter.text.length)
    }

    @Test
    fun fallsBackToHeadingsWithoutNavigation() {
        val source = FakeEpubSource(
            displayName = "nonav.epub",
            mimeType = "application/epub+zip",
            bytes = EpubFixture.createNoNavigation(),
        )
        val document = provider.open(source, OpenContext(source.displayName, source.mimeType))
        val chapter = document.chapters.single()

        assertEquals(
            listOf(0, chapter.text.indexOf("第二章")),
            chapter.pageBreaks,
        )
    }

    @Test
    fun keepsFootnoteReferenceInsideItsParagraph() {
        val rich = XhtmlText.extractRich(
            """
            <html><body><p>正文开始
              <a id="w1"></a>
              <a href="#m1"><sup>[1]</sup></a>
              后文继续。</p></body></html>
            """.trimIndent(),
        )
        assertFalse(rich.text.contains("\n[1]"))
        assertTrue(rich.text.contains("正文开始"))
        assertTrue(rich.text.contains("[1] 后文继续。"))
    }

    @Test
    fun detectsChapterStartEpigraphButNotMidChapterQuote() {
        val source = FakeEpubSource(
            displayName = "epigraph.epub",
            mimeType = "application/epub+zip",
            bytes = EpubFixture.createEpigraphBook(),
        )
        val document = provider.open(source, OpenContext(source.displayName, source.mimeType))
        val chapter = document.chapters.single()
        val epigraph = chapter.styles.filter { it.kind.name == "EPIGRAPH" }
        val sources = chapter.styles.filter { it.kind.name == "EPIGRAPH_SOURCE" }
        val bodyStart = chapter.text.indexOf("正文第一段")

        assertEquals(1, epigraph.size)
        assertEquals(1, sources.size)
        assertTrue(chapter.styles.any { it.kind.name == "ALIGN_CENTER" })
        assertTrue(epigraph.single().start < bodyStart)
        assertTrue(sources.single().start < bodyStart)
        assertTrue(
            chapter.styles.any {
                it.kind.name == "FIRST_LINE_INDENT" && bodyStart in it.start until it.endExclusive
            },
        )
        val reference = chapter.text.indexOf("[1]")
        val note = chapter.text.indexOf("注文")
        assertTrue(chapter.links.any { it.targetOffset in (note - 5)..(note + 10) })
        println("NOTE_TEST ref=$reference note=$note links=${chapter.links.map { it.start to it.targetOffset }} text=${chapter.text.replace("\n", "|")}")
        assertTrue(chapter.links.any {
            it.targetOffset in (reference - 5).coerceAtLeast(0)..(reference + 10)
        })
    }

    @Test
    fun doesNotApplyChineseParagraphIndentToEnglishBook() {
        val source = FakeEpubSource(
            displayName = "english.epub",
            mimeType = "application/epub+zip",
            bytes = EpubFixture.createNoNavigation("en"),
        )
        val document = provider.open(source, OpenContext(source.displayName, source.mimeType))
        val chapter = document.chapters.single()

        assertTrue(chapter.styles.none { it.kind.name == "FIRST_LINE_INDENT" })
    }

    @Test
    fun doesNotSplitLogicalChapterAcrossFiles() {
        val source = FakeEpubSource(
            displayName = "continuation.epub",
            mimeType = "application/epub+zip",
            bytes = EpubFixture.createContinuationFiles(),
        )
        val document = provider.open(source, OpenContext(source.displayName, source.mimeType))
        val chapter = document.chapters.single()

        assertEquals(listOf(0), chapter.pageBreaks)
    }

    @Test
    fun extractsHeadingRanges() {
        val rich = XhtmlText.extractRich(
            "<html><body><h1>Part</h1><h2>Chapter</h2><p>Body</p></body></html>",
        )
        assertEquals(listOf(1, 2), rich.headings.map { it.level })
        assertEquals("Part", rich.text.substring(rich.headings[0].start, rich.headings[0].endExclusive))
        assertEquals(
            "Chapter",
            rich.text.substring(rich.headings[1].start, rich.headings[1].endExclusive),
        )
    }

    @Test
    fun opensEpub2WithNcx() {
        val source = FakeEpubSource(
            displayName = "sample.epub",
            mimeType = "application/epub+zip",
            bytes = EpubFixture.create(useNcx = true),
        )
        val document = provider.open(source, OpenContext(source.displayName, source.mimeType))

        assertEquals(listOf("开始", "继续"), document.toc.map { it.title })
        assertTrue(document.chapters.single().text.contains("这是&第一段。"))
    }

    @Test
    fun stripsScriptsAndDecodesEntities() {
        val html = """
            <html><head><title>T</title><style>p { color: red; }</style></head>
            <body><script>alert(1)</script><p>A &amp; B &lt; C &#20320;&#x597D;.</p></body></html>
        """.trimIndent()

        val text = XhtmlText.extract(html)
        assertEquals("A & B < C 你好.", text)
    }

    @Test
    fun previewStartsAtRequestedChapterWithGlobalOffsets() {
        val bytes = EpubFixture.create()
        val file = java.io.File.createTempFile("inkreader-preview", ".epub").apply {
            writeBytes(bytes)
            deleteOnExit()
        }
        try {
            val source = FakeEpubSource(
                displayName = "sample.epub",
                mimeType = "application/epub+zip",
                bytes = bytes,
                local = file,
            )
            val context = OpenContext(source.displayName, source.mimeType)
            val full = provider.open(source, context)
            val marks = full.chapterMarks
            assertEquals(2, marks.size)
            assertTrue(marks[1].charOffset > marks[0].charOffset)

            val preview = provider.openPreview(
                source = source,
                context = context,
                maxChapters = 1,
                startChapter = 1,
                knownMarks = marks,
            )
            checkNotNull(preview)
            val chapter = preview.chapters.single()
            assertEquals(marks[1].charOffset, chapter.baseOffset)
            assertEquals(marks[1].length, chapter.text.length)
            assertEquals(marks[1].charOffset, preview.toc.first { it.title == "继续" }.charOffset)
            val outside = preview.toc.first { it.charOffset < 0 }
            assertNotNull(outside.targetKey)
            // Chapter-local span offsets must not include the skipped prefix.
            assertTrue(chapter.images.single().charOffset < chapter.text.length)
            assertTrue(chapter.links.isEmpty())
        } finally {
            file.delete()
        }
    }

    @Test
    fun estimateTextLengthMatchesFullDocumentLength() {
        val bytes = EpubFixture.create()
        val file = java.io.File.createTempFile("inkreader-estimate", ".epub").apply {
            writeBytes(bytes)
            deleteOnExit()
        }
        try {
            val source = FakeEpubSource(
                displayName = "sample.epub",
                mimeType = "application/epub+zip",
                bytes = bytes,
                local = file,
            )
            val context = OpenContext(source.displayName, source.mimeType)
            val full = provider.open(source, context)
            val estimate = provider.estimateTextLength(source)
            assertEquals(full.chapters.single().text.length, estimate)
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsNonZip() {
        val source = FakeEpubSource(
            displayName = "bad.epub",
            mimeType = "application/epub+zip",
            bytes = "not a zip".toByteArray(),
        )
        val failure = runCatching {
            provider.open(source, OpenContext(source.displayName, source.mimeType))
        }.exceptionOrNull()
        assertTrue(failure != null)
    }
}
