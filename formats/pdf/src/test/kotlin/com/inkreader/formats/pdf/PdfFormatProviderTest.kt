package com.inkreader.formats.pdf

import com.inkreader.kernel.api.BookSource
import com.inkreader.kernel.api.OpenContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PdfFormatProviderTest {
    private val provider = PdfFormatProvider()

    @Test
    fun probesPdfByExtensionAndMime() {
        assertNotNull(provider.probe(FakeSource("book.pdf", "application/octet-stream")))
        assertNotNull(provider.probe(FakeSource("book.bin", "application/pdf")))
        assertEquals(null, provider.probe(FakeSource("book.txt", "text/plain")))
    }

    @Test
    fun producesFixedPageDocument() {
        val source = FakeSource("book.pdf", "application/pdf")
        val document = provider.open(source, OpenContext(source.displayName, source.mimeType))
        assertEquals("pdf", document.formatId)
        assertEquals("book", document.metadata.title)
        assertEquals(com.inkreader.kernel.api.LayoutKind.FIXED_PAGE, document.layout)
    }

    private class FakeSource(
        override val displayName: String,
        override val mimeType: String?,
    ) : BookSource {
        override val size: Long = 0
        override fun readAll(): ByteArray = ByteArray(0)
        override fun close() = Unit
    }
}
