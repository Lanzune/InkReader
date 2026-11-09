// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

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
    private val local: java.io.File? = null,
) : BookSource {
    override val size: Long = bytes.size.toLong()
    override fun readAll(): ByteArray = bytes
    override fun localFile(): java.io.File? = local
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
                <html><head><title>第一章</title><style>
                  .center { text-align: center; }
                  .indent { text-indent: 2em; }
                </style></head><body>
                  <h1>第一章</h1>
                  <p><b>这是</b>&amp;第一段。</p>
                  <p><i>第二段</i>&nbsp;内容。</p>
                  <p align="center">居中</p>
                  <p class="center">CSS居中</p>
                  <p class="indent">缩进段落</p>
                  <p>脚注<a href="#note1">[1]</a></p>
                  <p><a id="note1"></a>注释内容。</p>
                  <p>脚注2<a href="#note2">[2]</a></p>
                  <p id="note2">第二条注释。</p>
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

    /** One XHTML file with a part heading, a chapter heading and a later chapter. */
    fun createSingleFileChapters(): ByteArray {
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
                  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="bookid">urn:test:single</dc:identifier>
                    <dc:title>Single File</dc:title>
                    <dc:language>zh-CN</dc:language>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="style" href="style.css" media-type="text/css"/>
                    <item id="c1" href="book.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="c1"/></spine>
                </package>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
            zip.write(
                """
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                  <body><nav epub:type="toc"><ol>
                    <li><a href="book.xhtml#part">第一部</a>
                      <ul>
                        <li><a href="book.xhtml#ch1">第一章</a></li>
                        <li><a href="book.xhtml#ch2">第二章</a></li>
                      </ul>
                    </li>
                  </ol></nav></body>
                </html>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/style.css"))
            zip.write(
                """
                h1 { text-align: center; }
                h2 { text-align: center; }
                p { text-indent: 2em; }
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/book.xhtml"))
            zip.write(
                """
                <html><head><title>Single File</title><link rel="stylesheet" href="style.css"/></head><body>
                  <h1 id="part">第一部</h1>
                  <h2 id="ch1">第一章</h2>
                  <p>第一章正文。</p>
                  <h2 id="ch2">第二章</h2>
                  <p>第二章正文。</p>
                  <hr/>
                  <p class="note">[1] 注文。</p>
                </body></html>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    /** No NAV/NCX at all: headings are the only chapter structure. */
    fun createNoNavigation(language: String = "zh-CN"): ByteArray {
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
                  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="bookid">urn:test:nonav</dc:identifier>
                    <dc:title>No Navigation</dc:title>
                    <dc:language>$language</dc:language>
                  </metadata>
                  <manifest>
                    <item id="c1" href="book.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="c1"/></spine>
                </package>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/book.xhtml"))
            zip.write(
                """
                <html><body>
                  <h2>第一章</h2><p>第一章正文。</p>
                  <h2>第二章</h2><p>第二章正文。</p>
                </body></html>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    /** Chapter-start blockquote epigraph plus a normal blockquote in the body. */
    fun createEpigraphBook(): ByteArray {
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
                  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="bookid">urn:test:epigraph</dc:identifier>
                    <dc:title>Epigraph</dc:title>
                    <dc:language>zh-CN</dc:language>
                  </metadata>
                  <manifest>
                    <item id="c1" href="book.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="c1"/></spine>
                </package>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/book.xhtml"))
            zip.write(
                """
                <html><body>
                  <h1>第一章</h1>
                  <blockquote><p>章首引语。</p><p>——作者</p></blockquote>
                  <p>正文第一段。<a id="w1"></a><a href="#m1">[1]</a></p>
                  <blockquote><p>正文中的引用。</p></blockquote>
                  <p>正文第二段。</p>
                  <hr/>
                  <p class="note"><a id="m1"></a><a href="#w1">[1]</a> 注文。</p>
                </body></html>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    /** A logical chapter that continues in a second spine file with no TOC entry. */
    fun createContinuationFiles(): ByteArray {
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
                  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="bookid">urn:test:continuation</dc:identifier>
                    <dc:title>Continuation</dc:title>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="c1" href="a.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="b.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="c1"/><itemref idref="c2"/></spine>
                </package>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
            zip.write(
                """
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                  <body><nav epub:type="toc"><ol>
                    <li><a href="a.xhtml#ch1">第一章</a></li>
                  </ol></nav></body>
                </html>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/a.xhtml"))
            zip.write(
                "<html><body><h1 id=\"ch1\">第一章</h1><p>前半。</p></body></html>".toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/b.xhtml"))
            zip.write(
                "<html><body><h2>续章</h2><p>后半。</p></body></html>".toByteArray(),
            )
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
