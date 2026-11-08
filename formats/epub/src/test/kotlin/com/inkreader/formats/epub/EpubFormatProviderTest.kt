// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.formats.epub

import com.inkreader.kernel.api.OpenContext
import org.junit.Assert.assertEquals
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
            assertEquals(marks[1].charOffset, preview.toc.first().charOffset)
            // Chapter-local span offsets must not include the skipped prefix.
            assertTrue(chapter.images.single().charOffset < chapter.text.length)
            assertTrue(chapter.links.isEmpty())
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
