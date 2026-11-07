// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.formats.htmlmd

import com.inkreader.kernel.api.BookSource
import com.inkreader.kernel.api.OpenContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlMarkdownFormatProviderTest {
    private val provider = HtmlMarkdownFormatProvider()

    @Test
    fun rendersMarkdownHeadingsAndLinks() {
        val markdown = """
            # 第一章

            这是 **粗体** 和 *斜体* 以及 [链接](https://example.com) 和 `代码`。
            还有 [内部链接](#第二节)。

            ## 第二节

            - 项目一
            - 项目二
        """.trimIndent()
        val source = FakeSource("note.md", "text/markdown", markdown.toByteArray())
        val document = provider.open(source, OpenContext(source.displayName, source.mimeType))

        assertEquals("note", document.metadata.title)
        assertEquals("markdown", document.formatId)
        assertEquals(listOf("第一章", "第二节"), document.toc.map { it.title })
        assertTrue(document.chapters.single().text.contains("这是 粗体 和 斜体 以及 链接"))
        assertTrue(document.chapters.single().styles.any { it.kind.name == "HEADING1" })
        assertTrue(document.chapters.single().styles.any { it.kind.name == "BOLD" })
        assertTrue(document.chapters.single().styles.any { it.kind.name == "ITALIC" })
        val link = document.chapters.single().links.single()
        assertTrue(link.targetOffset > link.start)
    }

    @Test
    fun stripsHtmlScriptsAndDecodesEntities() {
        val html = """
            <html><head><style>p { color:red }</style></head>
            <body><h1>标题</h1><script>alert(1)</script><p>A &amp; B</p><p>C</p></body></html>
        """.trimIndent()
        val source = FakeSource("page.html", "text/html", html.toByteArray())
        val document = provider.open(source, OpenContext(source.displayName, source.mimeType))

        assertEquals("html", document.formatId)
        assertEquals("标题", document.toc.single().title)
        assertTrue(document.chapters.single().text.contains("A & B\n\nC"))
        assertTrue(!document.chapters.single().text.contains("alert"))
    }

    @Test
    fun decodesGbkMarkdown() {
        val source = FakeSource(
            "gbk.md",
            "text/markdown",
            "# 中文标题\n正文".toByteArray(charset("GB18030")),
        )
        val document = provider.open(source, OpenContext(source.displayName, source.mimeType))
        assertTrue(document.chapters.single().text.contains("中文标题"))
    }

    private class FakeSource(
        override val displayName: String,
        override val mimeType: String?,
        private val bytes: ByteArray,
    ) : BookSource {
        override val size: Long = bytes.size.toLong()
        override fun readAll(): ByteArray = bytes
        override fun close() = Unit
    }
}
