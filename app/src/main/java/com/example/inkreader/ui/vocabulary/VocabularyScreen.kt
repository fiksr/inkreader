package com.example.inkreader.ui.vocabulary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.inkreader.data.VocabWord
import com.example.inkreader.data.VocabularyRepository
import com.example.inkreader.theme.LocalEInkColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyScreen(
    vocabularyRepository: VocabularyRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEInkColors.current
    val words by vocabularyRepository.words.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isFlashcardMode by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }

    val filteredWords = remember(words, searchQuery) {
        if (searchQuery.isBlank()) words
        else words.filter {
            it.word.contains(searchQuery, ignoreCase = true) ||
                    it.definition.contains(searchQuery, ignoreCase = true) ||
                    it.bookTitle.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Vocabulary Deck",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary
                        )
                        Text(
                            text = "${words.size} saved words • Flashcard Ready",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                    }
                },
                actions = {
                    if (words.isNotEmpty()) {
                        IconButton(onClick = { isFlashcardMode = true }) {
                            Icon(Icons.Default.Style, contentDescription = "Flashcards", tint = colors.textPrimary)
                        }

                        Box {
                            IconButton(onClick = { showExportMenu = true }) {
                                Icon(Icons.Default.FileDownload, contentDescription = "Export", tint = colors.textPrimary)
                            }
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false },
                                modifier = Modifier.background(colors.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("📥 Save CSV to Downloads (.csv)", color = colors.textPrimary, fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        showExportMenu = false
                                        val content = vocabularyRepository.exportToCsv()
                                        vocabularyRepository.saveToDownloads(content, "InkReader_Vocab_${System.currentTimeMillis()}.csv", "text/csv")
                                    },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = colors.textPrimary) }
                                )
                                DropdownMenuItem(
                                    text = { Text("📥 Save Markdown to Downloads (.md)", color = colors.textPrimary, fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        showExportMenu = false
                                        val content = vocabularyRepository.exportToMarkdown()
                                        vocabularyRepository.saveToDownloads(content, "InkReader_Vocab_${System.currentTimeMillis()}.md", "text/markdown")
                                    },
                                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = colors.textPrimary) }
                                )
                                DropdownMenuItem(
                                    text = { Text("📥 Save Anki Deck to Downloads (.txt)", color = colors.textPrimary, fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        showExportMenu = false
                                        val content = vocabularyRepository.exportToAnkiTsv()
                                        vocabularyRepository.saveToDownloads(content, "InkReader_Anki_${System.currentTimeMillis()}.txt", "text/plain")
                                    },
                                    leadingIcon = { Icon(Icons.Default.School, contentDescription = null, tint = colors.textPrimary) }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = colors.divider)
                                DropdownMenuItem(
                                    text = { Text("↗️ Share to Obsidian / Notes", color = colors.textPrimary) },
                                    onClick = {
                                        showExportMenu = false
                                        val content = vocabularyRepository.exportToMarkdown()
                                        vocabularyRepository.shareExportFile(content, "inkreader_vocab_deck.md", "text/plain")
                                    },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = colors.textPrimary) }
                                )
                                DropdownMenuItem(
                                    text = { Text("↗️ Share to Anki / Other Apps", color = colors.textPrimary) },
                                    onClick = {
                                        showExportMenu = false
                                        val content = vocabularyRepository.exportToAnkiTsv()
                                        vocabularyRepository.shareExportFile(content, "inkreader_anki_vocab.txt", "text/plain")
                                    },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = colors.textPrimary) }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        containerColor = colors.background,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search words, definitions, or books...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.textSecondary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = colors.textSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.textPrimary,
                    unfocusedBorderColor = colors.divider,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                )
            )

            if (filteredWords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = colors.textSecondary
                        )
                        Text(
                            text = if (searchQuery.isBlank()) "No words in your vocabulary yet" else "No matching words found",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary
                        )
                        Text(
                            text = "Words looked up or highlighted in the reader automatically appear here.",
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredWords) { wordItem ->
                        VocabWordCard(
                            wordItem = wordItem,
                            onToggleMastery = { vocabularyRepository.toggleMastery(wordItem.id) },
                            onDelete = { vocabularyRepository.deleteWord(wordItem.id) }
                        )
                    }
                }
            }
        }
    }

    if (isFlashcardMode && words.isNotEmpty()) {
        FlashcardReviewDialog(
            words = words,
            onToggleMastery = { id -> vocabularyRepository.toggleMastery(id) },
            onDismiss = { isFlashcardMode = false }
        )
    }
}

