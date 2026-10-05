package com.example.inkreader.audio.kokoro

import android.content.Context
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

sealed class KokoroDownloadState {
    object Idle : KokoroDownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : KokoroDownloadState()
    object Extracting : KokoroDownloadState()
    object Ready : KokoroDownloadState()
    data class Error(val message: String) : KokoroDownloadState()
}

class KokoroModelManager(private val context: Context) {

    companion object {
        const val MODEL_DIR_NAME = "kokoro_model"
        private const val MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2"
        private const val BACKUP_MODEL_URL = "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/"
    }

    val modelDir: File
        get() {
            val internal = File(context.filesDir, MODEL_DIR_NAME)
            if (isValidDir(internal)) return internal
            val external = context.getExternalFilesDir(null)?.resolve(MODEL_DIR_NAME)
            if (external != null && isValidDir(external)) return external
            return internal
        }

    private val _downloadState = MutableStateFlow<KokoroDownloadState>(if (isModelReady()) KokoroDownloadState.Ready else KokoroDownloadState.Idle)
    val downloadState: StateFlow<KokoroDownloadState> = _downloadState.asStateFlow()

    private fun isValidDir(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        val modelFile = File(dir, "model.int8.onnx")
        val voicesFile = File(dir, "voices.bin")
        val tokensFile = File(dir, "tokens.txt")
        val espeakDir = File(dir, "espeak-ng-data")
        return modelFile.exists() && modelFile.length() > 10_000_000 &&
                voicesFile.exists() && voicesFile.length() > 1_000_000 &&
                tokensFile.exists() &&
                espeakDir.exists() && espeakDir.isDirectory
    }

    fun isModelReady(): Boolean {
        return isValidDir(modelDir)
    }

    fun getModelDirectory(): File? {
        return if (isModelReady()) modelDir else null
    }

    fun getModelSizeFormatted(): String {
        val dir = modelDir
        if (!dir.exists()) return "0 MB"
        val bytes = dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        return "${bytes / (1024 * 1024)} MB"
    }

    suspend fun downloadAndInstall(
        onProgress: ((Float) -> Unit)? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (isModelReady()) {
            _downloadState.value = KokoroDownloadState.Ready
            onComplete?.invoke(true)
            return@withContext true
        }

        val tempArchive = File(context.cacheDir, "kokoro_temp.tar.bz2")
        try {
            _downloadState.value = KokoroDownloadState.Downloading(0f, 0, 0)
            
            // 1. Download tar.bz2 archive with progress
            val url = URL(MODEL_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "InkReader/1.0")
            }

            connection.connect()
            val totalBytes = connection.contentLength.toLong().let { if (it > 0) it else 132_300_000L }

            var downloaded = 0L
            connection.inputStream.use { input ->
                FileOutputStream(tempArchive).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var lastProgressUpdate = 0L

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        val progress = (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 100) {
                            lastProgressUpdate = now
                            _downloadState.value = KokoroDownloadState.Downloading(progress, downloaded, totalBytes)
                            onProgress?.invoke(progress)
                        }
                    }
                }
            }

            // 2. Extract tar.bz2 into filesDir/kokoro_model
            _downloadState.value = KokoroDownloadState.Extracting
            if (modelDir.exists()) modelDir.deleteRecursively()
            modelDir.mkdirs()

            extractTarBz2(tempArchive, modelDir)

            // If files extracted into a subfolder (e.g. kokoro-int8-multi-lang-v1_0/...), move them up
            flattenDirectory(modelDir)

            tempArchive.delete()

            if (isModelReady()) {
                _downloadState.value = KokoroDownloadState.Ready
                onComplete?.invoke(true)
                true
            } else {
                _downloadState.value = KokoroDownloadState.Error("Extraction completed but model files missing")
                onComplete?.invoke(false)
                false
            }
        } catch (e: Exception) {
            tempArchive.delete()
            _downloadState.value = KokoroDownloadState.Error(e.message ?: "Download failed")
            onComplete?.invoke(false)
            false
        }
    }

    private fun extractTarBz2(archiveFile: File, outputDir: File) {
        BufferedInputStream(archiveFile.inputStream()).use { bis ->
            BZip2CompressorInputStream(bis).use { bzIn ->
                TarArchiveInputStream(bzIn).use { tarIn ->
                    var entry: TarArchiveEntry? = tarIn.nextTarEntry
                    val buffer = ByteArray(64 * 1024)

                    while (entry != null) {
                        val outputFile = File(outputDir, entry.name)
                        if (entry.isDirectory) {
                            outputFile.mkdirs()
                        } else {
                            outputFile.parentFile?.mkdirs()
                            FileOutputStream(outputFile).use { fos ->
                                var count: Int
                                while (tarIn.read(buffer).also { count = it } != -1) {
                                    fos.write(buffer, 0, count)
                                }
                            }
                        }
                        entry = tarIn.nextTarEntry
                    }
                }
            }
        }
    }

    private fun flattenDirectory(root: File) {
        val children = root.listFiles() ?: return
        // If there is only one subdirectory, move all its contents up to root
        if (children.size == 1 && children[0].isDirectory) {
            val subDir = children[0]
            subDir.listFiles()?.forEach { file ->
                val dest = File(root, file.name)
                file.renameTo(dest)
            }
            subDir.delete()
        }
    }

    fun deleteModel(): Boolean {
        return try {
            val result = if (modelDir.exists()) modelDir.deleteRecursively() else true
            _downloadState.value = KokoroDownloadState.Idle
            result
        } catch (_: Exception) {
            false
        }
    }
}
