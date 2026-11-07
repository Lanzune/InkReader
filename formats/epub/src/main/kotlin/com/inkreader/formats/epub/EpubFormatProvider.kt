package com.inkreader.formats.epub

import com.inkreader.kernel.api.BookDocument
import com.inkreader.kernel.api.BookMetadata
import com.inkreader.kernel.api.BookReadException
import com.inkreader.kernel.api.BookSource
import com.inkreader.kernel.api.Chapter
import com.inkreader.kernel.api.ChapterImage
import com.inkreader.kernel.api.FormatMatch
import com.inkreader.kernel.api.FormatProvider
import com.inkreader.kernel.api.LayoutKind
import com.inkreader.kernel.api.OpenContext
import com.inkreader.kernel.api.TocEntry
import org.w3c.dom.Element

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
    ): BookDocument {
        val navigationByPath = navigation.groupBy { it.path }
        val text = StringBuilder()
        val toc = mutableListOf<TocEntry>()
        val chapterImages = mutableListOf<ChapterImage>()
        val resources = LinkedHashMap<String, ByteArray>()
        var hasContent = false
        var chapterNumber = 0

        for (item in packageInfo.spine) {
            val html = archive.text(item.path) ?: continue
            val rich = XhtmlText.extractRich(html)
            val chapterText = rich.text
            if (chapterText.isBlank()) continue

            if (hasContent) text.append("\n\n")
            val offset = text.length
            text.append(chapterText)
            hasContent = true
            chapterNumber += 1

            val baseDir = EpubArchive.baseDir(item.path)
            for (image in rich.images) {
                val path = EpubArchive.resolve(baseDir, image.src) ?: continue
                val bytes = archive.entries[path] ?: continue
                if (bytes.isEmpty()) continue
                resources.putIfAbsent(path, bytes)
                chapterImages.add(
                    ChapterImage(
                        charOffset = offset + image.charOffset,
                        resource = path,
                        alt = image.alt,
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
                ),
            ),
            toc = toc,
            resources = resources,
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
}
