package com.inkreader.formats.txt

import com.inkreader.kernel.api.BookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
    fun probesTxtByExtension() {
        val source = FakeSource("novel.txt", null, ByteArray(0))
        assertTrue(provider.probe(source) != null)
    }
}
