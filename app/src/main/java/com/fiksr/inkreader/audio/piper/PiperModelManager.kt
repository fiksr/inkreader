package com.fiksr.inkreader.audio.piper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "PiperModelManager"

sealed class PiperDownloadState {
    object Idle : PiperDownloadState()
    data class Downloading(val voiceId: String, val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : PiperDownloadState()
    data class Extracting(val voiceId: String) : PiperDownloadState()
    data class Ready(val voiceId: String) : PiperDownloadState()
    data class Error(val voiceId: String, val message: String) : PiperDownloadState()
}

class PiperModelManager(private val context: Context) {

    private val baseDir: File
        get() {
            val dir = File(context.filesDir, "piper_models")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private val _downloadState = MutableStateFlow<PiperDownloadState>(PiperDownloadState.Idle)
    val downloadState: StateFlow<PiperDownloadState> = _downloadState.asStateFlow()

    fun getVoiceDir(voice: PiperVoice): File {
        return File(baseDir, voice.id)
    }

    fun isVoiceReady(voice: PiperVoice): Boolean {
        val dir = getVoiceDir(voice)
        if (!dir.exists() || !dir.isDirectory) return false
        val onnxFile = File(dir, voice.modelFileName)
        val tokensFile = File(dir, "tokens.txt")
        val espeakDir = File(dir, "espeak-ng-data")
        return onnxFile.exists() && onnxFile.length() > 5_000_000 &&
                tokensFile.exists() &&
                espeakDir.exists() && espeakDir.isDirectory
    }

    fun getDownloadedVoices(): List<PiperVoice> {
        return PiperVoiceCatalog.ALL_VOICES.filter { isVoiceReady(it) }
    }

    fun getVoiceSizeFormatted(voice: PiperVoice): String {
        val dir = getVoiceDir(voice)
        if (!dir.exists()) return "0 MB"
        val bytes = dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        return "${bytes / (1024 * 1024)} MB"
    }

    suspend fun downloadAndInstall(
        voice: PiperVoice,
        onProgress: ((Float) -> Unit)? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (isVoiceReady(voice)) {
            _downloadState.value = PiperDownloadState.Ready(voice.id)
            onComplete?.invoke(true)
            return@withContext true
        }

        val targetDir = getVoiceDir(voice)
        val tempArchive = File(context.cacheDir, "piper_${voice.id}.tar.bz2")

        try {
            _downloadState.value = PiperDownloadState.Downloading(voice.id, 0f, 0, 0)
            Log.d(TAG, "Starting download of Piper voice '${voice.name}' from ${voice.downloadUrl}")

            // 1. Download tar.bz2 archive with progress
            val url = URL(voice.downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "InkReader/1.0")
            }

            connection.connect()
            val totalBytes = connection.contentLength.toLong().let { 
                if (it > 0) it else voice.sizeMb * 1024L * 1024L 
            }

            var downloaded = 0L
            connection.inputStream.use { input ->
                FileOutputStream(tempArchive).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    var lastReport = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 100) {
                            lastReport = now
                            val progress = (downloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                            _downloadState.value = PiperDownloadState.Downloading(voice.id, progress, downloaded, totalBytes)
                            onProgress?.invoke(progress)
                        }
                    }
                }
            }

            // 2. Extract tar.bz2
            _downloadState.value = PiperDownloadState.Extracting(voice.id)
            Log.d(TAG, "Extracting Piper voice to ${targetDir.absolutePath}")
            targetDir.mkdirs()

            BufferedInputStream(tempArchive.inputStream()).use { bis ->
                BZip2CompressorInputStream(bis).use { bzIn ->
                    TarArchiveInputStream(bzIn).use { tarIn ->
                        var entry: TarArchiveEntry? = tarIn.nextTarEntry
                        while (entry != null) {
                            val entryName = entry.name
                            // Normalize relative paths inside archive (e.g. "vits-piper-en_US-lessac-medium/...")
                            val relativePath = if (entryName.startsWith(voice.archiveFolder + "/")) {
                                entryName.substring(voice.archiveFolder.length + 1)
                            } else if (entryName.contains("/")) {
                                entryName.substringAfter("/")
                            } else {
                                entryName
                            }

                            if (relativePath.isNotBlank()) {
                                val destFile = File(targetDir, relativePath)
                                if (entry.isDirectory) {
                                    destFile.mkdirs()
                                } else {
                                    destFile.parentFile?.mkdirs()
                                    FileOutputStream(destFile).use { out ->
                                        tarIn.copyTo(out)
                                    }
                                }
                            }
                            entry = tarIn.nextTarEntry
                        }
                    }
                }
            }

            // Delete temporary download archive
            try { tempArchive.delete() } catch (_: Exception) {}

            val ready = isVoiceReady(voice)
            if (ready) {
                _downloadState.value = PiperDownloadState.Ready(voice.id)
                Log.d(TAG, "Piper voice '${voice.name}' installed successfully!")
                onComplete?.invoke(true)
                true
            } else {
                _downloadState.value = PiperDownloadState.Error(voice.id, "Extracted files incomplete")
                Log.e(TAG, "Piper voice extraction incomplete for '${voice.name}'")
                onComplete?.invoke(false)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading Piper voice '${voice.name}'", e)
            try { tempArchive.delete() } catch (_: Exception) {}
            _downloadState.value = PiperDownloadState.Error(voice.id, e.localizedMessage ?: "Download failed")
            onComplete?.invoke(false)
            false
        }
    }

    fun deleteVoice(voice: PiperVoice): Boolean {
        val dir = getVoiceDir(voice)
        return if (dir.exists()) {
            dir.deleteRecursively()
        } else false
    }
}
