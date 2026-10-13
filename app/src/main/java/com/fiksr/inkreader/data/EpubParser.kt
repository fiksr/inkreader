package com.fiksr.inkreader.data

import android.content.Context
import android.net.Uri
import com.fiksr.inkreader.model.Book
import com.fiksr.inkreader.model.Chapter
import com.fiksr.inkreader.model.TocItem
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

data class RawTocItem(
    val id: String = "",
    val title: String,
    val href: String,
    val normalizedHref: String,
    val level: Int = 0
)

data class OpfData(
    val title: String = "",
    val author: String = "",
    val manifest: Map<String, String> = emptyMap(), // id -> normalized path
    val spineHrefs: List<String> = emptyList(), // ordered list of spine file paths
    val coverHref: String? = null,
    val ncxHref: String? = null,
    val navHref: String? = null
)

object EpubParser {

    fun parseEpub(context: Context, uri: Uri): Book? {
        val importedDir = File(context.filesDir, "imported_books").apply { mkdirs() }
        val bookId = UUID.randomUUID().toString()
        val targetFile = File(importedDir, "book_${bookId}.epub")

        return try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return null

            FileOutputStream(targetFile).use { out ->
                inputStream.copyTo(out)
            }

            val book = parseEpubFile(context, targetFile, bookId)
            if (book == null || book.chapters.isEmpty()) {
                if (targetFile.exists()) targetFile.delete()
                null
            } else {
                book
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (targetFile.exists()) targetFile.delete()
            null
        }
    }

    fun parseEpubStream(context: Context?, inputStream: InputStream, sourcePath: String): Book? {
        val file = File(sourcePath)
        if (file.exists() && file.length() > 0) {
            return parseEpubFile(context, file, UUID.randomUUID().toString())
        }
        val temp = File.createTempFile("epub_stream_", ".epub")
        return try {
            FileOutputStream(temp).use { inputStream.copyTo(it) }
            parseEpubFile(context, temp, UUID.randomUUID().toString())
        } finally {
            temp.delete()
        }
    }

    fun parseEpubFile(context: Context?, file: File, bookId: String): Book? {
        if (!file.exists() || file.length() < 100) return null
        var zipFile: ZipFile? = null

        return try {
            zipFile = ZipFile(file)

            // 1. Locate OPF file via META-INF/container.xml
            val containerEntry = findZipEntry(zipFile, "META-INF/container.xml")
            val opfPath = if (containerEntry != null) {
                zipFile.getInputStream(containerEntry).use { extractOpfPathFromContainer(it) }
            } else {
                zipFile.entries().asSequence().firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }?.name
            } ?: return null

            val opfEntry = findZipEntry(zipFile, opfPath) ?: return null
            val opfDir = if (opfPath.contains('/')) opfPath.substringBeforeLast('/') else ""

            // 2. Parse OPF metadata, manifest, and spine
            val opfData = zipFile.getInputStream(opfEntry).use { parseOpf(it, opfDir) }

            val title = opfData.title.ifBlank { file.nameWithoutExtension.replace('_', ' ') }
            val author = opfData.author.ifBlank { "Unknown Author" }

            // 3. Extract cover image if available
            var coverImagePath: String? = null
            val coverHref = opfData.coverHref
            if (coverHref != null && context != null) {
                val coverEntry = findZipEntry(zipFile, coverHref)
                if (coverEntry != null) {
                    try {
                        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
                        val coverFile = File(coversDir, "$bookId.jpg")
                        zipFile.getInputStream(coverEntry).use { input ->
                            FileOutputStream(coverFile).use { out ->
                                input.copyTo(out)
                            }
                        }
                        coverImagePath = coverFile.absolutePath
                    } catch (_: Exception) {}
                }
            }

            // 4. Parse Table of Contents from NCX (EPUB 2) or Nav (EPUB 3)
            val rawToc = mutableListOf<RawTocItem>()
            if (opfData.navHref != null) {
                val navEntry = findZipEntry(zipFile, opfData.navHref)
                if (navEntry != null) {
                    val navDir = if (opfData.navHref.contains('/')) opfData.navHref.substringBeforeLast('/') else ""
                    zipFile.getInputStream(navEntry).use { stream ->
                        parseEpub3Nav(stream, navDir, rawToc)
                    }
                }
            }
            if (rawToc.isEmpty() && opfData.ncxHref != null) {
                val ncxEntry = findZipEntry(zipFile, opfData.ncxHref)
                if (ncxEntry != null) {
                    val ncxDir = if (opfData.ncxHref.contains('/')) opfData.ncxHref.substringBeforeLast('/') else ""
                    zipFile.getInputStream(ncxEntry).use { stream ->
                        parseNcx(stream, ncxDir, rawToc)
                    }
                }
            }

            // 5. Read spine HTML items in exact spine order
            val spineItems = if (opfData.spineHrefs.isNotEmpty()) {
                opfData.spineHrefs
            } else {
                zipFile.entries().asSequence()
                    .filter {
                        val lower = it.name.lowercase()
                        lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")
                    }
                    .map { it.name }
                    .sorted()
                    .toList()
            }

            if (spineItems.isEmpty()) return null

            val chapters = mutableListOf<Chapter>()
            val spineFileToChapterIndex = mutableMapOf<String, Int>()

            for (itemHref in spineItems) {
                val entry = findZipEntry(zipFile, itemHref) ?: continue
                val htmlContent = zipFile.getInputStream(entry).bufferedReader().readText()
                val plainText = htmlToPlainText(htmlContent)
                if (plainText.isBlank()) continue

                // Chapter title: Match TOC title if available, else heading, else fallback
                val matchingTocItem = rawToc.firstOrNull { it.normalizedHref == itemHref }
                val chapterTitle = matchingTocItem?.title
                    ?: extractChapterTitle(htmlContent)
                    ?: "Chapter ${chapters.size + 1}"

                val chIdx = chapters.size
                chapters.add(
                    Chapter(
                        id = "ch_${chIdx + 1}",
                        title = chapterTitle,
                        content = plainText
                    )
                )
                spineFileToChapterIndex[itemHref] = chIdx
            }

            if (chapters.isEmpty()) return null

            // 6. Map TOC items to parsed chapter indices
            val finalToc = mutableListOf<TocItem>()
            if (rawToc.isNotEmpty()) {
                for (raw in rawToc) {
                    val targetChIdx = spineFileToChapterIndex[raw.normalizedHref]
                    if (targetChIdx != null) {
                        finalToc.add(
                            TocItem(
                                id = raw.id,
                                title = raw.title,
                                href = raw.href,
                                targetChapterIndex = targetChIdx,
                                targetCharOffset = 0,
                                level = raw.level
                            )
                        )
                    }
                }
            }

            Book(
                id = bookId,
                title = title,
                author = author,
                coverPattern = (title.hashCode() and 0x7FFFFFFF) % 4,
                coverImagePath = coverImagePath,
                progressPercent = 0,
                currentChapterIndex = 0,
                currentPageIndex = 0,
                chapters = chapters,
                isCustomImported = true,
                filePath = file.absolutePath,
                toc = finalToc
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { zipFile?.close() } catch (_: Exception) {}
        }
    }

