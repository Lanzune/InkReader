// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

package com.inkreader.formats.epub

import com.inkreader.kernel.api.BookDocument
import com.inkreader.kernel.api.BookMetadata
import com.inkreader.kernel.api.BookReadException
import com.inkreader.kernel.api.BookSource
import com.inkreader.kernel.api.Chapter
import com.inkreader.kernel.api.ChapterImage
import com.inkreader.kernel.api.ChapterLink
import com.inkreader.kernel.api.ChapterMark
import com.inkreader.kernel.api.ChapterStyle
import com.inkreader.kernel.api.FormatMatch
import com.inkreader.kernel.api.FormatProvider
import com.inkreader.kernel.api.LayoutKind
import com.inkreader.kernel.api.OpenContext
import com.inkreader.kernel.api.TocEntry
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile

/**
 * EPUB 2/3 text provider.
 *
 * M3 baseline: OPF spine + NAV/NCX, XHTML converted to paragraph text. Images,
 * CSS typography and fixed layout are deliberately out of scope for this pass.
 */
class EpubFormatProvider : FormatProvider {
    override val id: String = "epub"

    override fun probe(source: BookSource): FormatMatch? {
        val name = source.displayName.lowercase()
        val mime = source.mimeType?.lowercase().orEmpty()
        return when {
            name.endsWith(".epub") -> FormatMatch(id, 96)
            mime == "application/epub+zip" -> FormatMatch(id, 95)
            else -> null
        }
    }

    override fun open(source: BookSource, context: OpenContext): BookDocument {
        val archive = EpubArchive.open(source.readAll())
        if (archive.entries.containsKey("META-INF/rights.xml")) {
            throw BookReadException("暂不支持带 DRM 的 EPUB")
        }
        val packageInfo = readPackage(archive)
        val navigation = readNavigation(archive, packageInfo)
        return buildDocument(archive, packageInfo, navigation, source.displayName)
    }

    /**
     * Random-access preview for a local EPUB. It reads only metadata, navigation
     * and the first few spine documents, so the reader can paint the first page
     * before the full book is parsed.
     */
    fun openPreview(
        source: BookSource,
        context: OpenContext,
        maxChapters: Int = 8,
        startChapter: Int = 0,
        knownMarks: List<ChapterMark> = emptyList(),
    ): BookDocument? {
        val file = source.localFile() ?: return null
        if (!file.isFile) return null
        ZipFile(file).use { zip ->
            val containerBytes = readZipEntry(zip, "META-INF/container.xml") ?: return null
            val opfPath = EpubXml.run {
                val document = parse(containerBytes)
                val rootfile = first(document.documentElement, "rootfile")
                attribute(rootfile ?: return@run null, "full-path")
                    ?.let { EpubArchive.resolve("", it) }
            } ?: return null
            val opfBytes = readZipEntry(zip, opfPath) ?: return null
            val baseEntries = linkedMapOf(
                "META-INF/container.xml" to containerBytes,
                opfPath to opfBytes,
            )
            val packageInfo = readPackage(EpubArchive.fromEntries(baseEntries))
            val navItem = packageInfo.manifest.values.firstOrNull {
                "nav" in it.properties && it.path.endsWith(".xhtml", ignoreCase = true)
            }
            val ncxItem = packageInfo.tocId?.let { packageInfo.manifest[it] }
                ?: packageInfo.manifest.values.firstOrNull {
                    it.mediaType.equals("application/x-dtbncx+xml", ignoreCase = true)
                }
            listOfNotNull(navItem, ncxItem).forEach { item ->
                readZipEntry(zip, item.path)?.let { baseEntries[item.path] = it }
            }
            val navigation = readNavigation(EpubArchive.fromEntries(baseEntries), packageInfo)

            // When an index is available startChapter addresses the index (which
            // only contains non-empty chapters); map it back to the spine so a
            // blank cover/front-matter entry cannot shift the window by one.
            val startMark = knownMarks.getOrNull(startChapter)
            val first = if (startMark != null) {
                packageInfo.spine.indexOfFirst { item -> item.path == startMark.path }
                    .takeIf { index -> index >= 0 }
                    ?: 0
            } else {
                startChapter.coerceIn(0, packageInfo.spine.lastIndex)
            }
            val selected = packageInfo.spine.drop(first).take(maxChapters.coerceAtLeast(1))
            val baseOffset = startMark
                ?.takeIf { mark -> mark.path == selected.firstOrNull()?.path }
                ?.charOffset
                ?: 0
            val chapterCache = LinkedHashMap<String, CachedChapter>()
            for (item in selected) {
                val bytes = readZipEntry(zip, item.path) ?: continue
                baseEntries[item.path] = bytes
                val html = EpubArchive.fromEntries(baseEntries).text(item.path) ?: continue
                val rich = XhtmlText.extractRich(html)
                chapterCache[item.path] = CachedChapter(html, rich)
                val baseDir = EpubArchive.baseDir(item.path)
                for (image in rich.images) {
                    val path = EpubArchive.resolve(baseDir, image.src) ?: continue
                    readZipEntry(zip, path)?.let { baseEntries[path] = it }
                }
            }
            if (selected.isEmpty()) return null
            val previewArchive = EpubArchive.fromEntries(baseEntries)
            return buildDocument(
                previewArchive,
                packageInfo.copy(spine = selected),
                navigation,
                source.displayName,
                baseOffset = baseOffset,
                chapterCache = chapterCache,
            )
        }
    }

