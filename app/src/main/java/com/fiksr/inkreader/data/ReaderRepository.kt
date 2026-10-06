package com.fiksr.inkreader.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.fiksr.inkreader.audio.AmbientAudioEngine
import com.fiksr.inkreader.data.opds.OpdsRepository
import com.fiksr.inkreader.data.sync.KoSyncClient
import com.fiksr.inkreader.data.sync.SyncProgress
import com.fiksr.inkreader.model.Book
import com.fiksr.inkreader.model.Bookmark
import com.fiksr.inkreader.model.Highlight
import com.fiksr.inkreader.theme.BookFontOption
import com.fiksr.inkreader.theme.EInkThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import android.provider.DocumentsContract
import com.fiksr.inkreader.theme.CustomFontManager
import org.json.JSONObject
import java.io.File

data class ReaderSettings(
    val fontSizeSp: Float = 17f,
    val lineSpacingMultiplier: Float = 1.50f,
    val fontOption: BookFontOption = BookFontOption.SERIF,
    val selectedFontId: String = "serif",
    val themeMode: EInkThemeMode = EInkThemeMode.PAPER,
    val pageFlashEnabled: Boolean = false,
    val ditherImages: Boolean = true,
    val focusMode: Boolean = false,
    val noAnimations: Boolean = true,
    val showCoversInLibrary: Boolean = true,
    val isLibraryGridView: Boolean = false,
    val libraryGridColumns: Int = 4,
    val usePublisherFormatting: Boolean = false,
    val footerShowChapterPages: Boolean = true,
    val footerShowBookPages: Boolean = true,
    val footerShowChapterTime: Boolean = true,
    val footerShowBookTime: Boolean = true,
    val bionicReadingEnabled: Boolean = false,
    val paragraphIndentEnabled: Boolean = true
)

class ReaderRepository(private val context: Context) {

    val audioEngine = AmbientAudioEngine(context)
    val syncClient = KoSyncClient(context)
    val opdsRepository = OpdsRepository(context)
    val bookOrbitClient = com.fiksr.inkreader.data.bookorbit.BookOrbitClient(context, syncClient)
    val aiCompanionService = AiCompanionService(context)
    val vocabularyRepository = VocabularyRepository(context)
    val statsRepository = ReadingStatsRepository(context)
    val ttsEngine = com.fiksr.inkreader.audio.TtsEngine(context)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("inkreader_library_prefs", Context.MODE_PRIVATE)

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _volumePageTurnEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val volumePageTurnEvents: SharedFlow<Boolean> = _volumePageTurnEvents.asSharedFlow()

    fun triggerVolumePageTurn(next: Boolean) {
        _volumePageTurnEvents.tryEmit(next)
    }

    private val _books = MutableStateFlow<List<Book>>(loadSavedBooks())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _currentBook = MutableStateFlow<Book?>(_books.value.firstOrNull())
    val currentBook: StateFlow<Book?> = _currentBook.asStateFlow()

