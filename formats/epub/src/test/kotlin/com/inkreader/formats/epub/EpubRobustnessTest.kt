package com.inkreader.formats.epub

import com.inkreader.kernel.api.BookReadException
import com.inkreader.kernel.api.OpenContext
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubRobustnessTest {
    private val provider = EpubFormatProvider()

    @Test
    fun failsClearlyWhenOpfIsMissing() {
        val source = FakeEpubSource(
            displayName = "broken.epub",
            mimeType = "application/epub+zip",
            bytes = zipOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """
                    <container><rootfiles><rootfile full-path="OEBPS/missing.opf"/></rootfiles></container>
                """.trimIndent(),
            ),
        )
        val failure = runCatching {
            provider.open(source, OpenContext(source.displayName, source.mimeType))
        }.exceptionOrNull()
        assertTrue(failure is BookReadException)
    }

    @Test
    fun failsClearlyOnMalformedOpf() {
        val source = FakeEpubSource(
            displayName = "broken.epub",
            mimeType = "application/epub+zip",
            bytes = zipOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """
                    <container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>
                """.trimIndent(),
                "OEBPS/content.opf" to "<package><manifest>",
            ),
        )
        val failure = runCatching {
            provider.open(source, OpenContext(source.displayName, source.mimeType))
        }.exceptionOrNull()
        assertTrue(failure is BookReadException)
    }

    @Test
    fun rejectsPathTraversalEntries() {
        val bytes = zipOf("../outside.txt" to "no")
        val failure = runCatching { EpubArchive.open(bytes) }.exceptionOrNull()
        assertTrue(failure is BookReadException)
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, value) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
