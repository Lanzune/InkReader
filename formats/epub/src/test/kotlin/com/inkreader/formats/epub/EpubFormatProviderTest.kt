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

        val image = document.chapters.single().images.single()
        assertEquals("图", image.alt)
        assertEquals(1, document.resources.size)
        assertTrue(document.resources.containsKey("OEBPS/images/sample.png"))

        assertTrue(document.chapters.single().styles.any { it.kind.name == "BOLD" })
        assertTrue(document.chapters.single().styles.any { it.kind.name == "ITALIC" })
        assertTrue(document.chapters.single().styles.any { it.kind.name == "HEADING1" })
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
