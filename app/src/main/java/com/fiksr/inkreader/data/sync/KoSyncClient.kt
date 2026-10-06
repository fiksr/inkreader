package com.fiksr.inkreader.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.fiksr.inkreader.data.NetworkUtils
import com.fiksr.inkreader.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

class KoSyncClient(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("inkreader_sync_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<SyncSettings> = _settings.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow<String?>("Ready")
    val syncStatusMessage: StateFlow<String?> = _syncStatusMessage.asStateFlow()

    private val deviceId: String by lazy {
        try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "inkreader_device"
        } catch (_: Exception) {
            "inkreader_device"
        }
    }

    private fun loadSettings(): SyncSettings {
        val savedUser = prefs.getString("username", "") ?: ""
        val savedKey = prefs.getString("user_key", "") ?: ""
        val defaultUser = if (savedUser.isBlank()) "Kindle" else savedUser
        val defaultKey = if (savedKey.isBlank()) "f751f8e54f4c34b398941489c6585a08" else savedKey

        return SyncSettings(
            serverUrl = prefs.getString("server_url", "https://buks.lol/api/v1/koreader") ?: "https://buks.lol/api/v1/koreader",
            username = defaultUser,
            userKey = defaultKey,
            deviceName = prefs.getString("device_name", "InkReader Android") ?: "InkReader Android",
            autoSyncOnOpen = prefs.getBoolean("auto_sync_open", true),
            autoSyncOnClose = prefs.getBoolean("auto_sync_close", true),
            lastSyncTimestamp = prefs.getLong("last_sync_time", 0L)
        )
    }

    fun saveSettings(
        serverUrl: String,
        username: String,
        userKey: String,
        deviceName: String,
        autoSyncOnOpen: Boolean,
        autoSyncOnClose: Boolean
    ) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        prefs.edit()
            .putString("server_url", cleanUrl)
            .putString("username", username.trim())
            .putString("user_key", userKey.trim())
            .putString("device_name", deviceName.trim())
            .putBoolean("auto_sync_open", autoSyncOnOpen)
            .putBoolean("auto_sync_close", autoSyncOnClose)
            .apply()

        _settings.value = SyncSettings(
            serverUrl = cleanUrl,
            username = username.trim(),
            userKey = userKey.trim(),
            deviceName = deviceName.trim(),
            autoSyncOnOpen = autoSyncOnOpen,
            autoSyncOnClose = autoSyncOnClose,
            lastSyncTimestamp = _settings.value.lastSyncTimestamp
        )
    }

    fun updateLastSyncTime(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("last_sync_time", timestamp).apply()
        _settings.value = _settings.value.copy(lastSyncTimestamp = timestamp)
    }

    fun isConfigured(): Boolean {
        val s = _settings.value
        return s.serverUrl.isNotBlank() && s.username.isNotBlank() && s.userKey.isNotBlank()
    }

    private fun getEffectiveEndpoint(subPath: String): String {
        val raw = _settings.value.serverUrl.trim().trimEnd('/')
        val cleanSub = subPath.trimStart('/')
        val base = when {
            raw.isEmpty() -> ""
            raw.endsWith("/api/v1/koreader") -> raw
            raw.contains("sync.koreader.rocks") -> raw
            else -> "$raw/api/v1/koreader"
        }
        return "$base/$cleanSub"
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val s = _settings.value
        if (s.serverUrl.isBlank()) return@withContext Result.failure(Exception("Server URL cannot be empty"))
        if (s.username.isBlank()) return@withContext Result.failure(Exception("Username cannot be empty"))
        if (s.userKey.isBlank()) return@withContext Result.failure(Exception("User key / password cannot be empty"))

        try {
            val fullUrl = getEffectiveEndpoint("users/auth")
            _syncStatusMessage.value = "Testing link to $fullUrl..."

            val url = URL(fullUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                if (this is HttpsURLConnection) {
                    NetworkUtils.configureSsl(this, acceptCustomCerts = true)
                }
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "KOReader/2024.04 (Android)")
                setRequestProperty("x-auth-user", s.username)
                setRequestProperty("x-auth-key", s.userKey)
                setRequestProperty("Accept", "application/json")
            }

            val code = conn.responseCode
            if (code in 200..299) {
                _syncStatusMessage.value = "Connected as ${s.username}"
                Result.success("Authentication successful! Connected as ${s.username}")
            } else if (code == 401 || code == 403) {
                val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                _syncStatusMessage.value = "Invalid credentials ($code)"
                Result.failure(Exception("Invalid credentials: ${errBody.ifEmpty { "Check username and password/key (Use KOReader credentials from BookOrbit Settings > KOReader)" }}"))
            } else {
                val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                _syncStatusMessage.value = "Server returned HTTP $code"
                Result.failure(Exception("HTTP $code: $errorMsg"))
            }
        } catch (e: Exception) {
            _syncStatusMessage.value = "Connection error: ${e.message}"
            Result.failure(Exception("Could not connect: ${e.localizedMessage ?: e.message}"))
        }
    }

    suspend fun registerAccount(serverUrl: String, username: String, passKey: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val fullUrl = getEffectiveEndpoint("users/create")
                _syncStatusMessage.value = "Registering on $fullUrl..."

                val url = URL(fullUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    if (this is HttpsURLConnection) {
                        NetworkUtils.configureSsl(this, acceptCustomCerts = true)
                    }
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("User-Agent", "KOReader/2024.04 (Android)")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }

                val body = JSONObject().apply {
                    put("username", username)
                    put("password", passKey)
                }

                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

                val code = conn.responseCode
                if (code in 200..299) {
                    _syncStatusMessage.value = "Account created for $username"
                    Result.success("Account registered successfully!")
                } else {
                    val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    _syncStatusMessage.value = "Registration failed (HTTP $code)"
                    Result.failure(Exception("Registration failed (HTTP $code): $errorMsg"))
                }
            } catch (e: Exception) {
                _syncStatusMessage.value = "Registration error: ${e.message}"
                Result.failure(e)
            }
        }

    fun computeDocumentHash(book: Book): String {
        if (book.filePath != null) {
            try {
                val file = File(book.filePath)
                if (file.exists() && file.length() > 0) {
                    val md = MessageDigest.getInstance("MD5")
                    FileInputStream(file).use { fis ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (fis.read(buffer).also { read = it } != -1) {
                            md.update(buffer, 0, read)
                        }
                    }
                    return md.digest().joinToString("") { "%02x".format(it) }
                }
            } catch (_: Exception) {}
        }

        val identifier = "${book.title.trim().lowercase()}::${book.author.trim().lowercase()}"
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(identifier.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun pushProgress(book: Book): Result<Boolean> = withContext(Dispatchers.IO) {
        val s = _settings.value
        if (!isConfigured()) return@withContext Result.failure(Exception("Sync not configured"))

        val docHash = computeDocumentHash(book)
        val nowSec = System.currentTimeMillis() / 1000L

        try {
            val fullUrl = getEffectiveEndpoint("syncs/progress")

            _syncStatusMessage.value = "Pushing progress for '${book.title}'..."
            val url = URL(fullUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                if (this is HttpsURLConnection) {
                    NetworkUtils.configureSsl(this, acceptCustomCerts = true)
                }
                requestMethod = "PUT"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("User-Agent", "KOReader/2024.04 (Android)")
                setRequestProperty("x-auth-user", s.username)
                setRequestProperty("x-auth-key", s.userKey)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val progressFloat = (book.progressPercent / 100.0).coerceIn(0.0, 1.0)
            val body = JSONObject().apply {
                put("document", docHash)
                put("progress", progressFloat)
                put("percentage", book.progressPercent)
                put("device", s.deviceName)
                put("device_id", deviceId)
                put("timestamp", nowSec)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            val code = conn.responseCode
            if (code in 200..299) {
                updateLastSyncTime()
                _syncStatusMessage.value = "Synced '${book.title}' (${book.progressPercent}%)"
                Result.success(true)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                _syncStatusMessage.value = "Push failed (HTTP $code)"
                Result.failure(Exception("HTTP $code: $err"))
            }
        } catch (e: Exception) {
            _syncStatusMessage.value = "Push failed: ${e.message}"
            Result.failure(e)
        }
    }

    suspend fun pullProgress(book: Book): Result<SyncProgress?> = withContext(Dispatchers.IO) {
        val s = _settings.value
        if (!isConfigured()) return@withContext Result.failure(Exception("Sync not configured"))

        val docHash = computeDocumentHash(book)

        try {
            val fullUrl = getEffectiveEndpoint("syncs/progress/$docHash")

            _syncStatusMessage.value = "Checking remote progress for '${book.title}'..."
            val url = URL(fullUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                if (this is HttpsURLConnection) {
                    NetworkUtils.configureSsl(this, acceptCustomCerts = true)
                }
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "KOReader/2024.04 (Android)")
                setRequestProperty("x-auth-user", s.username)
                setRequestProperty("x-auth-key", s.userKey)
                setRequestProperty("Accept", "application/json")
            }

            val code = conn.responseCode
            if (code == 200) {
                val responseText = InputStreamReader(conn.inputStream).use { it.readText() }
                val json = JSONObject(responseText)
                val progressFloat = json.optDouble("progress", 0.0)
                val percentage = if (json.has("percentage")) {
                    json.getInt("percentage")
                } else {
                    (progressFloat * 100).toInt().coerceIn(0, 100)
                }
                val device = json.optString("device", "Unknown Device")
                val devId = json.optString("device_id", "")
                val timestamp = json.optLong("timestamp", 0L)

                val totalChapters = book.chapters.size.coerceAtLeast(1)
                val calculatedChapter = ((percentage / 100f) * totalChapters).toInt().coerceIn(0, totalChapters - 1)

                val syncData = SyncProgress(
                    documentHash = docHash,
                    progressPercent = percentage,
                    chapterIndex = calculatedChapter,
                    pageIndex = 0,
                    device = device,
                    deviceId = devId,
                    timestamp = timestamp
                )
                updateLastSyncTime()
                _syncStatusMessage.value = "Fetched remote progress: $percentage%"
                Result.success(syncData)
            } else if (code == 404) {
                _syncStatusMessage.value = "No remote progress found for '${book.title}'"
                Result.success(null)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                _syncStatusMessage.value = "Pull failed (HTTP $code)"
                Result.failure(Exception("HTTP $code: $err"))
            }
        } catch (e: Exception) {
            _syncStatusMessage.value = "Pull failed: ${e.message}"
            Result.failure(e)
        }
    }
}
