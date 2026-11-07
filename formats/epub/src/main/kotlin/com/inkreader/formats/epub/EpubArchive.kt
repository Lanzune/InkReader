// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.formats.epub

import com.inkreader.kernel.api.BookReadException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/**
 * In-memory EPUB ZIP reader with path and size limits.
 *
 * M3 only needs random access to a handful of XML/XHTML entries; books are read
 * fully into memory. Streaming/offset storage is a later optimization.
 */
internal class EpubArchive private constructor(
    val entries: Map<String, ByteArray>,
) {
    fun text(path: String): String? {
        val bytes = entries[path] ?: return null
        return decodeText(bytes)
    }

    companion object {
        private const val MAX_ENTRIES = 5_000
        private const val MAX_ENTRY_BYTES = 30 * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 120 * 1024 * 1024

        fun open(bytes: ByteArray): EpubArchive {
            if (bytes.size < 4 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) {
                throw BookReadException("不是有效的 EPUB/ZIP 文件")
            }
            val entries = LinkedHashMap<String, ByteArray>()
            var total = 0L
            try {
                ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                    var count = 0
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        count += 1
                        if (count > MAX_ENTRIES) throw BookReadException("EPUB 条目过多")
                        if (entry.isDirectory) {
                            zip.closeEntry()
                            continue
                        }
                        val name = normalizePath(entry.name)
                            ?: throw BookReadException("EPUB 包含非法路径")
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(16 * 1024)
                        var entryBytes = 0
                        while (true) {
                            val read = zip.read(buffer)
                            if (read <= 0) break
                            entryBytes += read
                            total += read
                            if (entryBytes > MAX_ENTRY_BYTES) {
                                throw BookReadException("EPUB 单条目过大")
                            }
                            if (total > MAX_TOTAL_BYTES) {
                                throw BookReadException("EPUB 解压后过大")
                            }
                            out.write(buffer, 0, read)
                        }
                        entries[name] = out.toByteArray()
                        zip.closeEntry()
                    }
                }
            } catch (failure: BookReadException) {
                throw failure
            } catch (failure: Exception) {
                throw BookReadException("EPUB ZIP 解析失败", failure)
            }
            if (entries.isEmpty()) throw BookReadException("EPUB 为空")
            return EpubArchive(entries)
        }

        fun resolve(baseDir: String, href: String): String? {
            val clean = href.substringBefore('#').substringBefore('?').trim()
            if (clean.isEmpty()) return null
            val decoded = try {
                URLDecoder.decode(clean.replace("+", "%2B"), Charsets.UTF_8.name())
            } catch (_: Exception) {
                clean
            }
            val combined = if (decoded.startsWith('/')) decoded else "$baseDir/$decoded"
            return normalizePath(combined)
        }

        fun baseDir(path: String): String = path.substringBeforeLast('/', "")

        private fun normalizePath(raw: String): String? {
            val parts = raw.replace('\\', '/').split('/')
            val stack = ArrayDeque<String>()
            for (part in parts) {
                when (part) {
                    "", "." -> Unit
                    ".." -> {
                        if (stack.isEmpty()) return null
                        stack.removeLast()
                    }
                    else -> stack.addLast(part)
                }
            }
            return stack.joinToString("/").ifBlank { null }
        }

        private fun decodeText(bytes: ByteArray): String {
            if (bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte()
            ) {
                return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            }
            val head = String(bytes, 0, minOf(bytes.size, 512), Charsets.ISO_8859_1)
            val charsetName = Regex(
                """(?i)<\?xml[^>]*encoding\s*=\s*["']([^"']+)""",
            ).find(head)?.groupValues?.get(1)
                ?: Regex("""(?i)<meta[^>]+charset\s*=\s*["']?([A-Za-z0-9._-]+)""")
                    .find(head)?.groupValues?.get(1)
            val charset = charsetName
                ?.let { runCatching { Charset.forName(it) }.getOrNull() }
                ?: Charsets.UTF_8
            return String(bytes, charset)
        }
    }
}
