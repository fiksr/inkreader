package com.fiksr.inkreader.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.fiksr.inkreader.data.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

object CoverImageLoader {
    private val memoryCache = object : LruCache<String, Bitmap>(100) {}

    private fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "cover_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun hashUrl(url: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(url.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            url.replace(Regex("[^a-zA-Z0-9]"), "_").take(32)
        }
    }

    private fun decodeSampledBitmap(data: ByteArray, reqWidth: Int = 400, reqHeight: Int = 600): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
        } catch (_: Exception) {
            try {
                BitmapFactory.decodeByteArray(data, 0, data.size)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    suspend fun loadBitmap(
        context: Context,
        urlStr: String?,
        username: String = "",
        password: String = "",
        xAuthUser: String = "",
        xAuthKey: String = ""
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (urlStr.isNullOrBlank()) return@withContext null

        val rawUrl = urlStr.trim()
        val isHttp = rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true)

        // If it's a relative URL or starts with /api or /opds or contains thumbnail/cover
        val isRelativeWeb = !isHttp && (rawUrl.startsWith("/") || rawUrl.startsWith("api/") || rawUrl.contains("thumbnail") || rawUrl.contains("cover") || rawUrl.contains("koreader"))

        val cleanUrl = if (isRelativeWeb && !File(rawUrl).exists()) {
            val path = if (rawUrl.startsWith("/")) rawUrl else "/$rawUrl"
            "https://buks.lol$path"
        } else {
            rawUrl
        }

        // 1. Check local file if not http
        if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
            val file = File(cleanUrl)
            if (file.exists() && file.length() > 0) {
                return@withContext try {
                    BitmapFactory.decodeFile(cleanUrl)
                } catch (_: Exception) { null }
            }
            return@withContext null
        }

        // 2. Check in-memory cache
        memoryCache.get(cleanUrl)?.let { return@withContext it }

        // 3. Check disk cache
        val cacheFile = File(getCacheDir(context), "${hashUrl(cleanUrl)}.img")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            try {
                val bmp = BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (bmp != null) {
                    memoryCache.put(cleanUrl, bmp)
                    return@withContext bmp
                }
            } catch (_: Exception) {}
        }

        // 4. Download from network with authentication and redirect handling
        val effKoUser = when {
            xAuthUser.isNotBlank() -> xAuthUser.trim()
            username.isNotBlank() && username != "Kindle" -> username.trim()
            else -> "Kindle"
        }
        val effKoKey = when {
            xAuthKey.isNotBlank() -> xAuthKey.trim()
            password.isNotBlank() && password != "f751f8e54f4c34b398941489c6585a08" -> password.trim()
            else -> "f751f8e54f4c34b398941489c6585a08"
        }

        val effBasicUser = username.ifBlank { xAuthUser }.trim()
        val effBasicKey = password.ifBlank { xAuthKey }.trim()

        var currentUrl = cleanUrl
        var redirects = 0
        while (redirects < 5) {
            try {
                val url = URL(currentUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    if (this is HttpsURLConnection) {
                        NetworkUtils.configureSsl(this, acceptCustomCerts = true)
                    }
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "KOReader/2024.04 (Android; E-Ink)")
                    setRequestProperty("Accept", "image/webp,image/jpeg,image/png,image/*;q=0.8")

                    // Add HTTP Basic Auth if available
                    if (effBasicUser.isNotBlank() || effBasicKey.isNotBlank()) {
                        val auth = "$effBasicUser:$effBasicKey"
                        val encoded = Base64.encodeToString(auth.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                        setRequestProperty("Authorization", "Basic $encoded")
                    }

                    // Add KOReader custom auth headers
                    if (effKoUser.isNotBlank()) setRequestProperty("x-auth-user", effKoUser)
                    if (effKoKey.isNotBlank()) setRequestProperty("x-auth-key", effKoKey)
                }

                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
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

                if (code in 200..299) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (bytes.isNotEmpty()) {
                        // Save to disk cache
                        try {
                            FileOutputStream(cacheFile).use { it.write(bytes) }
                        } catch (_: Exception) {}

                        val bmp = decodeSampledBitmap(bytes)
                        if (bmp != null) {
                            memoryCache.put(cleanUrl, bmp)
                            return@withContext bmp
                        }
                    }
                }
                break
            } catch (_: Exception) {
                break
            }
        }

        return@withContext null
    }
}

@Composable
fun rememberCoverBitmap(
    url: String?,
    username: String = "",
    password: String = "",
    xAuthUser: String = "",
    xAuthKey: String = ""
): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(url, username, password, xAuthUser, xAuthKey) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(url, username, password, xAuthUser, xAuthKey) {
        if (url != null) {
            bitmap = CoverImageLoader.loadBitmap(context, url, username, password, xAuthUser, xAuthKey)
        } else {
            bitmap = null
        }
    }

    return bitmap
}
