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
import com.inkreader.kernel.api.InlineStyleKind
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
        baseOffsetOverride: Int? = null,
        maxImageBytes: Int = 32 * 1024 * 1024,
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
            val baseOffset = baseOffsetOverride ?: startMark
                ?.takeIf { mark -> mark.path == selected.firstOrNull()?.path }
                ?.charOffset
                ?: 0
            val chapterCache = LinkedHashMap<String, CachedChapter>()
            var imageBudget = maxImageBytes.coerceAtLeast(0)
            val readCss: (String) -> String? = { path ->
                readZipEntry(zip, path)?.let { bytes ->
                    EpubArchive.fromEntries(mapOf(path to bytes)).text(path)
                }
            }
            for (item in selected) {
                val bytes = readZipEntry(zip, item.path) ?: continue
                baseEntries[item.path] = bytes
                val html = EpubArchive.fromEntries(baseEntries).text(item.path) ?: continue
                val baseDir = EpubArchive.baseDir(item.path)
                val rich = XhtmlText.extractRich(
                    withLinkedStyles(html, baseDir, readCss),
                    compactParagraphs = packageInfo.metadata.language
                        ?.startsWith("zh", ignoreCase = true) == true,
                )
                chapterCache[item.path] = CachedChapter(html, rich)
                for (image in rich.images) {
                    if (imageBudget <= 0) break
                    val path = EpubArchive.resolve(baseDir, image.src) ?: continue
                    val imageBytes = readZipEntry(zip, path) ?: continue
                    if (imageBytes.size > imageBudget) continue
                    baseEntries[path] = imageBytes
                    imageBudget -= imageBytes.size
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

    /**
     * Streams every spine XHTML just long enough to count characters. Images,
     * styles and the full text are never retained; used by the percentage
     * progress path for books too large for a full document.
     */
    fun estimateTextLength(source: BookSource): Int? {
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
            val packageInfo = readPackage(
                EpubArchive.fromEntries(
                    linkedMapOf(
                        "META-INF/container.xml" to containerBytes,
                        opfPath to opfBytes,
                    ),
                ),
            )
            var total = 0
            var hasContent = false
            for (item in packageInfo.spine) {
                val bytes = readZipEntry(zip, item.path) ?: continue
                val html = EpubArchive.fromEntries(mapOf(item.path to bytes)).text(item.path)
                    ?: continue
                val text = XhtmlText.extract(
                    html,
                    compactParagraphs = packageInfo.metadata.language
                        ?.startsWith("zh", ignoreCase = true) == true,
                )
                if (text.isBlank()) continue
                if (hasContent) total += 2
                total += text.length
                hasContent = true
            }
            return total.takeIf { hasContent }
        }
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
        val tokenPattern = Regex(
            """(?is)<[ou]l\b[^>]*>|</[ou]l\s*>|<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a\s*>""",
        )
        val links = mutableListOf<NavLink>()
        var listDepth = 0
        for (match in tokenPattern.findAll(navBlock)) {
            val token = match.value
            when {
                token.startsWith("<ol", ignoreCase = true) ||
                    token.startsWith("<ul", ignoreCase = true) -> listDepth += 1
                token.startsWith("</ol", ignoreCase = true) ||
                    token.startsWith("</ul", ignoreCase = true) -> {
                    if (listDepth > 0) listDepth -= 1
                }
                else -> {
                    val href = match.groupValues[1]
                    val path = EpubArchive.resolve(baseDir, href) ?: continue
                    val fragment = href.substringAfter('#', "").trim()
                    val title = XhtmlText.stripTags(match.groupValues[2])
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    if (title.isNotBlank()) {
                        links.add(
                            NavLink(
                                title = title,
                                path = path,
                                fragment = fragment,
                                depth = (listDepth - 1).coerceAtLeast(0),
                            ),
                        )
                    }
                }
            }
            if (links.size >= 1_000) break
        }
        return links.distinctBy { "${it.path}\u0000${it.fragment}\u0000${it.title}" }
    }

    private fun parseNcx(document: org.w3c.dom.Document, baseDir: String): List<NavLink> {
        val navMap = EpubXml.first(document.documentElement, "navMap") ?: return emptyList()
        val links = mutableListOf<NavLink>()

        fun visit(parent: org.w3c.dom.Element, depth: Int) {
            if (links.size >= 1_000) return
            var child = parent.firstChild
            while (child != null && links.size < 1_000) {
                if (child is org.w3c.dom.Element) {
                    val name = child.localName ?: child.nodeName.substringAfter(':')
                    if (name == "navPoint") {
                        val label = EpubXml.first(child, "navLabel")
                            ?.let { EpubXml.first(it, "text") }
                            ?.let(EpubXml::text)
                            ?.trim()
                            .orEmpty()
                        val source = EpubXml.first(child, "content")
                            ?.let { EpubXml.attribute(it, "src") }
                        if (source != null) {
                            val path = EpubArchive.resolve(baseDir, source)
                            val fragment = source.substringAfter('#', "").trim()
                            if (path != null && label.isNotBlank()) {
                                links.add(NavLink(label, path, fragment, depth.coerceAtMost(8)))
                            }
                        }
                        visit(child, depth + 1)
                    }
                }
                child = child.nextSibling
            }
        }

        visit(navMap, 0)
        return links.distinctBy { "${it.path}\u0000${it.fragment}\u0000${it.title}" }
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
        val pageBreaks = mutableListOf<Int>()
        val headingRanges = mutableListOf<IntRange>()
        val resources = LinkedHashMap<String, ByteArray>()
        val anchors = LinkedHashMap<String, Int>()
        val chapterStarts = LinkedHashMap<String, Int>()
        val pendingLinks = mutableListOf<PendingLink>()
        var hasContent = false
        var chapterNumber = 0

        val spineIndexByPath = packageInfo.spine
            .mapIndexed { index, item -> item.path to index }
            .toMap()
        val parsedChapters = packageInfo.spine.parallelStream()
            .map { item ->
                val cached = chapterCache[item.path]
                val html = cached?.html ?: archive.text(item.path) ?: return@map null
                val rich = cached?.rich ?: run {
                    val baseDir = EpubArchive.baseDir(item.path)
                    XhtmlText.extractRich(
                        withLinkedStyles(html, baseDir) { path -> archive.text(path) },
                        compactParagraphs = packageInfo.metadata.language
                            ?.startsWith("zh", ignoreCase = true) == true,
                    )
                }
                if (rich.text.isBlank()) {
                    null
                } else {
                    ParsedChapter(
                        item = item,
                        html = html,
                        rich = rich,
                        spineIndex = spineIndexByPath[item.path] ?: 0,
                    )
                }
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
            chapterMarks.add(ChapterMark(item.path, offset, chapterText.length, parsed.spineIndex))
            chapterBreakOffsets(
                rich = rich,
                navLinks = navigationByPath[item.path].orEmpty(),
                hasNavigation = navigation.isNotEmpty(),
            ).forEach { breakOffset ->
                pageBreaks.add(localOffset + breakOffset)
            }
            rich.headings.forEach { heading ->
                headingRanges.add(
                    (localOffset + heading.start) until (localOffset + heading.endExclusive),
                )
            }

            val baseDir = EpubArchive.baseDir(item.path)
            for (anchor in rich.anchors) {
                anchors["${item.path}#${anchor.id}"] = offset + anchor.charOffset
            }
            for (heading in rich.headings) {
                heading.id?.let { id ->
                    anchors["${item.path}#$id"] = offset + heading.start
                }
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
                val bytes = archive.entries[path]
                if (bytes != null && bytes.isNotEmpty()) {
                    resources.putIfAbsent(path, bytes)
                }
                chapterImages.add(
                    ChapterImage(
                        charOffset = localOffset + image.charOffset,
                        resource = path,
                        alt = image.alt,
                    ),
                )
            }
            val preparedStyles = prepareStyles(rich, packageInfo.metadata.language)
            for (style in preparedStyles) {
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
                links.forEach { link ->
                    val localTarget = if (link.fragment.isBlank()) {
                        0
                    } else {
                        rich.headings.firstOrNull { it.id == link.fragment }
                            ?.takeIf { it.level <= 2 }
                            ?.start
                            ?: rich.anchors.firstOrNull { it.id == link.fragment }
                                ?.charOffset
                                ?.let { anchor -> headingStartForAnchor(rich, anchor) }
                            ?: 0
                    }
                    val candidate = offset + localTarget
                    val precedingBreak = pageBreaks.lastOrNull { it in offset..candidate }
                    val target = if (precedingBreak != null &&
                        onlyHeadingText(rich, precedingBreak - offset, localTarget)
                    ) {
                        precedingBreak
                    } else {
                        candidate
                    }
                    toc.add(TocEntry(link.title, target, link.depth))
                }
            } else {
                val title = XhtmlText.title(html) ?: "第${chapterNumber}章"
                toc.add(TocEntry(title, offset))
            }
        }

        if (!hasContent) throw BookReadException("EPUB 没有可显示的正文")
        val fullText = text.toString()
        if (navigation.isNotEmpty()) {
            toc.clear()
            navigation.forEach { link ->
                val key = if (link.fragment.isBlank()) {
                    link.path
                } else {
                    "${link.path}#${link.fragment}"
                }
                var target = if (link.fragment.isBlank()) {
                    chapterStarts[link.path]
                } else {
                    anchors[key]
                } ?: -1
                if (target > 0) {
                    val breakOffset = pageBreaks.lastOrNull { it in 0..target }
                    if (breakOffset != null && breakOffset < target &&
                        onlyHeadingText(fullText, headingRanges, breakOffset, target)
                    ) {
                        target = breakOffset
                    }
                }
                toc.add(TocEntry(link.title, target, link.depth, key))
            }
        }
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
            } ?: -1
            val targetKey = if (fragment.isBlank()) targetPath else "$targetPath#$fragment"
            ChapterLink(
                start = pending.start,
                endExclusive = pending.endExclusive,
                targetOffset = targetOffset,
                targetKey = targetKey,
            )
        }

        val metadata = packageInfo.metadata.copy(
            title = packageInfo.metadata.title.ifBlank {
                displayName.substringBeforeLast('.').ifBlank { "未命名书籍" }
            },
        )
        val internalTargets = LinkedHashMap<String, Int>()
        internalTargets.putAll(chapterStarts)
        internalTargets.putAll(anchors)
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
                    pageBreaks = pageBreaks.distinct().sorted(),
                ),
            ),
            toc = toc,
            resources = resources,
            chapterMarks = chapterMarks,
            internalTargets = internalTargets,
        )
    }

    private val linkTagPattern = Regex("(?is)<link\\b[^>]*>")
    private val relPattern = Regex("""(?i)\brel\s*=\s*["']([^"']*)["']""")
    private val linkHrefPattern = Regex("""(?i)\bhref\s*=\s*["']([^"']*)["']""")

    private data class StyleRange(
        val start: Int,
        val endExclusive: Int,
        val kind: InlineStyleKind,
    )

    private fun prepareStyles(
        rich: XhtmlText.RichText,
        language: String?,
    ): List<XhtmlText.RichStyle> {
        val styles = rich.styles.toMutableList()
        val epigraph = mutableListOf<StyleRange>()
        for (heading in rich.headings.filter { it.level <= 2 }) {
            var paragraphIndex = rich.paragraphs.indexOfFirst { it.start >= heading.endExclusive }
            if (paragraphIndex < 0) continue
            if (!rich.text.substring(heading.endExclusive, rich.paragraphs[paragraphIndex].start)
                    .isBlank()
            ) {
                continue
            }
            val group = mutableListOf<StyleRange>()
            var sawQuote = false
            while (paragraphIndex < rich.paragraphs.size) {
                val paragraph = rich.paragraphs[paragraphIndex]
                if (paragraph.start < heading.endExclusive) {
                    paragraphIndex += 1
                    continue
                }
                val quoteText = rich.text.substring(paragraph.start, paragraph.endExclusive).trim()
                val classes = paragraph.classes.toSet()
                val sourceLike = paragraph.align == "right" ||
                    quoteText.startsWith("—") ||
                    quoteText.startsWith("–") ||
                    classes.any { it in setOf("source", "cite", "author", "attribution", "lk") }
                val quoteLike = paragraph.tag == "blockquote" ||
                    paragraph.leftMarginEm >= 0.8f ||
                    paragraph.rightMarginEm >= 0.8f ||
                    paragraph.fontScale < 0.95f ||
                    classes.any {
                        it in setOf("epigraph", "quote", "blockquote", "block", "kaiti")
                    }
                if (!sourceLike && !quoteLike) break
                val kind = if (sourceLike) InlineStyleKind.EPIGRAPH_SOURCE else InlineStyleKind.EPIGRAPH
                if (kind == InlineStyleKind.EPIGRAPH) sawQuote = true
                group.add(StyleRange(paragraph.start, paragraph.endExclusive, kind))
                paragraphIndex += 1
            }
            if (sawQuote) epigraph.addAll(group)
        }

        val uniqueEpigraph = epigraph.distinctBy { Triple(it.start, it.endExclusive, it.kind) }
        val epigraphRanges = uniqueEpigraph.map { it.start until it.endExclusive }
        styles.removeAll { style ->
            (style.kind == InlineStyleKind.FIRST_LINE_INDENT || style.kind == InlineStyleKind.INDENT) &&
                epigraphRanges.any { range ->
                    style.start < range.last + 1 && style.endExclusive > range.first
                }
        }
        uniqueEpigraph.forEach { range ->
            styles.add(XhtmlText.RichStyle(range.start, range.endExclusive, range.kind))
        }

        val blockquoteRanges = rich.paragraphs
            .filter { it.tag == "blockquote" }
            .map { it.start until it.endExclusive }
        if (blockquoteRanges.isNotEmpty()) {
            styles.removeAll { style ->
                style.kind == InlineStyleKind.FIRST_LINE_INDENT &&
                    blockquoteRanges.any { range ->
                        style.start < range.last + 1 && style.endExclusive > range.first
                    }
            }
        }

        val noteParagraphs = rich.paragraphs.filter { paragraph ->
            paragraph.classes.any { name ->
                name == "note" || name == "footnote" || name == "endnote" ||
                    name == "footnotes" || name == "endnotes"
            }
        }
        if (noteParagraphs.isNotEmpty()) {
            noteParagraphs.forEach { paragraph ->
                styles.add(
                    XhtmlText.RichStyle(
                        paragraph.start,
                        paragraph.endExclusive,
                        InlineStyleKind.NOTE,
                    ),
                )
            }
            val firstNote = noteParagraphs.first()
            styles.add(
                XhtmlText.RichStyle(
                    firstNote.start,
                    firstNote.endExclusive,
                    InlineStyleKind.NOTE_SEPARATOR,
                ),
            )
            styles.removeAll { style ->
                style.kind == InlineStyleKind.ALIGN_CENTER &&
                    noteParagraphs.any { note ->
                        style.start < note.endExclusive && style.endExclusive > note.start
                    }
            }
            styles.removeAll { style ->
                style.kind == InlineStyleKind.FIRST_LINE_INDENT &&
                    noteParagraphs.any { note ->
                        style.start < note.endExclusive && style.endExclusive > note.start
                    }
            }
        }

        if (language?.startsWith("zh", ignoreCase = true) == true) {
            for (paragraph in rich.paragraphs) {
                if (paragraph.tag != "p") continue
                if (paragraph.indentSpecified) continue
                if (paragraph.align != null && paragraph.align != "left") continue
                if (paragraph.leftMarginEm > 0.1f || paragraph.rightMarginEm > 0.1f) continue
                if (paragraph.fontScale < 0.98f) continue
                if (paragraph.classes.any {
                        it in setOf(
                            "center", "right", "note", "img", "image", "caption",
                            "quote", "epigraph", "poem", "list", "title", "author",
                        )
                    }
                ) {
                    continue
                }
                if (epigraphRanges.any { paragraph.start < it.last + 1 && paragraph.endExclusive > it.first }) {
                    continue
                }
                styles.add(
                    XhtmlText.RichStyle(
                        paragraph.start,
                        paragraph.endExclusive,
                        InlineStyleKind.FIRST_LINE_INDENT,
                    ),
                )
            }
        }
        for (heading in rich.headings.filter { it.level <= 2 }) {
            val aligned = styles.any { style ->
                style.kind in setOf(
                    InlineStyleKind.ALIGN_LEFT,
                    InlineStyleKind.ALIGN_CENTER,
                    InlineStyleKind.ALIGN_RIGHT,
                ) && style.start < heading.endExclusive && style.endExclusive > heading.start
            }
            if (!aligned) {
                styles.add(
                    XhtmlText.RichStyle(
                        heading.start,
                        heading.endExclusive,
                        InlineStyleKind.ALIGN_CENTER,
                    ),
                )
            }
        }
        return styles.sortedWith(compareBy({ it.start }, { it.endExclusive }))
    }

    private fun withLinkedStyles(
        html: String,
        baseDir: String,
        readCss: (String) -> String?,
    ): String {
        val result = StringBuilder(html)
        for (tag in linkTagPattern.findAll(html)) {
            val rel = relPattern.find(tag.value)?.groupValues?.get(1).orEmpty()
            if (!rel.split(Regex("\\s+")).any { it.equals("stylesheet", ignoreCase = true) }) {
                continue
            }
            val href = linkHrefPattern.find(tag.value)?.groupValues?.get(1) ?: continue
            val path = EpubArchive.resolve(baseDir, href) ?: continue
            val css = readCss(path) ?: continue
            result.append("\n<style>\n").append(css).append("\n</style>")
        }
        return result.toString()
    }

    private fun chapterBreakOffsets(
        rich: XhtmlText.RichText,
        navLinks: List<NavLink>,
        hasNavigation: Boolean,
    ): List<Int> {
        val headings = rich.headings.filter { it.level <= 2 }
        val candidates = mutableListOf<Int>()
        if (navLinks.isNotEmpty()) {
            for (link in navLinks) {
                if (link.fragment.isNotBlank()) {
                    val heading = rich.headings.firstOrNull { it.id == link.fragment }
                        ?.takeIf { it.level <= 2 }
                        ?: rich.anchors.firstOrNull { it.id == link.fragment }
                            ?.charOffset
                            ?.let { anchor -> headingStartForAnchor(rich, anchor) }
                            ?.let { start -> headings.firstOrNull { it.start == start } }
                        ?: continue
                    candidates.add(heading.start)
                } else {
                    val first = headings.firstOrNull()
                    candidates.add(first?.start ?: 0)
                }
            }
        } else if (!hasNavigation) {
            candidates.addAll(headings.map { it.start })
        } else {
            // A file with no TOC entry may be a continuation of the previous
            // chapter. Only the top-level heading is safe to break at.
            headings.firstOrNull { it.level == 1 }?.let { candidates.add(it.start) }
        }

        val result = mutableListOf<Int>()
        for (offset in candidates.distinct().sorted()) {
            if (offset !in 0 until rich.text.length) continue
            if (result.isEmpty()) {
                result.add(offset)
                continue
            }
            if (onlyHeadingText(rich, result.last(), offset)) continue
            result.add(offset)
        }
        return result
    }

    private fun headingStartForAnchor(rich: XhtmlText.RichText, anchor: Int): Int? {
        rich.headings.firstOrNull { anchor in it.start until it.endExclusive }?.let {
            return it.start
        }
        return rich.headings.firstOrNull { heading ->
            heading.start >= anchor &&
                rich.text.substring(anchor, heading.start).isBlank()
        }?.start
    }

    private fun onlyHeadingText(
        text: String,
        headings: List<IntRange>,
        from: Int,
        to: Int,
    ): Boolean {
        if (from >= to) return true
        for (index in from until to) {
            if (text[index].isWhitespace()) continue
            if (headings.none { index in it }) return false
        }
        return true
    }

    /** Part title immediately followed by chapter title belongs to one start page. */
    private fun onlyHeadingText(
        rich: XhtmlText.RichText,
        from: Int,
        to: Int,
    ): Boolean {
        if (from >= to) return true
        for (index in from until to) {
            if (rich.text[index].isWhitespace()) continue
            if (rich.headings.none { index in it.start until it.endExclusive }) return false
        }
        return true
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
        val fragment: String = "",
        val depth: Int = 0,
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
        val spineIndex: Int,
    )

    private data class CachedChapter(
        val html: String,
        val rich: XhtmlText.RichText,
    )
}