    private fun readZipEntry(zip: ZipFile, path: String): ByteArray? {
        val entry = zip.getEntry(path) ?: return null
        if (entry.size > 30L * 1024 * 1024) return null
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    private fun readPackage(archive: EpubArchive): PackageInfo {
        val containerBytes = archive.entries["META-INF/container.xml"]
        val opfPath = if (containerBytes != null) {
            val document = EpubXml.parse(containerBytes)
            val rootfile = EpubXml.first(document.documentElement, "rootfile")
            val fullPath = rootfile?.let { EpubXml.attribute(it, "full-path") }
            fullPath?.let { EpubArchive.resolve("", it) }
        } else {
            null
        } ?: archive.entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
            ?: throw BookReadException("EPUB 缺少 OPF 文件")

        val opfBytes = archive.entries[opfPath]
            ?: throw BookReadException("EPUB OPF 文件不存在")
        val document = EpubXml.parse(opfBytes)
        val root = document.documentElement
        val opfDir = EpubArchive.baseDir(opfPath)

        val metadataElement = EpubXml.first(root, "metadata")
        val title = EpubXml.text(EpubXml.first(metadataElement, "title"))
        val author = EpubXml.text(EpubXml.first(metadataElement, "creator"))
            .takeIf { it.isNotBlank() }
        val language = EpubXml.text(EpubXml.first(metadataElement, "language"))
            .takeIf { it.isNotBlank() }

        val manifestElement = EpubXml.first(root, "manifest")
            ?: throw BookReadException("EPUB 缺少 manifest")
        val manifest = LinkedHashMap<String, ManifestItem>()
        for (element in EpubXml.elements(manifestElement, "item")) {
            val id = EpubXml.attribute(element, "id") ?: continue
            val href = EpubXml.attribute(element, "href") ?: continue
            val path = EpubArchive.resolve(opfDir, href) ?: continue
            manifest[id] = ManifestItem(
                id = id,
                path = path,
                mediaType = EpubXml.attribute(element, "media-type").orEmpty(),
                properties = EpubXml.attribute(element, "properties").orEmpty()
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .toSet(),
            )
        }
        if (manifest.isEmpty()) throw BookReadException("EPUB manifest 为空")

        val spineElement = EpubXml.first(root, "spine")
            ?: throw BookReadException("EPUB 缺少 spine")
        val spine = EpubXml.elements(spineElement, "itemref")
            .filter { EpubXml.attribute(it, "linear")?.lowercase() != "no" }
            .mapNotNull { itemRef ->
                val idref = EpubXml.attribute(itemRef, "idref") ?: return@mapNotNull null
                manifest[idref]
            }
            .filter { item ->
                item.mediaType.isBlank() ||
                    item.mediaType.equals("application/xhtml+xml", ignoreCase = true) ||
                    item.mediaType.equals("text/html", ignoreCase = true)
            }
        if (spine.isEmpty()) throw BookReadException("EPUB spine 没有可读章节")

        return PackageInfo(
            opfPath = opfPath,
            metadata = BookMetadata(
                title = title,
                author = author,
                language = language,
            ),
            manifest = manifest,
            spine = spine,
            tocId = EpubXml.attribute(spineElement, "toc"),
        )
    }

    private fun readNavigation(archive: EpubArchive, packageInfo: PackageInfo): List<NavLink> {
        val navItem = packageInfo.manifest.values.firstOrNull {
            "nav" in it.properties && it.path.endsWith(".xhtml", ignoreCase = true)
        }
        if (navItem != null) {
            val html = archive.text(navItem.path).orEmpty()
            val links = parseNavLinks(html, EpubArchive.baseDir(navItem.path))
            if (links.isNotEmpty()) return links
        }

        val ncxItem = packageInfo.tocId?.let { packageInfo.manifest[it] }
            ?: packageInfo.manifest.values.firstOrNull {
                it.mediaType.equals("application/x-dtbncx+xml", ignoreCase = true)
            }
            ?: return emptyList()
        val bytes = archive.entries[ncxItem.path] ?: return emptyList()
        return runCatching {
            parseNcx(EpubXml.parse(bytes), EpubArchive.baseDir(ncxItem.path))
        }.getOrDefault(emptyList())
    }

    private fun parseNavLinks(html: String, baseDir: String): List<NavLink> {
        val navBlock = Regex(
            """(?is)<nav\b[^>]*epub:type\s*=\s*["'][^"']*\btoc\b[^"']*["'][^>]*>(.*?)</nav\s*>""",
        ).find(html)?.groupValues?.get(1) ?: html
        val anchorPattern = Regex(
            """(?is)<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a\s*>""",
        )
        return anchorPattern.findAll(navBlock).mapNotNull { match ->
            val path = EpubArchive.resolve(baseDir, match.groupValues[1]) ?: return@mapNotNull null
            val title = XhtmlText.stripTags(match.groupValues[2])
                .replace(Regex("\\s+"), " ")
                .trim()
            if (title.isBlank()) null else NavLink(title = title, path = path)
        }.distinctBy { "${it.path}\u0000${it.title}" }.take(1_000).toList()
    }

    private fun parseNcx(document: org.w3c.dom.Document, baseDir: String): List<NavLink> {
        val links = mutableListOf<NavLink>()
        for (navPoint in EpubXml.elements(document.documentElement, "navPoint")) {
            val label = EpubXml.first(navPoint, "navLabel")
                ?.let { EpubXml.first(it, "text") }
                ?.let(EpubXml::text)
                ?.trim()
                .orEmpty()
            val source = EpubXml.first(navPoint, "content")
                ?.let { EpubXml.attribute(it, "src") }
                ?: continue
            val path = EpubArchive.resolve(baseDir, source) ?: continue
            if (label.isNotBlank()) links.add(NavLink(label, path))
            if (links.size >= 1_000) break
        }
        return links.distinctBy { "${it.path}\u0000${it.title}" }
    }

    private fun buildDocument(
        archive: EpubArchive,
        packageInfo: PackageInfo,
        navigation: List<NavLink>,
        displayName: String,
        baseOffset: Int = 0,
        chapterCache: Map<String, CachedChapter> = emptyMap(),
    ): BookDocument {
        val navigationByPath = navigation.groupBy { it.path }
        val text = StringBuilder()
        val toc = mutableListOf<TocEntry>()
        val chapterImages = mutableListOf<ChapterImage>()
        val chapterStyles = mutableListOf<ChapterStyle>()
        val chapterMarks = mutableListOf<ChapterMark>()
        val resources = LinkedHashMap<String, ByteArray>()
        val anchors = LinkedHashMap<String, Int>()
        val chapterStarts = LinkedHashMap<String, Int>()
        val pendingLinks = mutableListOf<PendingLink>()
        var hasContent = false
        var chapterNumber = 0

        val parsedChapters = packageInfo.spine.parallelStream()
            .map { item ->
                val cached = chapterCache[item.path]
                val html = cached?.html ?: archive.text(item.path) ?: return@map null
                val rich = cached?.rich ?: XhtmlText.extractRich(html)
                if (rich.text.isBlank()) null else ParsedChapter(item, html, rich)
            }
            .filter { it != null }
            .collect(java.util.stream.Collectors.toList())
            .filterNotNull()

        for (parsed in parsedChapters) {
            val item = parsed.item
            val html = parsed.html
            val rich = parsed.rich
            val chapterText = rich.text

            if (hasContent) text.append("\n\n")
            val localOffset = text.length
            val offset = baseOffset + localOffset
            text.append(chapterText)
            hasContent = true
            chapterNumber += 1
            chapterStarts[item.path] = offset
            chapterMarks.add(ChapterMark(item.path, offset, chapterText.length))

            val baseDir = EpubArchive.baseDir(item.path)
            for (anchor in rich.anchors) {
                anchors["${item.path}#${anchor.id}"] = offset + anchor.charOffset
            }
            for (link in rich.links) {
                pendingLinks.add(
                    PendingLink(
                        start = localOffset + link.start,
                        endExclusive = localOffset + link.endExclusive,
                        target = link.target,
                        chapterPath = item.path,
                        baseDir = baseDir,
                    ),
                )
            }
            for (image in rich.images) {
                val path = EpubArchive.resolve(baseDir, image.src) ?: continue
                val bytes = archive.entries[path] ?: continue
                if (bytes.isEmpty()) continue
                resources.putIfAbsent(path, bytes)
                chapterImages.add(
                    ChapterImage(
                        charOffset = localOffset + image.charOffset,
                        resource = path,
                        alt = image.alt,
                    ),
                )
            }
            for (style in rich.styles) {
                chapterStyles.add(
                    ChapterStyle(
                        start = localOffset + style.start,
                        endExclusive = localOffset + style.endExclusive,
                        kind = style.kind,
                    ),
                )
            }

            val links = navigationByPath[item.path].orEmpty()
            if (links.isNotEmpty()) {
                links.forEach { link -> toc.add(TocEntry(link.title, offset)) }
            } else {
                val title = XhtmlText.title(html) ?: "第${chapterNumber}章"
                toc.add(TocEntry(title, offset))
            }
        }

        if (!hasContent) throw BookReadException("EPUB 没有可显示的正文")
        val fullText = text.toString()
        if (toc.isEmpty()) toc.add(TocEntry("正文", 0))

        val links = pendingLinks.mapNotNull { pending ->
            if (pending.target.startsWith("http", ignoreCase = true) ||
                pending.target.startsWith("mailto:", ignoreCase = true)
            ) {
                return@mapNotNull null
            }
            val pathPart = pending.target.substringBefore('#').trim()
            val fragment = pending.target.substringAfter('#', "").trim()
            val targetPath = if (pathPart.isBlank()) {
                pending.chapterPath
            } else {
                EpubArchive.resolve(pending.baseDir, pathPart) ?: return@mapNotNull null
            }
            val targetOffset = if (fragment.isBlank()) {
                chapterStarts[targetPath]
            } else {
                anchors["$targetPath#$fragment"]
            } ?: return@mapNotNull null
            ChapterLink(
                start = pending.start,
                endExclusive = pending.endExclusive,
                targetOffset = targetOffset,
            )
        }

        val metadata = packageInfo.metadata.copy(
            title = packageInfo.metadata.title.ifBlank {
                displayName.substringBeforeLast('.').ifBlank { "未命名书籍" }
            },
        )
        return BookDocument(
            metadata = metadata,
            formatId = id,
            layout = LayoutKind.REFLOWABLE,
            chapters = listOf(
                Chapter(
                    id = "epub-0",
                    title = metadata.title,
                    text = fullText,
                    images = chapterImages,
                    styles = chapterStyles,
                    links = links,
                    baseOffset = baseOffset,
                ),
            ),
            toc = toc,
            resources = resources,
            chapterMarks = chapterMarks,
        )
    }

    private data class ManifestItem(
        val id: String,
        val path: String,
        val mediaType: String,
        val properties: Set<String>,
    )

    private data class PackageInfo(
        val opfPath: String,
        val metadata: BookMetadata,
        val manifest: Map<String, ManifestItem>,
        val spine: List<ManifestItem>,
        val tocId: String?,
    )

    private data class NavLink(
        val title: String,
        val path: String,
    )

    private data class PendingLink(
        val start: Int,
        val endExclusive: Int,
        val target: String,
        val chapterPath: String,
        val baseDir: String,
    )

    private data class ParsedChapter(
        val item: ManifestItem,
        val html: String,
        val rich: XhtmlText.RichText,
    )

    private data class CachedChapter(
        val html: String,
        val rich: XhtmlText.RichText,
    )
}
