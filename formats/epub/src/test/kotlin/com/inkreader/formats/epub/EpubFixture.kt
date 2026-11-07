package com.inkreader.formats.epub

import com.inkreader.kernel.api.BookSource
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class FakeEpubSource(
    override val displayName: String,
    override val mimeType: String?,
    private val bytes: ByteArray,
) : BookSource {
    override val size: Long = bytes.size.toLong()
    override fun readAll(): ByteArray = bytes
    override fun close() = Unit
}

internal object EpubFixture {
    fun create(
        title: String = "Sample Book",
        author: String = "Writer",
        useNcx: Boolean = false,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()

            val navManifest = if (useNcx) {
                """<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>"""
            } else {
                """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>"""
            }
            val spineToc = if (useNcx) " toc=\"ncx\"" else ""
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="bookid">urn:test:1</dc:identifier>
                    <dc:title>$title</dc:title>
                    <dc:creator>$author</dc:creator>
                    <dc:language>zh-CN</dc:language>
                  </metadata>
                  <manifest>
                    $navManifest
                    <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine$spineToc>
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                  </spine>
                </package>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()

            if (useNcx) {
                zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
                zip.write(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                      <navMap>
                        <navPoint id="n1" playOrder="1">
                          <navLabel><text>开始</text></navLabel>
                          <content src="chapter1.xhtml"/>
                        </navPoint>
                        <navPoint id="n2" playOrder="2">
                          <navLabel><text>继续</text></navLabel>
                          <content src="chapter2.xhtml"/>
                        </navPoint>
                      </navMap>
                    </ncx>
                    """.trimIndent().toByteArray(),
                )
                zip.closeEntry()
            } else {
                zip.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
                zip.write(
                    """
                    <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                      <body>
                        <nav epub:type="toc">
                          <ol>
                            <li><a href="chapter1.xhtml">开始</a></li>
                            <li><a href="chapter2.xhtml">继续</a></li>
                          </ol>
                        </nav>
                      </body>
                    </html>
                    """.trimIndent().toByteArray(),
                )
                zip.closeEntry()
            }

            zip.putNextEntry(ZipEntry("OEBPS/chapter1.xhtml"))
            zip.write(
                """
                <html><head><title>第一章</title></head><body>
                  <h1>第一章</h1>
                  <p>这是&amp;第一段。</p>
                  <p>第二段&nbsp;内容。</p>
                </body></html>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/chapter2.xhtml"))
            zip.write(
                """
                <html><head><title>第二章</title></head><body>
                  <h1>第二章</h1>
                  <p>Another paragraph.</p>
                  <p><img src="images/sample.png" alt="图"/></p>
                </body></html>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/images/sample.png"))
            zip.write(
                Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Zl9sAAAAASUVORK5CYII=",
                ),
            )
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