    private fun extractOpfPathFromContainer(stream: InputStream): String? {
        val doc = parseXmlDocument(stream) ?: return null
        val rootfiles = doc.getElementsByTagName("rootfile")
        for (i in 0 until rootfiles.length) {
            val el = rootfiles.item(i) as? Element ?: continue
            val fullPath = el.getAttribute("full-path").trim()
            if (fullPath.isNotBlank()) return fullPath
        }
        return null
    }

    private fun parseOpf(stream: InputStream, opfDir: String): OpfData {
        val doc = parseXmlDocument(stream) ?: return OpfData()

        // Metadata
        var title = ""
        val titleNodes = doc.getElementsByTagNameNS("*", "title")
        if (titleNodes.length > 0) {
            title = titleNodes.item(0).textContent.trim()
        }
        if (title.isBlank()) {
            val simpleTitles = doc.getElementsByTagName("dc:title")
            if (simpleTitles.length > 0) title = simpleTitles.item(0).textContent.trim()
        }

        var author = ""
        val creatorNodes = doc.getElementsByTagNameNS("*", "creator")
        if (creatorNodes.length > 0) {
            author = creatorNodes.item(0).textContent.trim()
        }
        if (author.isBlank()) {
            val simpleCreators = doc.getElementsByTagName("dc:creator")
            if (simpleCreators.length > 0) author = simpleCreators.item(0).textContent.trim()
        }

        // Manifest
        val manifestMap = mutableMapOf<String, String>()
        var coverHref: String? = null
        var ncxHref: String? = null
        var navHref: String? = null
        var coverMetaId: String? = null

        val metaNodes = doc.getElementsByTagName("meta")
        for (i in 0 until metaNodes.length) {
            val el = metaNodes.item(i) as? Element ?: continue
            if (el.getAttribute("name").equals("cover", ignoreCase = true)) {
                coverMetaId = el.getAttribute("content").trim()
            }
        }

        val itemNodes = doc.getElementsByTagName("item")
        for (i in 0 until itemNodes.length) {
            val el = itemNodes.item(i) as? Element ?: continue
            val id = el.getAttribute("id").trim()
            val rawHref = el.getAttribute("href").trim()
            val mediaType = el.getAttribute("media-type").trim().lowercase()
            val properties = el.getAttribute("properties").trim().lowercase()

            if (id.isBlank() || rawHref.isBlank()) continue
            val normPath = normalizeZipPath(opfDir, rawHref)
            manifestMap[id] = normPath

            if (properties.contains("nav")) {
                navHref = normPath
            }
            if (mediaType == "application/x-dtbncx+xml" || id.equals("ncx", ignoreCase = true)) {
                ncxHref = normPath
            }
            if (properties.contains("cover-image") || (coverMetaId != null && id == coverMetaId)) {
                coverHref = normPath
            } else if (coverHref == null && mediaType.startsWith("image/") && (id.contains("cover", ignoreCase = true) || rawHref.contains("cover", ignoreCase = true))) {
                coverHref = normPath
            }
        }

        // Spine
        val spineHrefs = mutableListOf<String>()
        val spineNodes = doc.getElementsByTagName("spine")
        if (spineNodes.length > 0) {
            val spineEl = spineNodes.item(0) as? Element
            val tocId = spineEl?.getAttribute("toc")?.trim()
            if (ncxHref == null && !tocId.isNullOrBlank()) {
                ncxHref = manifestMap[tocId]
            }

            val itemrefNodes = doc.getElementsByTagName("itemref")
            for (i in 0 until itemrefNodes.length) {
                val el = itemrefNodes.item(i) as? Element ?: continue
                val idref = el.getAttribute("idref").trim()
                val targetPath = manifestMap[idref]
                if (targetPath != null) {
                    spineHrefs.add(targetPath)
                }
            }
        }

        return OpfData(
            title = title,
            author = author,
            manifest = manifestMap,
            spineHrefs = spineHrefs,
            coverHref = coverHref,
            ncxHref = ncxHref,
            navHref = navHref
        )
    }

