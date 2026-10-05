package com.example.inkreader

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.inkreader.data.ReaderRepository
import com.example.inkreader.model.Book
import com.example.inkreader.theme.InkReaderTheme
import com.example.inkreader.theme.LocalEInkColors
import com.example.inkreader.ui.library.LibraryScreen
import com.example.inkreader.ui.opds.OpdsScreen
import com.example.inkreader.ui.reader.ReaderScreen
import com.example.inkreader.ui.stats.StatsScreen
import com.example.inkreader.ui.vocabulary.VocabularyScreen

enum class ScreenState {
    LIBRARY, READER, OPDS, STATS, VOCABULARY
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: ReaderRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ReaderRepository(applicationContext)

        enableEdgeToEdge()

        setContent {
            val settings by repository.settings.collectAsState()
            val currentBook by repository.currentBook.collectAsState()
            var currentScreen by remember { mutableStateOf(ScreenState.LIBRARY) }

            // Keep screen on if Focus Mode is enabled
            LaunchedEffect(settings.focusMode) {
                if (settings.focusMode) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            InkReaderTheme(mode = settings.themeMode) {
                val colors = LocalEInkColors.current
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    color = colors.background
                ) {
                    when (currentScreen) {
                        ScreenState.LIBRARY -> {
                            LibraryScreen(
                                repository = repository,
                                onBookSelected = { book ->
                                    repository.selectBook(book.id)
                                    currentScreen = ScreenState.READER
                                },
                                onOpenOpdsCatalog = {
                                    currentScreen = ScreenState.OPDS
                                },
                                onOpenStats = {
                                    currentScreen = ScreenState.STATS
                                },
                                onOpenVocab = {
                                    currentScreen = ScreenState.VOCABULARY
                                }
                            )
                        }
                        ScreenState.READER -> {
                            currentBook?.let { book ->
                                ReaderScreen(
                                    repository = repository,
                                    book = book,
                                    onBackToLibrary = { currentScreen = ScreenState.LIBRARY }
                                )
                            } ?: run {
                                currentScreen = ScreenState.LIBRARY
                            }
                        }
                        ScreenState.OPDS -> {
                            OpdsScreen(
                                repository = repository,
                                onBack = { currentScreen = ScreenState.LIBRARY },
                                onBookDownloadedAndOpen = { book ->
                                    repository.selectBook(book.id)
                                    currentScreen = ScreenState.READER
                                }
                            )
                        }
                        ScreenState.STATS -> {
                            StatsScreen(
                                statsRepository = repository.statsRepository,
                                onBack = { currentScreen = ScreenState.LIBRARY }
                            )
                        }
                        ScreenState.VOCABULARY -> {
                            VocabularyScreen(
                                vocabularyRepository = repository.vocabularyRepository,
                                onBack = { currentScreen = ScreenState.LIBRARY }
                            )
                        }
                    }
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        repository.audioEngine.release()
    }
}
