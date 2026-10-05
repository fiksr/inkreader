package com.example.inkreader.data.opds

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.URI

object OpdsParser {

    fun parseFeed(inputStream: InputStream, baseUrl: String): OpdsFeed {
        var feedTitle = "OPDS Catalog"
        var feedSubtitle = ""
        var searchHref: String? = null
        var nextPageHref: String? = null

        val entries = mutableListOf<OpdsEntry>()
        val navLinks = mutableListOf<OpdsLink>()

        try {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(inputStream, "UTF-8")
            }

            var eventType = parser.eventType
            var inFeed = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name?.lowercase() ?: ""

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tagName) {
                            "feed" -> inFeed = true
                            "title" -> {
                                if (inFeed && parser.depth <= 2) {
                                    feedTitle = readText(parser)
                                }
                            }
                            "subtitle" -> {
                                if (inFeed && parser.depth <= 2) {
                                    feedSubtitle = readText(parser)
                                }
                            }
                            "link" -> {
                                if (inFeed && parser.depth <= 2) {
                                    val rel = parser.getAttributeValue(null, "rel") ?: ""
                                    val href = parser.getAttributeValue(null, "href") ?: ""
                                    val type = parser.getAttributeValue(null, "type") ?: ""
                                    val title = parser.getAttributeValue(null, "title") ?: ""
                                    val absHref = resolveUrl(baseUrl, href)

                                    if (rel.contains("search", ignoreCase = true)) {
                                        searchHref = absHref
                                    } else if (rel == "next") {
                                        nextPageHref = absHref
                                    } else if (rel.contains("subsection") || rel.contains("start") || rel.contains("parent") || rel == "alternate") {
                                        navLinks.add(OpdsLink(rel = rel, href = absHref, type = type, title = title.ifEmpty { rel }))
                                    }
                                }
                            }
                            "entry" -> {
                                val entry = parseEntry(parser, baseUrl)
                                if (entry != null) {
                                    entries.add(entry)
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}

        return OpdsFeed(
            title = feedTitle,
            subtitle = feedSubtitle,
            entries = entries,
            navigationLinks = navLinks,
            searchHref = searchHref,
            nextPageHref = nextPageHref
        )
    }

    private fun parseEntry(parser: XmlPullParser, baseUrl: String): OpdsEntry? {
        var id = ""
        var title = "Untitled Book"
        var author = "Unknown"
        var summary = ""
        var published = ""
        var coverUrl: String? = null
        var thumbnailUrl: String? = null
        val acquisitions = mutableListOf<OpdsAcquisitionLink>()
        var navHref: String? = null
        var isNavOnly = true

        val initialDepth = parser.depth

        while (parser.next() != XmlPullParser.END_TAG || parser.depth > initialDepth) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            val tagName = parser.name?.lowercase() ?: ""
            when (tagName) {
                "id" -> id = readText(parser)
                "title" -> title = readText(parser)
                "author" -> author = parseAuthor(parser)
                "summary", "content" -> summary = cleanHtmlSummary(readText(parser))
                "published", "issued", "updated" -> published = readText(parser)
                "link" -> {
                    val rel = parser.getAttributeValue(null, "rel") ?: ""
                    val href = parser.getAttributeValue(null, "href") ?: ""
                    val type = parser.getAttributeValue(null, "type") ?: ""
                    val linkTitle = parser.getAttributeValue(null, "title") ?: ""
                    val absHref = resolveUrl(baseUrl, href)

                    if (rel.contains("image", ignoreCase = true) || rel.contains("cover", ignoreCase = true)) {
                        coverUrl = absHref
                    } else if (rel.contains("thumbnail", ignoreCase = true)) {
                        thumbnailUrl = absHref
                    } else if (rel.contains("acquisition", ignoreCase = true) || type.contains("epub", ignoreCase = true) || href.endsWith(".epub", ignoreCase = true)) {
                        isNavOnly = false
                        acquisitions.add(
                            OpdsAcquisitionLink(
                                href = absHref,
                                type = type.ifEmpty { "application/epub+zip" },
                                rel = rel,
                                title = linkTitle
                            )
                        )
                    } else if (rel.contains("subsection") || type.contains("atom+xml") || type.contains("opds")) {
                        navHref = absHref
                    }
                }
            }
        }

        if (id.isEmpty()) id = title

        return OpdsEntry(
            id = id,
            title = title,
            author = author,
            summary = summary,
            published = published,
            coverUrl = coverUrl ?: thumbnailUrl,
            thumbnailUrl = thumbnailUrl ?: coverUrl,
            acquisitionLinks = acquisitions,
            navigationHref = navHref,
            isNavigationOnly = isNavOnly && acquisitions.isEmpty() && navHref != null
        )
    }

    private fun parseAuthor(parser: XmlPullParser): String {
        var authorName = "Unknown"
        val depth = parser.depth
        while (parser.next() != XmlPullParser.END_TAG || parser.depth > depth) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name?.lowercase() == "name") {
                authorName = readText(parser)
            }
        }
        return authorName
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text ?: ""
            parser.nextTag()
        }
        return result.trim()
    }

    private fun cleanHtmlSummary(html: String): String {
        return html.replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun resolveUrl(base: String, relative: String): String {
        return try {
            val baseUri = URI(base)
            baseUri.resolve(relative).toString()
        } catch (_: Exception) {
            if (relative.startsWith("http://") || relative.startsWith("https://")) {
                relative
            } else {
                val cleanBase = base.substringBeforeLast('/')
                "$cleanBase/${relative.trimStart('/')}"
            }
        }
    }
}
