package com.fiksr.inkreader.data.opds

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import com.fiksr.inkreader.data.EpubParser
import com.fiksr.inkreader.data.NetworkUtils
import com.fiksr.inkreader.model.Book
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
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

class OpdsRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("inkreader_opds_catalogs", Context.MODE_PRIVATE)

    private val _catalogs = MutableStateFlow<List<OpdsCatalog>>(emptyList())
    val catalogs: StateFlow<List<OpdsCatalog>> = _catalogs.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    init {
        loadCatalogs()
    }

    private fun loadCatalogs() {
        if (!prefs.contains("saved_catalogs")) {
            val defaults = getDefaultCatalogs()
            saveCatalogs(defaults)
            return
        }

        val jsonString = prefs.getString("saved_catalogs", null)
        val list = mutableListOf<OpdsCatalog>()

        if (jsonString != null) {
            try {
                val array = JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        OpdsCatalog(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            url = obj.getString("url"),
                            description = obj.optString("description", ""),
                            username = obj.optString("username", ""),
                            password = obj.optString("password", ""),
                            isDefault = obj.optBoolean("isDefault", false)
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        // Auto-migrate any stale local IP for BookOrbit default catalog
        var changed = false
        val migrated = list.map { cat ->
            if (cat.id == "bookorbit_default" && (cat.url.contains("192.168.1.100") || (cat.url.endsWith("/opds") && !cat.url.contains("buks.lol")))) {
                changed = true
                cat.copy(url = "https://buks.lol/api/v1/opds")
            } else cat
        }
        if (changed) {
            saveCatalogs(migrated)
        } else {
            _catalogs.value = list
        }
    }

    private fun saveCatalogs(list: List<OpdsCatalog>) {
        _catalogs.value = list
        val array = JSONArray()
        for (cat in list) {
            val obj = JSONObject().apply {
                put("id", cat.id)
                put("name", cat.name)
                put("url", cat.url)
                put("description", cat.description)
                put("username", cat.username)
                put("password", cat.password)
                put("isDefault", cat.isDefault)
            }
            array.put(obj)
        }
        prefs.edit().putString("saved_catalogs", array.toString()).apply()
    }

    private fun getDefaultCatalogs(): List<OpdsCatalog> {
        return listOf(
            OpdsCatalog(
                id = "bookorbit_default",
                name = "BookOrbit Server",
                url = "https://buks.lol/api/v1/opds",
                description = "Connect to your BookOrbit library via OPDS.",
                isDefault = true
            ),
            OpdsCatalog(
                id = "standard_ebooks",
                name = "Standard Ebooks",
                url = "https://standardebooks.org/opds/all",
                description = "Free, public domain ebooks carefully typeset and edited.",
                isDefault = true
            ),
            OpdsCatalog(
                id = "gutenberg",
                name = "Project Gutenberg",
                url = "https://m.gutenberg.org/ebooks.opds/",
                description = "Over 70,000 free historical public domain books.",
                isDefault = true
            ),
            OpdsCatalog(
                id = "calibre_default",
                name = "Calibre Content Server",
                url = "http://192.168.1.100:8080/opds",
                description = "Connect to your home Calibre library via OPDS.",
                isDefault = true
            )
        )
    }

    fun addCatalog(name: String, url: String, description: String = "", user: String = "", pass: String = "") {
        val current = _catalogs.value.toMutableList()
        val cleanUrl = url.trim()
        val newCat = OpdsCatalog(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            url = cleanUrl,
            description = description.trim(),
            username = user.trim(),
            password = pass.trim(),
            isDefault = false
        )
        current.add(newCat)
        saveCatalogs(current)
    }

    fun updateCatalog(catalog: OpdsCatalog) {
        val current = _catalogs.value.toMutableList()
        val index = current.indexOfFirst { it.id == catalog.id }
        if (index != -1) {
            current[index] = catalog
            saveCatalogs(current)
        }
    }

    fun removeCatalog(id: String) {
        val current = _catalogs.value.filter { it.id != id }
        saveCatalogs(current)
    }

    suspend fun fetchFeed(url: String, username: String = "", password: String = ""): Result<OpdsFeed> =
        withContext(Dispatchers.IO) {
            try {
                var normalizedUrl = url.trim()
                if (normalizedUrl.endsWith("/api/v1/koreader")) {
                    normalizedUrl = normalizedUrl.substringBeforeLast("/api/v1/koreader") + "/api/v1/opds"
                } else if (normalizedUrl.endsWith("/api/v1/koreader/")) {
                    normalizedUrl = normalizedUrl.substringBeforeLast("/api/v1/koreader/") + "/api/v1/opds"
                }

                val conn = (URL(normalizedUrl).openConnection() as HttpURLConnection).apply {
                    if (this is HttpsURLConnection) {
                        NetworkUtils.configureSsl(this, acceptCustomCerts = true)
                    }
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("User-Agent", "InkReader/1.0 (Android; E-Ink)")
                    setRequestProperty("Accept", "application/atom+xml,application/xml,text/xml,*/*")

                    if (username.isNotBlank() || password.isNotBlank()) {
                        val auth = "$username:$password"
                        val encoded = Base64.encodeToString(auth.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                        setRequestProperty("Authorization", "Basic $encoded")
                    }
                }

                val code = conn.responseCode
                if (code in 200..299) {
                    val feed = conn.inputStream.use { stream ->
                        OpdsParser.parseFeed(stream, url)
                    }
                    Result.success(feed)
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Result.failure(Exception("HTTP $code: $err"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun downloadEpub(
        entry: OpdsEntry,
        username: String = "",
        password: String = "",
        baseUrl: String = ""
    ): Result<Book> = withContext(Dispatchers.IO) {
        val bookNumId = entry.id.toIntOrNull()
        val isBookOrbit = baseUrl.contains("buks.lol") || baseUrl.contains("bookorbit") || bookNumId != null

        val origin = if (baseUrl.isNotBlank() && baseUrl.startsWith("http")) {
            try {
                val u = URL(baseUrl)
                "${u.protocol}://${u.host}${if (u.port != -1 && u.port != 80 && u.port != 443) ":${u.port}" else ""}"
            } catch (_: Exception) { "https://buks.lol" }
        } else {
            "https://buks.lol"
        }

        var downloadUrl: String? = null

        val effKoUser = "Kindle"
        val effKoKey = "f751f8e54f4c34b398941489c6585a08"

        // 1. If BookOrbit and numeric ID, fetch book detail first to get true file ID
        if (isBookOrbit && bookNumId != null) {
            try {
                val detailUrl = "$origin/api/v1/koreader/plugin/catalog/books/$bookNumId"
                val detailConn = (URL(detailUrl).openConnection() as HttpURLConnection).apply {
                    if (this is HttpsURLConnection) NetworkUtils.configureSsl(this, acceptCustomCerts = true)
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("User-Agent", "KOReader/2024.04 (Android)")
                    setRequestProperty("Accept", "application/json")
                    if (username.isNotBlank() || password.isNotBlank()) {
                        val auth = "$username:$password"
                        val encoded = Base64.encodeToString(auth.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                        setRequestProperty("Authorization", "Basic $encoded")
                    }
                    setRequestProperty("x-auth-user", effKoUser)
                    setRequestProperty("x-auth-key", effKoKey)
                }

                if (detailConn.responseCode in 200..299) {
                    val jsonStr = detailConn.inputStream.bufferedReader().use { it.readText() }
                    val root = JSONObject(jsonStr)
                    val filesArr = root.optJSONArray("files")
                    var primaryFileId: Int? = null
                    var primaryDlUrl: String? = null
                    if (filesArr != null) {
                        for (k in 0 until filesArr.length()) {
                            val fObj = filesArr.optJSONObject(k) ?: continue
                            val fmt = fObj.optString("format", "").lowercase()
                            if (fmt == "epub" || primaryFileId == null) {
                                primaryFileId = fObj.optInt("id")
                                primaryDlUrl = fObj.optString("downloadUrl", "")
                            }
                        }
                    }

                    if (!primaryDlUrl.isNullOrBlank()) {
                        downloadUrl = if (primaryDlUrl.startsWith("http")) primaryDlUrl else "$origin${if (primaryDlUrl.startsWith("/")) "" else "/"}$primaryDlUrl"
                    } else if (primaryFileId != null && primaryFileId > 0) {
                        downloadUrl = "$origin/api/v1/koreader/plugin/catalog/files/$primaryFileId/download"
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Fallback to entry acquisition link
        if (downloadUrl.isNullOrBlank()) {
            val rawHref = entry.epubLink?.href?.trim()
            if (!rawHref.isNullOrBlank()) {
                downloadUrl = if (rawHref.startsWith("http://", ignoreCase = true) || rawHref.startsWith("https://", ignoreCase = true)) {
                    rawHref
                } else {
                    "$origin${if (rawHref.startsWith("/")) "" else "/"}$rawHref"
                }
            }
        }

        val finalDownloadUrl = downloadUrl ?: return@withContext Result.failure(Exception("No EPUB download link found for '${entry.title}'"))

        val bookId = UUID.randomUUID().toString()
        val booksDir = File(context.filesDir, "downloaded_books").apply { mkdirs() }
        val safeFileName = entry.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(40)
        val targetFile = File(booksDir, "${safeFileName}_${System.currentTimeMillis()}.epub")

        try {
            updateProgress(entry.id, 0.05f)

            var currentUrl = finalDownloadUrl
            var conn: HttpURLConnection? = null
            var redirects = 0

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
                    setRequestProperty("User-Agent", "KOReader/2024.04 (Android; E-Ink)")
                    setRequestProperty("Accept", "application/epub+zip,application/octet-stream,*/*")

                    if (username.isNotBlank() || password.isNotBlank()) {
                        val auth = "$username:$password"
                        val encoded = Base64.encodeToString(auth.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                        setRequestProperty("Authorization", "Basic $encoded")
                    }
                    if (currentUrl.contains("/koreader/") || isBookOrbit) {
                        setRequestProperty("x-auth-user", effKoUser)
                        setRequestProperty("x-auth-key", effKoKey)
                    }
                }

                val code = c.responseCode
                if (code in 300..399) {
                    val location = c.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        currentUrl = if (location.startsWith("http://", ignoreCase = true) || location.startsWith("https://", ignoreCase = true)) {
                            location
                        } else {
                            val u = URL(currentUrl)
                            "${u.protocol}://${u.host}${if (u.port != -1 && u.port != 80 && u.port != 443) ":${u.port}" else ""}${if (location.startsWith("/")) "" else "/"}$location"
                        }
                        redirects++
                        continue
                    }
                }

                conn = c
                break
            }

            val finalConn = conn ?: return@withContext Result.failure(Exception("Failed to connect to download '${entry.title}'"))
            val responseCode = finalConn.responseCode
            if (responseCode !in 200..299) {
                updateProgress(entry.id, null)
                val err = finalConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                return@withContext Result.failure(Exception("Download failed with HTTP $responseCode: $err"))
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
                            updateProgress(entry.id, (downloadedBytes.toFloat() / totalLength).coerceIn(0.1f, 0.95f))
                        }
                    }
                }
            }

            updateProgress(entry.id, 1.0f)

            // Parse downloaded EPUB into Book model
            val fileUri = Uri.fromFile(targetFile)
            var parsedBook = EpubParser.parseEpub(context, fileUri)
                ?: Book(
                    id = bookId,
                    title = entry.title,
                    author = entry.author,
                    coverPattern = kotlin.math.abs(entry.title.hashCode() % 5),
                    coverImagePath = null,
                    progressPercent = 0,
                    currentChapterIndex = 0,
                    currentPageIndex = 0,
                    chapters = listOf(
                        com.fiksr.inkreader.model.Chapter(
                            id = "1",
                            title = "Chapter 1",
                            content = entry.summary.ifEmpty { "Downloaded from OPDS Catalog." }
                        )
                    ),
                    isCustomImported = true,
                    filePath = targetFile.absolutePath
                )

            // If parsed EPUB doesn't have an internal cover image, copy cached cover if available
            if (parsedBook.coverImagePath == null) {
                val coverUrl = entry.coverUrl ?: entry.thumbnailUrl
                if (!coverUrl.isNullOrBlank()) {
                    val coverCacheFile = File(File(context.cacheDir, "cover_cache"), "${hashUrl(coverUrl.trim())}.img")
                    if (coverCacheFile.exists() && coverCacheFile.length() > 0) {
                        val persistentCoversDir = File(context.filesDir, "covers").apply { mkdirs() }
                        val persistentCover = File(persistentCoversDir, "${parsedBook.id}.jpg")
                        try {
                            coverCacheFile.copyTo(persistentCover, overwrite = true)
                            parsedBook = parsedBook.copy(coverImagePath = persistentCover.absolutePath)
                        } catch (_: Exception) {}
                    }
                }
            }

            updateProgress(entry.id, null)
            Result.success(parsedBook.copy(filePath = targetFile.absolutePath))
        } catch (e: Exception) {
            updateProgress(entry.id, null)
            if (targetFile.exists()) targetFile.delete()
            Result.failure(e)
        }
    }

    private fun hashUrl(url: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val bytes = md.digest(url.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            url.replace(Regex("[^a-zA-Z0-9]"), "_").take(32)
        }
    }

    private fun updateProgress(entryId: String, progress: Float?) {
        val current = _downloadProgress.value.toMutableMap()
        if (progress == null) {
            current.remove(entryId)
        } else {
            current[entryId] = progress
        }
        _downloadProgress.value = current
    }
}