@Composable
fun VocabWordCard(
    wordItem: VocabWord,
    onToggleMastery: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalEInkColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.divider)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Top row: Word + Phonetic + Mastery badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = wordItem.word,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontFamily = FontFamily.Serif
                    )
                    if (wordItem.phonetic.isNotBlank()) {
                        Text(
                            text = wordItem.phonetic,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Mastery Chip
                    Surface(
                        onClick = onToggleMastery,
                        shape = RoundedCornerShape(12.dp),
                        color = when (wordItem.masteryScore) {
                            2 -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                            1 -> Color(0xFFE65100).copy(alpha = 0.15f)
                            else -> colors.background
                        },
                        border = BorderStroke(
                            1.dp,
                            when (wordItem.masteryScore) {
                                2 -> Color(0xFF2E7D32)
                                1 -> Color(0xFFE65100)
                                else -> colors.divider
                            }
                        ),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(
                                text = when (wordItem.masteryScore) {
                                    2 -> "✓ Mastered"
                                    1 -> "● Learning"
                                    else -> "○ New"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (wordItem.masteryScore) {
                                    2 -> Color(0xFF2E7D32)
                                    1 -> Color(0xFFE65100)
                                    else -> colors.textSecondary
                                }
                            )
                        }
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Definition
            Text(
                text = wordItem.definition,
                fontSize = 13.sp,
                color = colors.textPrimary,
                lineHeight = 18.sp
            )

            // Sentence Context
            if (wordItem.contextSentence.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "“${wordItem.contextSentence}”",
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(8.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (wordItem.bookTitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "From: ${wordItem.bookTitle}",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun FlashcardReviewDialog(
    words: List<VocabWord>,
    onToggleMastery: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalEInkColors.current
    var currentIndex by remember { mutableIntStateOf(0) }
    var isRevealed by remember { mutableStateOf(false) }

    val currentWord = words[currentIndex.coerceIn(0, words.size - 1)]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.75f)
                .clip(RoundedCornerShape(16.dp)),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.divider)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Card ${currentIndex + 1} of ${words.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Flashcard Surface (Tap to flip)
                Surface(
                    onClick = { isRevealed = !isRevealed },
                    shape = RoundedCornerShape(14.dp),
                    color = colors.background,
                    border = BorderStroke(1.5.dp, colors.divider),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = currentWord.word,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif,
                                color = colors.textPrimary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            if (currentWord.phonetic.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentWord.phonetic,
                                    fontSize = 14.sp,
                                    color = colors.textSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (isRevealed) {
                                Text(
                                    text = currentWord.definition,
                                    fontSize = 15.sp,
                                    color = colors.textPrimary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 22.sp
                                )

                                if (currentWord.contextSentence.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "“${currentWord.contextSentence}”",
                                        fontSize = 12.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = colors.textSecondary,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            } else {
                                Text(
                                    text = "Tap card to reveal definition",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Navigation & Mastery Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (currentIndex > 0) {
                                currentIndex--
                                isRevealed = false
                            }
                        },
                        enabled = currentIndex > 0
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = colors.textPrimary)
                    }

                    Button(
                        onClick = {
                            onToggleMastery(currentWord.id)
                            if (currentIndex < words.size - 1) {
                                currentIndex++
                                isRevealed = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.textPrimary,
                            contentColor = colors.background
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Mark Mastered & Next", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = {
                            if (currentIndex < words.size - 1) {
                                currentIndex++
                                isRevealed = false
                            }
                        },
                        enabled = currentIndex < words.size - 1
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = colors.textPrimary)
                    }
                }
            }
        }
    }
}
