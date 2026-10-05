package com.example.inkreader.data

import android.content.Context
import android.net.Uri
import com.example.inkreader.model.Book
import com.example.inkreader.model.Chapter
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object EpubParser {

    fun parseEpub(context: Context, uri: Uri): Book? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return null

            // Cache EPUB to internal storage so it is persistent across restarts & syncable
            val importedDir = File(context.filesDir, "imported_books").apply { mkdirs() }
            val bookId = UUID.randomUUID().toString()
            val tempFile = File(importedDir, "book_${bookId}.epub")

            FileOutputStream(tempFile).use { out ->
                inputStream.copyTo(out)
            }

            FileInputStream(tempFile).use { fis ->
                val book = parseEpubStream(context, fis, tempFile.absolutePath)
                book.copy(id = bookId, filePath = tempFile.absolutePath)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun parseEpubStream(context: Context?, inputStream: InputStream, sourcePath: String): Book {
        var title = "Imported Book"
        var author = "Unknown Author"
        val htmlEntries = mutableMapOf<String, String>()
        var coverBytes: ByteArray? = null
        val bookId = UUID.randomUUID().toString()
        val zip = ZipInputStream(inputStream)
        var entry: ZipEntry? = zip.nextEntry

        while (entry != null) {
            val name = entry.name
            val lower = name.lowercase()
            if (lower.endsWith(".html") || lower.endsWith(".xhtml") || lower.endsWith(".htm")) {
                val content = zip.bufferedReader().readText()
                htmlEntries[name] = content
            } else if (lower.endsWith(".opf")) {
                val opfContent = zip.bufferedReader().readText()
                val (parsedTitle, parsedAuthor) = extractOpfMetadata(opfContent)
                if (parsedTitle.isNotEmpty()) title = parsedTitle
                if (parsedAuthor.isNotEmpty()) author = parsedAuthor
            } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")) {
                val bytes = zip.readBytes()
                if (coverBytes == null || lower.contains("cover")) {
                    coverBytes = bytes
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        zip.close()

        var coverImagePath: String? = null
        if (coverBytes != null && context != null) {
            try {
                val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
                val coverFile = File(coversDir, "$bookId.jpg")
                coverFile.writeBytes(coverBytes)
                coverImagePath = coverFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val chapters = mutableListOf<Chapter>()
        var chapterIndex = 1
        htmlEntries.entries.sortedBy { it.key }.forEach { (_, html) ->
            val cleanText = htmlToPlainText(html)
            if (cleanText.isNotBlank()) {
                val chapterTitle = extractChapterTitle(html) ?: "Chapter $chapterIndex"
                chapters.add(Chapter(
                    id = "ch_$chapterIndex",
                    title = chapterTitle,
                    content = cleanText
                ))
                chapterIndex++
            }
        }

        if (chapters.isEmpty()) {
            chapters.add(Chapter("1", "Chapter 1", "This EPUB file could not be parsed into readable text."))
        }

        return Book(
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
            filePath = sourcePath
        )
    }

    private fun extractOpfMetadata(opf: String): Pair<String, String> {
        var title = ""
        var author = ""
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(opf.reader())

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name.lowercase()) {
                        "title" -> title = parser.nextText().trim()
                        "creator" -> if (author.isEmpty()) author = parser.nextText().trim()
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}
        return Pair(title, author)
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
