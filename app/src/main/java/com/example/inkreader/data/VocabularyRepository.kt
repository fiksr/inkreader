package com.example.inkreader.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class VocabWord(
    val id: String = UUID.randomUUID().toString(),
    val word: String,
    val phonetic: String = "",
    val partOfSpeech: String = "word",
    val definition: String = "",
    val contextSentence: String = "",
    val bookTitle: String = "",
    val bookAuthor: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val masteryScore: Int = 0 // 0 = new, 1 = learning, 2 = mastered
)

class VocabularyRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("inkreader_vocab_prefs", Context.MODE_PRIVATE)

    private val _words = MutableStateFlow<List<VocabWord>>(loadWords())
    val words: StateFlow<List<VocabWord>> = _words.asStateFlow()

    private fun loadWords(): List<VocabWord> {
        val json = prefs.getString("saved_vocab_words_v1", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<VocabWord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    VocabWord(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        word = obj.optString("word"),
                        phonetic = obj.optString("phonetic"),
                        partOfSpeech = obj.optString("partOfSpeech", "word"),
                        definition = obj.optString("definition"),
                        contextSentence = obj.optString("contextSentence"),
                        bookTitle = obj.optString("bookTitle"),
                        bookAuthor = obj.optString("bookAuthor"),
                        addedAt = obj.optLong("addedAt", System.currentTimeMillis()),
                        masteryScore = obj.optInt("masteryScore", 0)
                    )
                )
            }
            list.sortedByDescending { it.addedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveWords(list: List<VocabWord>) {
        try {
            val arr = JSONArray()
            list.forEach { w ->
                val obj = JSONObject().apply {
                    put("id", w.id)
                    put("word", w.word)
                    put("phonetic", w.phonetic)
                    put("partOfSpeech", w.partOfSpeech)
                    put("definition", w.definition)
                    put("contextSentence", w.contextSentence)
                    put("bookTitle", w.bookTitle)
                    put("bookAuthor", w.bookAuthor)
                    put("addedAt", w.addedAt)
                    put("masteryScore", w.masteryScore)
                }
                arr.put(obj)
            }
            prefs.edit().putString("saved_vocab_words_v1", arr.toString()).apply()
            _words.value = list.sortedByDescending { it.addedAt }
        } catch (_: Exception) {}
    }

    fun addWord(
        word: String,
        phonetic: String = "",
        partOfSpeech: String = "word",
        definition: String,
        contextSentence: String = "",
        bookTitle: String = "",
        bookAuthor: String = ""
    ) {
        val cleanWord = word.trim()
        if (cleanWord.isBlank()) return

        val current = _words.value.toMutableList()
        // If word already exists from same book or general, update definition
        val existingIndex = current.indexOfFirst { it.word.equals(cleanWord, ignoreCase = true) }
        if (existingIndex >= 0) {
            val existing = current[existingIndex]
            current[existingIndex] = existing.copy(
                definition = definition.ifBlank { existing.definition },
                contextSentence = contextSentence.ifBlank { existing.contextSentence },
                phonetic = phonetic.ifBlank { existing.phonetic },
                addedAt = System.currentTimeMillis()
            )
        } else {
            current.add(
                0,
                VocabWord(
                    word = cleanWord,
                    phonetic = phonetic,
                    partOfSpeech = partOfSpeech,
                    definition = definition,
                    contextSentence = contextSentence,
                    bookTitle = bookTitle,
                    bookAuthor = bookAuthor
                )
            )
        }
        saveWords(current)
    }

    fun deleteWord(id: String) {
        val updated = _words.value.filter { it.id != id }
        saveWords(updated)
    }

    fun toggleMastery(id: String) {
        val updated = _words.value.map {
            if (it.id == id) it.copy(masteryScore = (it.masteryScore + 1) % 3) else it
        }
        saveWords(updated)
    }

    /**
     * Generates a standard CSV File (.csv) with quoted columns.
     */
    fun exportToCsv(): String {
        val sb = StringBuilder()
        sb.append("\"Word\",\"Phonetic\",\"PartOfSpeech\",\"Definition\",\"Context\",\"Book\",\"Author\"\n")
        _words.value.forEach { w ->
            val cleanSentence = w.contextSentence.replace("\"", "\"\"")
            val cleanDef = w.definition.replace("\"", "\"\"")
            val cleanWord = w.word.replace("\"", "\"\"")
            val cleanBook = w.bookTitle.replace("\"", "\"\"")
            val cleanAuthor = w.bookAuthor.replace("\"", "\"\"")
            sb.append("\"$cleanWord\",\"${w.phonetic}\",\"${w.partOfSpeech}\",\"$cleanDef\",\"$cleanSentence\",\"$cleanBook\",\"$cleanAuthor\"\n")
        }
        return sb.toString()
    }

    /**
     * Generates a standard Anki Tab-Separated File (.txt / .tsv) for import into Anki / AnkiDroid.
     */
    fun exportToAnkiTsv(): String {
        val sb = StringBuilder()
        sb.append("#tags:InkReader Vocabulary\n")
        sb.append("#columns:Word\tPhonetic\tPartOfSpeech\tDefinition\tSentence\tBook\n")
        _words.value.forEach { w ->
            val cleanSentence = w.contextSentence.replace("\t", " ").replace("\n", " ")
            val cleanDef = w.definition.replace("\t", " ").replace("\n", " ")
            sb.append("${w.word}\t${w.phonetic}\t${w.partOfSpeech}\t$cleanDef\t$cleanSentence\t${w.bookTitle}\n")
        }
        return sb.toString()
    }

    /**
     * Generates an Obsidian / Markdown document with callouts and YAML frontmatter.
     */
    fun exportToMarkdown(): String {
        val sb = StringBuilder()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sb.append("---\n")
        sb.append("title: InkReader Vocabulary Deck\n")
        sb.append("created: ${sdf.format(Date())}\n")
        sb.append("total_words: ${_words.value.size}\n")
        sb.append("tags:\n  - reading\n  - vocabulary\n  - inkreader\n")
        sb.append("---\n\n")
        sb.append("# 📖 InkReader Vocabulary & Words\n\n")

        _words.value.forEach { w ->
            sb.append("### **${w.word}** `${w.phonetic}` *(${w.partOfSpeech})*\n")
            sb.append("> **Definition:** ${w.definition}\n\n")
            if (w.contextSentence.isNotBlank()) {
                sb.append("> 💬 *\"${w.contextSentence}\"*\n")
            }
            if (w.bookTitle.isNotBlank()) {
                sb.append("— *${w.bookTitle}* ${if (w.bookAuthor.isNotBlank()) "by ${w.bookAuthor}" else ""}\n")
            }
            sb.append("\n---\n\n")
        }
        return sb.toString()
    }

    fun saveToDownloads(content: String, fileName: String, mimeType: String = "text/plain"): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { stream ->
                        stream.write(content.toByteArray(Charsets.UTF_8))
                    }
                    android.widget.Toast.makeText(context, "Saved to Downloads: $fileName", android.widget.Toast.LENGTH_LONG).show()
                    return true
                }
            }
            // Fallback for older Android or if MediaStore insert returns null
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val targetFile = File(downloadsDir, fileName)
            targetFile.writeText(content, Charsets.UTF_8)
            android.widget.Toast.makeText(context, "Saved to Downloads: ${targetFile.name}", android.widget.Toast.LENGTH_LONG).show()
            true
        } catch (e: Exception) {
            try {
                val fallbackDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val fallbackFile = File(fallbackDir, fileName)
                fallbackFile.writeText(content, Charsets.UTF_8)
                android.widget.Toast.makeText(context, "Saved to: ${fallbackFile.name}", android.widget.Toast.LENGTH_LONG).show()
                true
            } catch (ex: Exception) {
                android.widget.Toast.makeText(context, "Error saving file: ${ex.message}", android.widget.Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    fun shareExportFile(content: String, fileName: String, mimeType: String = "text/plain") {
        try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, fileName)
            file.writeText(content, Charsets.UTF_8)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "InkReader Export: $fileName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share $fileName").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Error sharing file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