    private val _settings = MutableStateFlow(loadSavedSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(loadBookmarks())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _highlights = MutableStateFlow<List<Highlight>>(loadHighlights())
    val highlights: StateFlow<List<Highlight>> = _highlights.asStateFlow()

    private fun loadSavedSettings(): ReaderSettings {
        val json = prefs.getString("reader_settings_v1", null) ?: return ReaderSettings()
        return try {
            val obj = JSONObject(json)
            val fontName = obj.optString("fontOption", BookFontOption.SERIF.name)
            val fontOpt = try { BookFontOption.valueOf(fontName) } catch (_: Exception) { BookFontOption.SERIF }
            val themeName = obj.optString("themeMode", EInkThemeMode.PAPER.name)
            val themeMod = try { EInkThemeMode.valueOf(themeName) } catch (_: Exception) { EInkThemeMode.PAPER }

            ReaderSettings(
                fontSizeSp = obj.optDouble("fontSizeSp", 17.0).toFloat(),
                lineSpacingMultiplier = obj.optDouble("lineSpacingMultiplier", 1.50).toFloat(),
                fontOption = fontOpt,
                selectedFontId = obj.optString("selectedFontId", "serif"),
                themeMode = themeMod,
                pageFlashEnabled = obj.optBoolean("pageFlashEnabled", false),
                ditherImages = obj.optBoolean("ditherImages", true),
                focusMode = obj.optBoolean("focusMode", false),
                noAnimations = obj.optBoolean("noAnimations", true),
                showCoversInLibrary = obj.optBoolean("showCoversInLibrary", true),
                isLibraryGridView = obj.optBoolean("isLibraryGridView", false),
                libraryGridColumns = obj.optInt("libraryGridColumns", 4),
                usePublisherFormatting = obj.optBoolean("usePublisherFormatting", false),
                footerShowChapterPages = obj.optBoolean("footerShowChapterPages", true),
                footerShowBookPages = obj.optBoolean("footerShowBookPages", true),
                footerShowChapterTime = obj.optBoolean("footerShowChapterTime", true),
                footerShowBookTime = obj.optBoolean("footerShowBookTime", true),
                bionicReadingEnabled = obj.optBoolean("bionicReadingEnabled", false),
                paragraphIndentEnabled = obj.optBoolean("paragraphIndentEnabled", true)
            )
        } catch (_: Exception) {
            ReaderSettings()
        }
    }

    private fun saveSavedSettings(s: ReaderSettings) {
        try {
            val obj = JSONObject().apply {
                put("fontSizeSp", s.fontSizeSp.toDouble())
                put("lineSpacingMultiplier", s.lineSpacingMultiplier.toDouble())
                put("fontOption", s.fontOption.name)
                put("selectedFontId", s.selectedFontId)
                put("themeMode", s.themeMode.name)
                put("pageFlashEnabled", s.pageFlashEnabled)
                put("ditherImages", s.ditherImages)
                put("focusMode", s.focusMode)
                put("noAnimations", s.noAnimations)
                put("showCoversInLibrary", s.showCoversInLibrary)
                put("isLibraryGridView", s.isLibraryGridView)
                put("libraryGridColumns", s.libraryGridColumns)
                put("usePublisherFormatting", s.usePublisherFormatting)
                put("footerShowChapterPages", s.footerShowChapterPages)
                put("footerShowBookPages", s.footerShowBookPages)
                put("footerShowChapterTime", s.footerShowChapterTime)
                put("footerShowBookTime", s.footerShowBookTime)
                put("bionicReadingEnabled", s.bionicReadingEnabled)
                put("paragraphIndentEnabled", s.paragraphIndentEnabled)
            }
            prefs.edit().putString("reader_settings_v1", obj.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun loadSavedBooks(): List<Book> {
        val deletedDefaults = prefs.getStringSet("deleted_default_books", emptySet()) ?: emptySet()
        val initial = DefaultLibrary.getInitialBooks()
            .filter { it.id !in deletedDefaults }
            .toMutableList()

        val customJson = prefs.getString("custom_books_meta", null)
        if (customJson != null) {
            try {
                val array = JSONArray(customJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val filePath = obj.optString("filePath", "")
                    val file = if (filePath.isNotEmpty()) File(filePath) else null
                    if (file != null && file.exists()) {
                        val parsed = EpubParser.parseEpub(context, Uri.fromFile(file))
                        if (parsed != null) {
                            val restored = parsed.copy(
                                id = obj.getString("id"),
                                progressPercent = obj.optInt("progressPercent", 0),
                                currentChapterIndex = obj.optInt("currentChapterIndex", 0),
                                currentPageIndex = obj.optInt("currentPageIndex", 0)
                            )
                            initial.add(0, restored)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return initial
    }

    private fun saveCustomBooks(list: List<Book>) {
        val customBooks = list.filter { it.isCustomImported }
        val array = JSONArray()
        for (b in customBooks) {
            val obj = JSONObject().apply {
                put("id", b.id)
                put("title", b.title)
                put("author", b.author)
                put("progressPercent", b.progressPercent)
                put("currentChapterIndex", b.currentChapterIndex)
                put("currentPageIndex", b.currentPageIndex)
                put("filePath", b.filePath ?: "")
            }
            array.put(obj)
        }
        prefs.edit().putString("custom_books_meta", array.toString()).apply()
    }

    fun selectBook(bookId: String) {
        val found = _books.value.find { it.id == bookId }
        if (found != null) {
            _currentBook.value = found

            // Auto sync check on open
            if (syncClient.settings.value.autoSyncOnOpen && syncClient.isConfigured()) {
                scope.launch {
                    val result = syncClient.pullProgress(found)
                    result.onSuccess { remoteProgress ->
                        if (remoteProgress != null && remoteProgress.progressPercent > found.progressPercent) {
                            jumpToChapter(found.id, remoteProgress.chapterIndex)
                        }
                    }
                }
            }
        }
    }

    fun jumpToChapter(bookId: String, chapterIndex: Int) {
        val currentList = _books.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == bookId }
        if (index != -1) {
            val oldBook = currentList[index]
            val safeChapter = chapterIndex.coerceIn(0, (oldBook.chapters.size - 1).coerceAtLeast(0))
            val updatedBook = oldBook.copy(
                currentChapterIndex = safeChapter,
                currentPageIndex = 0
            )
            currentList[index] = updatedBook
            _books.value = currentList
            saveCustomBooks(currentList)
            if (_currentBook.value?.id == bookId) {
                _currentBook.value = updatedBook
            }
        }
    }

    fun updateProgress(bookId: String, chapterIndex: Int, pageIndex: Int, totalPagesInChapter: Int) {
        val currentList = _books.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == bookId }
        if (index != -1) {
            val oldBook = currentList[index]
            val totalChapters = oldBook.chapters.size.coerceAtLeast(1)
            val chapterPercent = (chapterIndex.toFloat() / totalChapters) * 100
            val inChapterPercent = ((pageIndex.toFloat() / totalPagesInChapter.coerceAtLeast(1)) / totalChapters) * 100
            val totalPercent = (chapterPercent + inChapterPercent).toInt().coerceIn(0, 100)

            val updatedBook = oldBook.copy(
                currentChapterIndex = chapterIndex,
                currentPageIndex = pageIndex,
                progressPercent = totalPercent
            )
            currentList[index] = updatedBook
            _books.value = currentList
            saveCustomBooks(currentList)
            if (_currentBook.value?.id == bookId) {
                _currentBook.value = updatedBook
            }

            // Trigger KOReader / BookOrbit background push
            if (syncClient.settings.value.autoSyncOnClose && syncClient.isConfigured()) {
                scope.launch {
                    syncClient.pushProgress(updatedBook)
                }
            }
        }
    }

    fun importEpub(uri: Uri): Book? {
        val book = EpubParser.parseEpub(context, uri)
        if (book != null) {
            val list = _books.value.toMutableList()
            list.removeAll { it.id == book.id || (it.title == book.title && it.author == book.author) }
            list.add(0, book)
            _books.value = list
            _currentBook.value = book
            saveCustomBooks(list)
            return book
        }
        return null
    }

    fun importEpubsFromTreeUri(treeUri: Uri): Int {
        var count = 0
        try {
            val contentResolver = context.contentResolver
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val queue = ArrayDeque<String>()
            queue.add(treeDocId)

            while (queue.isNotEmpty()) {
                val parentDocId = queue.removeFirst()
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )
                contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(idIdx)
                        val displayName = cursor.getString(nameIdx) ?: ""
                        val mimeType = cursor.getString(mimeIdx) ?: ""

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.add(docId)
                        } else if (displayName.endsWith(".epub", ignoreCase = true) || mimeType == "application/epub+zip") {
                            try {
                                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                                val book = importEpub(docUri)
                                if (book != null) {
                                    count++
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return count
    }

    fun addBook(book: Book) {
        val list = _books.value.toMutableList()
        list.removeAll { it.id == book.id || (it.title == book.title && it.author == book.author) }
        list.add(0, book)
        _books.value = list
        saveCustomBooks(list)
    }

    fun deleteBook(bookId: String) {
        val currentList = _books.value.toMutableList()
        val book = currentList.find { it.id == bookId }
        if (book != null) {
            if (!book.isCustomImported) {
                val deleted = prefs.getStringSet("deleted_default_books", emptySet())?.toMutableSet() ?: mutableSetOf()
                deleted.add(bookId)
                prefs.edit().putStringSet("deleted_default_books", deleted).apply()
            }
            if (book.filePath != null) {
                try { File(book.filePath).delete() } catch (_: Exception) {}
            }
            if (book.coverImagePath != null) {
                try { File(book.coverImagePath).delete() } catch (_: Exception) {}
            }
            currentList.remove(book)
            _books.value = currentList
            saveCustomBooks(currentList)
            if (_currentBook.value?.id == bookId) {
                _currentBook.value = currentList.firstOrNull()
            }
        }
    }

    // Bookmarks and Highlights
    private fun loadBookmarks(): List<Bookmark> {
        val json = prefs.getString("user_bookmarks", null) ?: return emptyList()
        val list = mutableListOf<Bookmark>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    Bookmark(
                        id = obj.getString("id"),
                        bookId = obj.getString("bookId"),
                        chapterIndex = obj.getInt("chapterIndex"),
                        pageIndex = obj.getInt("pageIndex"),
                        chapterTitle = obj.getString("chapterTitle"),
                        excerpt = obj.getString("excerpt"),
                        note = obj.optString("note", ""),
                        timestamp = obj.optLong("timestamp", 0L)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveBookmarks(list: List<Bookmark>) {
        _bookmarks.value = list
        val array = JSONArray()
        for (bm in list) {
            val obj = JSONObject().apply {
                put("id", bm.id)
                put("bookId", bm.bookId)
                put("chapterIndex", bm.chapterIndex)
                put("pageIndex", bm.pageIndex)
                put("chapterTitle", bm.chapterTitle)
                put("excerpt", bm.excerpt)
                put("note", bm.note)
                put("timestamp", bm.timestamp)
            }
            array.put(obj)
        }
        prefs.edit().putString("user_bookmarks", array.toString()).apply()
    }

    fun addBookmark(bookmark: Bookmark) {
        val list = _bookmarks.value.toMutableList()
        list.add(0, bookmark)
        saveBookmarks(list)
    }

    fun removeBookmark(bookmarkId: String) {
        val list = _bookmarks.value.filter { it.id != bookmarkId }
        saveBookmarks(list)
    }

    private fun loadHighlights(): List<Highlight> {
        val json = prefs.getString("user_highlights", null) ?: return emptyList()
        val list = mutableListOf<Highlight>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    Highlight(
                        id = obj.getString("id"),
                        bookId = obj.getString("bookId"),
                        chapterIndex = obj.getInt("chapterIndex"),
                        pageIndex = obj.optInt("pageIndex", 0),
                        selectedText = obj.getString("selectedText"),
                        startCharOffset = obj.optInt("startCharOffset", 0),
                        endCharOffset = obj.optInt("endCharOffset", 0),
                        note = obj.optString("note", ""),
                        timestamp = obj.optLong("timestamp", 0L)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveHighlights(list: List<Highlight>) {
        _highlights.value = list
        val array = JSONArray()
        for (hl in list) {
            val obj = JSONObject().apply {
                put("id", hl.id)
                put("bookId", hl.bookId)
                put("chapterIndex", hl.chapterIndex)
                put("pageIndex", hl.pageIndex)
                put("selectedText", hl.selectedText)
                put("startCharOffset", hl.startCharOffset)
                put("endCharOffset", hl.endCharOffset)
                put("note", hl.note)
                put("timestamp", hl.timestamp)
            }
            array.put(obj)
        }
        prefs.edit().putString("user_highlights", array.toString()).apply()
    }

    fun addHighlight(highlight: Highlight) {
        val list = _highlights.value.toMutableList()
        list.add(0, highlight)
        saveHighlights(list)
    }

    fun removeHighlight(highlightId: String) {
        val list = _highlights.value.filter { it.id != highlightId }
        saveHighlights(list)
    }

    // Settings
    fun updateSettings(newSettings: ReaderSettings) {
        _settings.value = newSettings
        saveSavedSettings(newSettings)
    }

    fun setFontSize(size: Float) {
        val updated = _settings.value.copy(fontSizeSp = size.coerceIn(12f, 28f))
        _settings.value = updated
        saveSavedSettings(updated)
    }

    fun setThemeMode(mode: EInkThemeMode) {
        val updated = _settings.value.copy(themeMode = mode)
        _settings.value = updated
        saveSavedSettings(updated)
    }

    fun togglePageFlash(enabled: Boolean) {
        val updated = _settings.value.copy(pageFlashEnabled = enabled)
        _settings.value = updated
        saveSavedSettings(updated)
    }

    fun toggleDither(enabled: Boolean) {
        val updated = _settings.value.copy(ditherImages = enabled)
        _settings.value = updated
        saveSavedSettings(updated)
    }

    fun toggleFocusMode(enabled: Boolean) {
        val updated = _settings.value.copy(focusMode = enabled)
        _settings.value = updated
        saveSavedSettings(updated)
    }

    fun toggleCoversInLibrary(enabled: Boolean) {
        val updated = _settings.value.copy(showCoversInLibrary = enabled)
        _settings.value = updated
        saveSavedSettings(updated)
    }

    fun toggleLibraryGridView(isGrid: Boolean? = null) {
        val next = isGrid ?: !_settings.value.isLibraryGridView
        val updated = _settings.value.copy(isLibraryGridView = next)
        _settings.value = updated
        saveSavedSettings(updated)
    }

    fun setLibraryGridColumns(columns: Int) {
        val clamped = columns.coerceIn(2, 6)
        val updated = _settings.value.copy(libraryGridColumns = clamped)
        _settings.value = updated
        saveSavedSettings(updated)
    }

    fun cycleLibraryGridColumns() {
        val next = when (_settings.value.libraryGridColumns) {
            3 -> 4
            4 -> 5
            5 -> 3
            else -> 4
        }
        setLibraryGridColumns(next)
    }

    fun togglePublisherFormatting(enabled: Boolean) {
        val updated = _settings.value.copy(usePublisherFormatting = enabled)
        _settings.value = updated
        saveSavedSettings(updated)
    }

    fun toggleParagraphIndent(enabled: Boolean) {
        val updated = _settings.value.copy(paragraphIndentEnabled = enabled)
        _settings.value = updated
        saveSavedSettings(updated)
    }

    fun toggleFooterChapterPages(enabled: Boolean) {
        val updated = _settings.value.copy(footerShowChapterPages = enabled)
        _settings.value = updated
        saveSavedSettings(updated)
    }

    fun toggleFooterBookPages(enabled: Boolean) {
        val updated = _settings.value.copy(footerShowBookPages = enabled)
        _settings.value = updated
        saveSavedSettings(updated)
    }

    fun toggleFooterChapterTime(enabled: Boolean) {
        val updated = _settings.value.copy(footerShowChapterTime = enabled)
        _settings.value = updated
        saveSavedSettings(updated)
    }

    fun toggleFooterBookTime(enabled: Boolean) {
        val updated = _settings.value.copy(footerShowBookTime = enabled)
        _settings.value = updated
        saveSavedSettings(updated)
    }

    fun setFontOption(font: BookFontOption) {
        val fontId = when (font) {
            BookFontOption.SERIF -> "serif"
            BookFontOption.SANS_SERIF -> "sans"
            BookFontOption.MONOSPACE -> "mono"
            BookFontOption.CURSIVE -> "cursive"
        }
        val updated = _settings.value.copy(fontOption = font, selectedFontId = fontId)
        _settings.value = updated
        saveSavedSettings(updated)
    }

    fun setFontId(fontId: String) {
        val legacy = when (fontId.lowercase()) {
            "sans", "sans_serif" -> BookFontOption.SANS_SERIF
            "mono", "monospace" -> BookFontOption.MONOSPACE
            "cursive" -> BookFontOption.CURSIVE
            else -> BookFontOption.SERIF
        }
        val updated = _settings.value.copy(selectedFontId = fontId, fontOption = legacy)
        _settings.value = updated
        saveSavedSettings(updated)
    }

    fun setLineSpacingMultiplier(multiplier: Float) {
        val updated = _settings.value.copy(lineSpacingMultiplier = multiplier)
        _settings.value = updated
        saveSavedSettings(updated)
    }

    fun toggleBionicReading(enabled: Boolean? = null) {
        val next = enabled ?: !_settings.value.bionicReadingEnabled
        val updated = _settings.value.copy(bionicReadingEnabled = next)
        _settings.value = updated
        saveSavedSettings(updated)
    }

    fun release() {
        audioEngine.release()
        ttsEngine.release()
    }
}