    private fun parseNcx(stream: InputStream, ncxDir: String, outList: MutableList<RawTocItem>) {
        val doc = parseXmlDocument(stream) ?: return
        val navMapNodes = doc.getElementsByTagName("navMap")
        if (navMapNodes.length == 0) return
        val navMap = navMapNodes.item(0) as? Element ?: return
        parseNcxChildren(navMap, ncxDir, 0, outList)
    }

    private fun parseNcxChildren(parent: Element, ncxDir: String, level: Int, outList: MutableList<RawTocItem>) {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName.equals("navPoint", ignoreCase = true)) {
                val id = node.getAttribute("id").trim()
                var title = ""
                val labelNodes = node.getElementsByTagName("navLabel")
                if (labelNodes.length > 0) {
                    val labelEl = labelNodes.item(0) as? Element
                    val textNodes = labelEl?.getElementsByTagName("text")
                    if (textNodes != null && textNodes.length > 0) {
                        title = textNodes.item(0).textContent.trim()
                    }
                }
                var src = ""
                val contentNodes = node.getElementsByTagName("content")
                if (contentNodes.length > 0) {
                    val contentEl = contentNodes.item(0) as? Element
                    src = contentEl?.getAttribute("src")?.trim() ?: ""
                }

                if (title.isNotBlank() && src.isNotBlank()) {
                    val cleanSrc = src.substringBefore('#')
                    val normPath = normalizeZipPath(ncxDir, cleanSrc)
                    outList.add(
                        RawTocItem(
                            id = id,
                            title = title,
                            href = normalizeZipPath(ncxDir, src),
                            normalizedHref = normPath,
                            level = level
                        )
                    )
                }

                // Recursively parse nested navPoints (subchapters)
                parseNcxChildren(node, ncxDir, level + 1, outList)
            }
        }
    }

    private fun parseEpub3Nav(stream: InputStream, navDir: String, outList: MutableList<RawTocItem>) {
        val doc = parseXmlDocument(stream) ?: return
        val navNodes = doc.getElementsByTagName("nav")
        var targetNav: Element? = null
        for (i in 0 until navNodes.length) {
            val el = navNodes.item(i) as? Element ?: continue
            val epubType = el.getAttribute("epub:type").trim().lowercase()
            val id = el.getAttribute("id").trim().lowercase()
            if (epubType == "toc" || id == "toc") {
                targetNav = el
                break
            }
        }
        if (targetNav == null && navNodes.length > 0) {
            targetNav = navNodes.item(0) as? Element
        }
        if (targetNav != null) {
            val olNodes = targetNav.getElementsByTagName("ol")
            if (olNodes.length > 0) {
                parseNavOl(olNodes.item(0) as Element, navDir, 0, outList)
            }
        }
    }

    private fun parseNavOl(ol: Element, navDir: String, level: Int, outList: MutableList<RawTocItem>) {
        val children = ol.childNodes
        for (i in 0 until children.length) {
            val li = children.item(i)
            if (li is Element && li.tagName.equals("li", ignoreCase = true)) {
                // Find direct <a> tag
                val aNodes = li.getElementsByTagName("a")
                if (aNodes.length > 0) {
                    val a = aNodes.item(0) as Element
                    val href = a.getAttribute("href").trim()
                    val title = a.textContent.trim()
                    if (title.isNotBlank() && href.isNotBlank()) {
                        val cleanHref = href.substringBefore('#')
                        val normPath = normalizeZipPath(navDir, cleanHref)
                        outList.add(
                            RawTocItem(
                                title = title,
                                href = normalizeZipPath(navDir, href),
                                normalizedHref = normPath,
                                level = level
                            )
                        )
                    }
                }
                // Check for nested <ol>
                val subOlNodes = li.getElementsByTagName("ol")
                if (subOlNodes.length > 0) {
                    parseNavOl(subOlNodes.item(0) as Element, navDir, level + 1, outList)
                }
            }
        }
    }

    fun normalizeZipPath(baseDir: String, relativePath: String): String {
        val decoded = try { URLDecoder.decode(relativePath.trim(), "UTF-8") } catch (_: Exception) { relativePath.trim() }
        val cleanRel = decoded.replace('\\', '/')
        val full = if (cleanRel.startsWith("/")) cleanRel.drop(1)
        else if (baseDir.isBlank()) cleanRel
        else if (baseDir.endsWith("/")) "$baseDir$cleanRel"
        else "$baseDir/$cleanRel"

        val parts = full.split("/").filter { it.isNotEmpty() }
        val stack = mutableListOf<String>()
        for (part in parts) {
            if (part == ".") continue
            if (part == "..") {
                if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
            } else {
                stack.add(part)
            }
        }
        return stack.joinToString("/")
    }

    private fun findZipEntry(zip: ZipFile, path: String): ZipEntry? {
        val direct = zip.getEntry(path)
        if (direct != null) return direct

        val decoded = try { URLDecoder.decode(path, "UTF-8") } catch (_: Exception) { path }
        val decodedEntry = zip.getEntry(decoded)
        if (decodedEntry != null) return decodedEntry

        // Case-insensitive match
        return zip.entries().asSequence().firstOrNull {
            it.name.equals(path, ignoreCase = true) || it.name.equals(decoded, ignoreCase = true)
        }
    }

    private fun parseXmlDocument(stream: InputStream): org.w3c.dom.Document? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            factory.isValidating = false
            try {
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            } catch (_: Exception) {}
            factory.newDocumentBuilder().parse(stream)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractChapterTitle(html: String): String? {
        val h1Regex = "<h[1-3][^>]*>(.*?)</h[1-3]>".toRegex(RegexOption.IGNORE_CASE)
        val match = h1Regex.find(html)
        return match?.groupValues?.get(1)?.replace("<[^>]*>".toRegex(), "")?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun htmlToPlainText(html: String): String {
        val raw = html
            .replace("<style[\\s\\S]*?</style>".toRegex(RegexOption.IGNORE_CASE), "")
            .replace("<script[\\s\\S]*?</script>".toRegex(RegexOption.IGNORE_CASE), "")
            .replace("<head[\\s\\S]*?</head>".toRegex(RegexOption.IGNORE_CASE), "")
            .replace("</p>", "\n")
            .replace("<br/?>".toRegex(), "\n")
            .replace("</div>", "\n")
            .replace("</h1>", "\n\n")
            .replace("</h2>", "\n\n")
            .replace("</h3>", "\n\n")
            .replace("<[^>]*>".toRegex(), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")

        val lines = raw.lines().map { it.trim() }
        val cleanParagraphs = mutableListOf<String>()
        var pendingPara = StringBuilder()

        for (line in lines) {
            if (line.isEmpty()) {
                if (pendingPara.isNotEmpty()) {
                    cleanParagraphs.add(pendingPara.toString().trim())
                    pendingPara = StringBuilder()
                }
            } else {
                if (pendingPara.isNotEmpty()) {
                    if (line.startsWith("‘") || line.startsWith("“") || line.startsWith("\"") || line.startsWith("'") || line.startsWith("—") || line.startsWith("„") || line.startsWith("«")) {
                        cleanParagraphs.add(pendingPara.toString().trim())
                        pendingPara = StringBuilder(line)
                    } else {
                        pendingPara.append(" ").append(line)
                    }
                } else {
                    pendingPara.append(line)
                }
            }
        }
        if (pendingPara.isNotEmpty()) {
            cleanParagraphs.add(pendingPara.toString().trim())
        }

        return cleanParagraphs.joinToString("\n\n")
    }
}
