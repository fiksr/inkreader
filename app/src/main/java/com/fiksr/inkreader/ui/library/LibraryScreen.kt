package com.fiksr.inkreader.ui.library

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fiksr.inkreader.audio.AmbientSoundType
import com.fiksr.inkreader.data.ReaderRepository
import com.fiksr.inkreader.model.Book
import com.fiksr.inkreader.theme.BookFontFamily
import com.fiksr.inkreader.theme.LocalEInkColors
import com.fiksr.inkreader.ui.components.AmbientAudioSheet
import com.fiksr.inkreader.ui.components.DitherCover
import com.fiksr.inkreader.ui.sync.SyncSettingsDialog
import kotlinx.coroutines.launch

enum class LibraryTab {
    READING, ALL, DONE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    repository: ReaderRepository,
    onBookSelected: (Book) -> Unit,
    onOpenOpdsCatalog: () -> Unit,
    onOpenStats: () -> Unit = {},
    onOpenVocab: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalEInkColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val books by repository.books.collectAsState()
    val settings by repository.settings.collectAsState()
    val currentAmbient by repository.audioEngine.currentSound.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(LibraryTab.ALL) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val imported = repository.importEpub(uri)
            if (imported != null) {
                Toast.makeText(context, "Loaded: ${imported.title}", Toast.LENGTH_SHORT).show()
                onBookSelected(imported)
            } else {
                Toast.makeText(context, "Could not parse EPUB file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            coroutineScope.launch {
                Toast.makeText(context, "Scanning folder for EPUB books...", Toast.LENGTH_SHORT).show()
                val importedCount = repository.importEpubsFromTreeUri(treeUri)
                if (importedCount > 0) {
                    Toast.makeText(context, "Successfully imported $importedCount book(s)!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "No new EPUB books found in selected folder", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val filteredBooks = remember(books, searchQuery, selectedTab) {
        books.filter { book ->
            val matchesQuery = book.title.contains(searchQuery, ignoreCase = true) ||
                    book.author.contains(searchQuery, ignoreCase = true)
            val matchesTab = when (selectedTab) {
                LibraryTab.READING -> book.progressPercent in 1..99
                LibraryTab.ALL -> true
                LibraryTab.DONE -> book.progressPercent == 100
            }
            matchesQuery && matchesTab
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        // TOP HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "InkReader",
                fontFamily = BookFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = colors.text,
                modifier = Modifier.padding(end = 8.dp)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reading Stats & Heatmap
                IconButton(
                    onClick = onOpenStats,
                    modifier = Modifier
                        .border(1.dp, colors.border)
                        .size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Insights,
                        contentDescription = "Reading Stats",
                        tint = colors.text,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Vocabulary Deck & Flashcards
                IconButton(
                    onClick = onOpenVocab,
                    modifier = Modifier
                        .border(1.dp, colors.border)
                        .size(34.dp)
                ) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = "Vocabulary Deck",
                        tint = colors.text,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // AI Reading Companion Settings
                IconButton(
                    onClick = { showAiSettingsDialog = true },
                    modifier = Modifier
                        .border(1.dp, colors.border)
                        .size(34.dp)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "AI Reading Companion",
                        tint = colors.text,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Ambient White Noise & Sleep Timer
                IconButton(
                    onClick = { showAudioSheet = true },
                    modifier = Modifier
                        .border(1.dp, colors.border)
                        .size(34.dp)
                ) {
                    if (currentAmbient != AmbientSoundType.OFF) {
                        Text(currentAmbient.iconLabel, fontSize = 14.sp)
                    } else {
                        Icon(
                            Icons.Default.Headphones,
                            contentDescription = "Ambient Noise & Sleep",
                            tint = colors.text,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                // OPDS Online Catalogs Browser (BookOrbit / Calibre / Gutenberg / Standard Ebooks)
                IconButton(
                    onClick = onOpenOpdsCatalog,
                    modifier = Modifier
                        .border(1.dp, colors.border)
                        .size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = "OPDS Catalogs",
                        tint = colors.text,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // BookOrbit & KOReader Cloud Sync Settings
                IconButton(
                    onClick = { showSyncDialog = true },
                    modifier = Modifier
                        .border(1.dp, colors.border)
                        .size(34.dp)
                ) {
                    Icon(
                        Icons.Default.CloudSync,
                        contentDescription = "Sync Settings",
                        tint = colors.text,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Toggle Grid View <-> List View (4-5 books in a row)
                IconButton(
                    onClick = { repository.toggleLibraryGridView() },
                    modifier = Modifier
                        .border(1.dp, colors.border)
                        .size(34.dp)
                ) {
                    Icon(
                        imageVector = if (settings.isLibraryGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                        contentDescription = if (settings.isLibraryGridView) "Switch to List View" else "Switch to Grid View (4-5 in a row)",
                        tint = colors.text,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Fixed prominent Add Book button
            IconButton(
                onClick = { showImportDialog = true },
                modifier = Modifier
                    .border(1.5.dp, colors.border, RoundedCornerShape(6.dp))
                    .background(colors.text, RoundedCornerShape(6.dp))
                    .size(34.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Import EPUB",
                    tint = colors.background,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // SEARCH BAR
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    "Search books or authors...",
                    fontFamily = BookFontFamily,
                    fontSize = 13.sp,
                    color = colors.muted
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = colors.text, modifier = Modifier.size(18.dp))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            singleLine = true,
            shape = RoundedCornerShape(0.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.text,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text,
                cursorColor = colors.text
            )
        )

        HorizontalDivider(thickness = 1.dp, color = colors.border)

        // BOOK CONTENT (LIST VIEW OR GRID VIEW OF 4-5 BOOKS PER ROW)
        if (filteredBooks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No books found in this tab.",
                        fontFamily = BookFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = colors.text
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Tap '+' to import from Google Drive/device,\nor tap 🌐 to browse OPDS catalogs.",
                        fontFamily = BookFontFamily,
                        fontSize = 13.sp,
                        color = colors.muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else if (settings.isLibraryGridView) {
            // GRID VIEW: 4-5 books in row
            val gridCols = settings.libraryGridColumns.coerceIn(3, 5)
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridCols),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                items(filteredBooks, key = { it.id }) { book ->
                    BookGridItem(
                        book = book,
                        isDitherEnabled = settings.ditherImages,
                        onClick = { onBookSelected(book) },
                        onDelete = { repository.deleteBook(book.id) }
                    )
                }
            }
        } else {
            // LIST VIEW
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                items(filteredBooks, key = { it.id }) { book ->
                    BookRowItem(
                        book = book,
                        showCover = settings.showCoversInLibrary,
                        isDitherEnabled = settings.ditherImages,
                        onClick = { onBookSelected(book) },
                        onDelete = { repository.deleteBook(book.id) }
                    )
                }
            }
        }

        // BOTTOM TABS: [Reading | All | Done]
        HorizontalDivider(thickness = 1.dp, color = colors.border)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LibraryTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, colors.border)
                        .background(if (isSelected) colors.text else colors.background)
                        .clickable { selectedTab = tab }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (tab) {
                            LibraryTab.READING -> "Reading"
                            LibraryTab.ALL -> "All (${books.size})"
                            LibraryTab.DONE -> "Done"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp,
                        color = if (isSelected) colors.background else colors.text
                    )
                }
            }
        }
    }

    // Import Options Bottom Sheet
    if (showImportDialog) {
        val importSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showImportDialog = false },
            sheetState = importSheetState,
            containerColor = colors.background,
            contentColor = colors.text,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 8.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .background(colors.border, RoundedCornerShape(2.dp))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Books to Library",
                        fontFamily = BookFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.text
                    )
                    IconButton(
                        onClick = { showImportDialog = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.text,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = "Import EPUB files from your Google Drive, cloud storage, device files, or online OPDS servers.",
                    fontFamily = BookFontFamily,
                    fontSize = 12.sp,
                    color = colors.muted
                )

                HorizontalDivider(thickness = 1.dp, color = colors.border)

                // Option 1: Google Drive & Local Storage
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, colors.border),
                    color = colors.card,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showImportDialog = false
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = colors.text,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Google Drive & Single EPUB",
                                fontFamily = BookFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = colors.text
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Pick a single EPUB from Google Drive, Downloads, or Local Storage.",
                                fontFamily = BookFontFamily,
                                fontSize = 11.sp,
                                color = colors.muted
                            )
                        }
                    }
                }

                // Option 2: Scan Entire Folder / SD Card
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, colors.border),
                    color = colors.card,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showImportDialog = false
                            folderPickerLauncher.launch(null)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = colors.text,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📁 Scan Entire Folder / SD Card",
                                fontFamily = BookFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = colors.text
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Batch scan and import all EPUB files in a chosen folder tree.",
                                fontFamily = BookFontFamily,
                                fontSize = 11.sp,
                                color = colors.muted
                            )
                        }
                    }
                }

                // Option 3: OPDS Online Catalog
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, colors.border),
                    color = colors.card,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showImportDialog = false
                            onOpenOpdsCatalog()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            tint = colors.text,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Browse OPDS Catalogs",
                                fontFamily = BookFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = colors.text
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "BookOrbit, Calibre, Standard Ebooks, and Project Gutenberg.",
                                fontFamily = BookFontFamily,
                                fontSize = 11.sp,
                                color = colors.muted
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showSyncDialog) {
        SyncSettingsDialog(
            repository = repository,
            onDismiss = { showSyncDialog = false }
        )
    }

    if (showAudioSheet) {
        AmbientAudioSheet(
            audioEngine = repository.audioEngine,
            onDismiss = { showAudioSheet = false }
        )
    }

    if (showAiSettingsDialog) {
        com.fiksr.inkreader.ui.components.AiSettingsDialog(
            aiService = repository.aiCompanionService,
            onDismiss = { showAiSettingsDialog = false }
        )
    }
}

