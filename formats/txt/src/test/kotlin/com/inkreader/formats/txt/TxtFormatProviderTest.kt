// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.formats.txt

import com.inkreader.kernel.api.BookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class TxtFormatProviderTest {
    private class FakeSource(
        override val displayName: String,
        override val mimeType: String?,
        private val bytes: ByteArray,
    ) : BookSource {
        override val size: Long get() = bytes.size.toLong()
        override fun readAll(): ByteArray = bytes
        override fun close() = Unit
    }

    private val provider = TxtFormatProvider()

    @Test
    fun opensUtf8TextAndStripsBom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "第一章\n你好，世界。".toByteArray(StandardCharsets.UTF_8)
        val source = FakeSource("sample.txt", "text/plain", bytes)

        val document = provider.open(source, com.inkreader.kernel.api.OpenContext(source.displayName, source.mimeType))

        assertEquals("txt", document.formatId)
        assertEquals("sample", document.metadata.title)
        assertEquals("第一章\n你好，世界。", document.chapters.single().text)
    }

    @Test
    fun decodesGb18030() {
        val text = "这是简体中文测试，编码应该是GB18030。"
        val bytes = text.toByteArray(Charset.forName("GB18030"))
        val decoded = TxtDecoder.decode(bytes)
        assertEquals("GB18030", decoded.charsetName)
        assertEquals(text, decoded.text)
    }

    @Test
    fun decodesBig5() {
        val text = "這是繁體中文測試，編碼應該是Big5。"
        val bytes = text.toByteArray(Charset.forName("Big5"))
        val decoded = TxtDecoder.decode(bytes)
        assertEquals("Big5", decoded.charsetName)
        assertEquals(text, decoded.text)
    }

    @Test
    fun decodesUtf16LeWithBom() {
        val text = "UTF-16 测试文本"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            text.toByteArray(Charsets.UTF_16LE)
        val decoded = TxtDecoder.decode(bytes)
        assertEquals("UTF-16LE", decoded.charsetName)
        assertEquals(text, decoded.text)
    }

    @Test
    fun buildsChapterOffsets() {
        val text = "第一章 开始\n正文一。\n\n第二章 继续\n正文二。"
        val source = FakeSource("book.txt", "text/plain", text.toByteArray(Charsets.UTF_8))
        val document = provider.open(source, com.inkreader.kernel.api.OpenContext(source.displayName, source.mimeType))

        assertEquals(2, document.toc.size)
        assertEquals("第一章 开始", document.toc[0].title)
        assertEquals(text.indexOf("第一章 开始"), document.toc[0].charOffset)
        assertEquals(text.indexOf("第二章 继续"), document.toc[1].charOffset)
    }

    @Test
    fun probesTxtByExtension() {
        val source = FakeSource("novel.txt", null, ByteArray(0))
        assertTrue(provider.probe(source) != null)
    }
}
