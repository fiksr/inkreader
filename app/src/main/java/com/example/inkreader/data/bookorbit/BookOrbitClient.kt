package com.example.inkreader.data.bookorbit

import android.content.Context
import android.net.Uri
import com.example.inkreader.data.EpubParser
import com.example.inkreader.data.NetworkUtils
import com.example.inkreader.data.sync.KoSyncClient
import com.example.inkreader.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

class BookOrbitClient(
    private val context: Context,
    private val syncClient: KoSyncClient
) {
    private val _downloadProgress = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<Int, Float>> = _downloadProgress.asStateFlow()

    private fun getBaseUrl(): String {
        val s = syncClient.settings.value
        val raw = s.serverUrl.trim().trimEnd('/')
        return when {
            raw.isEmpty() -> "https://buks.lol"
            raw.endsWith("/api/v1/koreader") -> raw.substringBeforeLast("/api/v1/koreader")
            raw.endsWith("/api/v1") -> raw.substringBeforeLast("/api/v1")
            else -> raw
        }
    }

    private fun getAuthHeaders(fallbackUser: String = "", fallbackKey: String = ""): Pair<String, String> {
        val s = syncClient.settings.value
        val u = when {
            s.username.isNotBlank() -> s.username.trim()
            fallbackUser.isNotBlank() -> fallbackUser.trim()
            else -> "Kindle"
        }
        val k = when {
            s.userKey.isNotBlank() -> s.userKey.trim()
            fallbackKey.isNotBlank() -> fallbackKey.trim()
            else -> "f751f8e54f4c34b398941489c6585a08"
        }
        return Pair(u, k)
    }

    fun buildUrl(path: String): String {
        val base = getBaseUrl()
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return if (cleanPath.startsWith("http://") || cleanPath.startsWith("https://")) {
            cleanPath
        } else {
            "$base$cleanPath"
        }
    }

    private fun openConnection(
        urlStr: String,
        method: String = "GET",
        authUser: String = "",
        authKey: String = ""
    ): HttpURLConnection {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        if (conn is HttpsURLConnection) {
            NetworkUtils.configureSsl(conn, acceptCustomCerts = true)
        }
        conn.requestMethod = method
        conn.connectTimeout = 12000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "KOReader/2024.04 (Android)")
        conn.setRequestProperty("Accept", "application/json")

        val (user, key) = getAuthHeaders(authUser, authKey)
        if (user.isNotBlank() || key.isNotBlank()) {
            val auth = "$user:$key"
            val encoded = android.util.Base64.encodeToString(auth.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            conn.setRequestProperty("Authorization", "Basic $encoded")
            conn.setRequestProperty("x-auth-user", user)
            conn.setRequestProperty("x-auth-key", key)
        }

        return conn
    }

    suspend fun fetchDashboard(authUser: String = "", authKey: String = ""): Result<BookOrbitDashboard> = withContext(Dispatchers.IO) {
        try {
            val endpoint = buildUrl("/api/v1/koreader/plugin/catalog/dashboard")
            val conn = openConnection(endpoint, authUser = authUser, authKey = authKey)
            val code = conn.responseCode

            if (code in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)

                val continueList = parseBookArray(root.optJSONArray("continueReading"))
                val discoverList = parseBookArray(root.optJSONArray("discover"))

                val sectionsList = mutableListOf<BookOrbitSection>()
                val sectionsArray = root.optJSONArray("browseSections")
                if (sectionsArray != null) {
                    for (i in 0 until sectionsArray.length()) {
                        val obj = sectionsArray.getJSONObject(i)
                        sectionsList.add(
                            BookOrbitSection(
                                id = obj.optString("id", ""),
                                title = obj.optString("title", ""),
                                section = obj.optString("section", ""),
                                href = obj.optNullableString("href"),
                                booksHref = obj.optNullableString("booksHref")
                            )
                        )
                    }
                }

                var streak = 0
                val streakObj = root.optJSONObject("readingStreak")
                if (streakObj != null) {
                    streak = streakObj.optInt("currentStreak", 0)
                }

                var highlight: BookOrbitHighlight? = null
                val hlObj = root.optJSONObject("highlightOfTheDay")
                if (hlObj != null) {
                    highlight = BookOrbitHighlight(
                        text = hlObj.optString("text", ""),
                        bookTitle = hlObj.optString("bookTitle", ""),
                        chapterTitle = hlObj.optNullableString("chapterTitle"),
                        bookId = hlObj.optInt("bookId", 0)
                    )
                }

                Result.success(
                    BookOrbitDashboard(
                        continueReading = continueList,
                        discover = discoverList,
                        browseSections = sectionsList,
                        highlightOfTheDay = highlight,
                        currentStreak = streak
                    )
                )
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Result.failure(Exception("HTTP $code: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSectionBooks(sectionOrHref: String, page: Int = 1, authUser: String = "", authKey: String = ""): Result<List<BookOrbitBook>> =
        withContext(Dispatchers.IO) {
            try {
                val path = when {
                    sectionOrHref.startsWith("http://") || sectionOrHref.startsWith("https://") -> sectionOrHref
                    sectionOrHref.startsWith("/") -> sectionOrHref
                    sectionOrHref == "all-books" -> "/api/v1/koreader/plugin/catalog/books?sort=title&page=$page"
                    else -> "/api/v1/koreader/plugin/catalog/sections/$sectionOrHref?page=$page"
                }

                val conn = openConnection(buildUrl(path), authUser = authUser, authKey = authKey)
                val code = conn.responseCode

                if (code in 200..299) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val list = if (jsonStr.trim().startsWith("[")) {
                        parseBookArray(JSONArray(jsonStr))
                    } else {
                        val obj = JSONObject(jsonStr)
                        parseBookArray(obj.optJSONArray("books") ?: obj.optJSONArray("items") ?: JSONArray())
                    }
                    Result.success(list)
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Result.failure(Exception("HTTP $code: $err"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun searchBooks(query: String, authUser: String = "", authKey: String = ""): Result<List<BookOrbitBook>> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val path = "/api/v1/koreader/plugin/catalog/books?q=$encoded"
            val conn = openConnection(buildUrl(path), authUser = authUser, authKey = authKey)
            val code = conn.responseCode

            if (code in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val list = if (jsonStr.trim().startsWith("[")) {
                    parseBookArray(JSONArray(jsonStr))
                } else {
                    val obj = JSONObject(jsonStr)
                    parseBookArray(obj.optJSONArray("books") ?: obj.optJSONArray("items") ?: JSONArray())
                }
                Result.success(list)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Result.failure(Exception("HTTP $code: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchBookDetail(bookId: Int, authUser: String = "", authKey: String = ""): Result<BookOrbitBook> = withContext(Dispatchers.IO) {
        try {
            val path = "/api/v1/koreader/plugin/catalog/books/$bookId"
            val conn = openConnection(buildUrl(path), authUser = authUser, authKey = authKey)
            val code = conn.responseCode

            if (code in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(jsonStr)
                val book = parseBookObject(obj)
                Result.success(book)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Result.failure(Exception("HTTP $code: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadBook(
        book: BookOrbitBook,
        authUser: String = "",
        authKey: String = ""
    ): Result<Book> = withContext(Dispatchers.IO) {
        try {
            updateProgress(book.id, 0.05f)

            var targetFileId = book.fileId
            var targetDownloadUrl = book.downloadUrl

            // If fileId not known, query detail endpoint first
            if (targetFileId == null && targetDownloadUrl == null) {
                val detailResult = fetchBookDetail(book.id, authUser = authUser, authKey = authKey)
                val detail = detailResult.getOrNull()
                if (detail != null) {
                    targetFileId = detail.fileId
                    targetDownloadUrl = detail.downloadUrl
                }
            }

            val fileUrlStr = targetDownloadUrl?.let { buildUrl(it) }
                ?: targetFileId?.let { buildUrl("/api/v1/koreader/plugin/catalog/files/$it/download") }
                ?: return@withContext Result.failure(Exception("No EPUB file found for '${book.title}'"))

            val booksDir = File(context.filesDir, "downloaded_books").apply { mkdirs() }
            val safeFileName = book.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(40)
            val targetFile = File(booksDir, "${safeFileName}_${System.currentTimeMillis()}.epub")

            var currentUrl = fileUrlStr
            var conn: HttpURLConnection? = null
            var redirects = 0
            val (user, key) = getAuthHeaders(authUser, authKey)

            while (redirects < 5) {
                val url = URL(currentUrl)
                val c = (url.openConnection() as HttpURLConnection).apply {
                    if (this is HttpsURLConnection) {
                        NetworkUtils.configureSsl(this, acceptCustomCerts = true)
                    }
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 25000
                    setRequestProperty("User-Agent", "KOReader/2024.04 (Android)")
                    setRequestProperty("Accept", "application/epub+zip,application/octet-stream,*/*")

                    if (user.isNotBlank() || key.isNotBlank()) {
                        val auth = "$user:$key"
                        val encoded = android.util.Base64.encodeToString(auth.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                        setRequestProperty("Authorization", "Basic $encoded")
                        setRequestProperty("x-auth-user", user)
                        setRequestProperty("x-auth-key", key)
                    }
                }

                val code = c.responseCode
                if (code in 300..399) {
                    val location = c.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        currentUrl = if (location.startsWith("http")) location else buildUrl(location)
                        redirects++
                        continue
                    }
                }

                conn = c
                break
            }

            val finalConn = conn ?: return@withContext Result.failure(Exception("Could not connect to download '${book.title}'"))
            val code = finalConn.responseCode
            if (code !in 200..299) {
                updateProgress(book.id, null)
                val err = finalConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                return@withContext Result.failure(Exception("Download failed with HTTP $code: $err"))
            }

            val totalLength = finalConn.contentLength
            var downloadedBytes = 0L

            finalConn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalLength > 0) {
                            updateProgress(book.id, (downloadedBytes.toFloat() / totalLength).coerceIn(0.1f, 0.95f))
                        }
                    }
                }
            }

            updateProgress(book.id, 1.0f)

            // Parse downloaded EPUB into Book model
            val fileUri = Uri.fromFile(targetFile)
            var parsedBook = EpubParser.parseEpub(context, fileUri)
                ?: Book(
                    id = UUID.randomUUID().toString(),
                    title = book.title,
                    author = book.authors.joinToString(", "),
                    coverPattern = kotlin.math.abs(book.title.hashCode() % 5),
                    coverImagePath = null,
                    progressPercent = (book.progressPercentage?.toInt() ?: 0).coerceIn(0, 100),
                    currentChapterIndex = 0,
                    currentPageIndex = 0,
                    chapters = listOf(
                        com.example.inkreader.model.Chapter(
                            id = "1",
                            title = "Chapter 1",
                            content = book.description ?: "Downloaded from BookOrbit."
                        )
                    ),
                    isCustomImported = true,
                    filePath = targetFile.absolutePath
                )

            // Attach cover image from cache if available
            if (parsedBook.coverImagePath == null) {
                val thumbUrl = book.thumbnailUrl ?: buildUrl("/api/v1/koreader/plugin/catalog/books/${book.id}/thumbnail")
                val hash = try {
                    val md = java.security.MessageDigest.getInstance("MD5")
                    val bytes = md.digest(thumbUrl.toByteArray(Charsets.UTF_8))
                    bytes.joinToString("") { "%02x".format(it) }
                } catch (_: Exception) { thumbUrl.replace(Regex("[^a-zA-Z0-9]"), "_").take(32) }

                val cacheFile = File(File(context.cacheDir, "cover_cache"), "$hash.img")
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    val persistentCoversDir = File(context.filesDir, "covers").apply { mkdirs() }
                    val persistentCover = File(persistentCoversDir, "${parsedBook.id}.jpg")
                    try {
                        cacheFile.copyTo(persistentCover, overwrite = true)
                        parsedBook = parsedBook.copy(coverImagePath = persistentCover.absolutePath)
                    } catch (_: Exception) {}
                }
            }

            updateProgress(book.id, null)
            Result.success(parsedBook.copy(filePath = targetFile.absolutePath))
        } catch (e: Exception) {
            updateProgress(book.id, null)
            Result.failure(e)
        }
    }

    private fun parseBookArray(array: JSONArray?): List<BookOrbitBook> {
        if (array == null) return emptyList()
        val list = mutableListOf<BookOrbitBook>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            list.add(parseBookObject(obj))
        }
        return list
    }

    private fun parseBookObject(obj: JSONObject): BookOrbitBook {
        val authorsList = mutableListOf<String>()
        val authorsArr = obj.optJSONArray("authors")
        if (authorsArr != null) {
            for (j in 0 until authorsArr.length()) {
                authorsList.add(authorsArr.optString(j))
            }
        }

        val formatsList = mutableListOf<String>()
        val formatsArr = obj.optJSONArray("formats")
        if (formatsArr != null) {
            for (j in 0 until formatsArr.length()) {
                formatsList.add(formatsArr.optString(j))
            }
        }

        var primaryFileId: Int? = null
        var primaryDownloadUrl: String? = null
        val filesArr = obj.optJSONArray("files")
        if (filesArr != null) {
            for (k in 0 until filesArr.length()) {
                val fObj = filesArr.optJSONObject(k) ?: continue
                val fmt = fObj.optString("format", "").lowercase()
                if (fmt == "epub" || primaryFileId == null) {
                    primaryFileId = fObj.optInt("id")
                    primaryDownloadUrl = fObj.optNullableString("downloadUrl")
                }
            }
        }

        val bookId = obj.optInt("id", 0)
        val thumbUrl = obj.optNullableString("thumbnailUrl")?.let { buildUrl(it) }
            ?: if (bookId > 0 && obj.optBoolean("hasCover", true)) buildUrl("/api/v1/koreader/plugin/catalog/books/$bookId/thumbnail") else null
        val detUrl = obj.optNullableString("detailUrl")?.let { buildUrl(it) }

        return BookOrbitBook(
            id = bookId,
            title = obj.optString("title", "Untitled"),
            authors = authorsList,
            seriesName = obj.optNullableString("seriesName"),
            seriesIndex = obj.optNullableString("seriesIndex"),
            progressPercentage = if (obj.has("progressPercentage") && !obj.isNull("progressPercentage")) obj.optDouble("progressPercentage") else null,
            readStatus = obj.optNullableString("readStatus"),
            formats = formatsList,
            hasCover = obj.optBoolean("hasCover", true),
            thumbnailUrl = thumbUrl,
            detailUrl = detUrl,
            description = obj.optNullableString("description")?.let { cleanHtml(it) },
            publisher = obj.optNullableString("publisher"),
            publishedYear = if (obj.has("publishedYear") && !obj.isNull("publishedYear")) obj.optInt("publishedYear") else null,
            fileId = primaryFileId,
            downloadUrl = primaryDownloadUrl
        )
    }

    private fun JSONObject.optNullableString(name: String): String? {
        return if (has(name) && !isNull(name)) {
            val s = optString(name, "").trim()
            if (s.isEmpty() || s == "null") null else s
        } else null
    }

    private fun cleanHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun updateProgress(bookId: Int, progress: Float?) {
        val current = _downloadProgress.value.toMutableMap()
        if (progress == null) {
            current.remove(bookId)
        } else {
            current[bookId] = progress
        }
        _downloadProgress.value = current
    }
}