@Composable
fun BookRowItem(
    book: Book,
    showCover: Boolean,
    isDitherEnabled: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalEInkColors.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(2.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.border),
        color = colors.background,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showCover) {
                DitherCover(
                    title = book.title,
                    author = book.author,
                    coverImagePath = book.coverImagePath,
                    pattern = book.coverPattern,
                    isDitherEnabled = isDitherEnabled,
                    modifier = Modifier.size(width = 50.dp, height = 72.dp)
                )
                Spacer(Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = book.title,
                        fontFamily = BookFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${book.progressPercent}%",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = colors.muted
                    )
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    text = book.author,
                    fontFamily = BookFontFamily,
                    fontSize = 12.sp,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { book.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .border(0.5.dp, colors.border),
                    color = colors.text,
                    trackColor = colors.card
                )
            }

            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete Book",
                    tint = colors.muted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "Remove Book?",
                    fontFamily = BookFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove '${book.title}' from your library?",
                    fontFamily = BookFontFamily,
                    fontSize = 13.sp,
                    color = colors.text
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = colors.text, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = colors.muted)
                }
            },
            containerColor = colors.background,
            shape = RoundedCornerShape(2.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookGridItem(
    book: Book,
    isDitherEnabled: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEInkColors.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showDeleteConfirm = true }
            )
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Book Cover Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
        ) {
            DitherCover(
                title = book.title,
                author = book.author,
                coverImagePath = book.coverImagePath,
                pattern = book.coverPattern,
                isDitherEnabled = isDitherEnabled,
                modifier = Modifier.fillMaxSize()
            )

            // Progress percentage badge
            if (book.progressPercent > 0) {
                Surface(
                    shape = RoundedCornerShape(topStart = 2.dp),
                    color = colors.background.copy(alpha = 0.92f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.border),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = "${book.progressPercent}%",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(3.dp))

        // Progress line
        LinearProgressIndicator(
            progress = { book.progressPercent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .border(0.5.dp, colors.border),
            color = colors.text,
            trackColor = colors.card
        )

        Spacer(Modifier.height(3.dp))

        // Title
        Text(
            text = book.title,
            fontFamily = BookFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Author
        if (book.author.isNotBlank()) {
            Text(
                text = book.author,
                fontFamily = BookFontFamily,
                fontSize = 8.sp,
                lineHeight = 10.sp,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "Remove Book?",
                    fontFamily = BookFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove '${book.title}' from your library?",
                    fontFamily = BookFontFamily,
                    fontSize = 13.sp,
                    color = colors.text
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = colors.text, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = colors.muted)
                }
            },
            containerColor = colors.background,
            shape = RoundedCornerShape(2.dp)
        )
    }
}
