package com.fiksr.inkreader.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fiksr.inkreader.data.AiChatMessage
import com.fiksr.inkreader.data.AiCompanionService
import com.fiksr.inkreader.theme.LocalEInkColors
import com.fiksr.inkreader.ui.components.AiSettingsDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCompanionSheet(
    aiService: AiCompanionService,
    bookTitle: String,
    bookAuthor: String,
    currentChapterTitle: String,
    currentChapterIndex: Int = 1,
    totalChapters: Int = 1,
    bookProgressPercent: Int = 0,
    currentExcerpt: String,
    initialQuery: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalEInkColors.current
    val scope = rememberCoroutineScope()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var currentAiSettings by remember { mutableStateOf(aiService.getSettings()) }

    var messages by remember { mutableStateOf<List<AiChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var activeModeTitle by remember { mutableStateOf("✦ AI Reading Companion") }

    // Execute initial query if provided (e.g. from selecting text in reader)
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank() && currentAiSettings.apiKey.isNotBlank()) {
            isGenerating = true
            activeModeTitle = "Explain: \"${initialQuery.take(24)}...\""
            messages = listOf(AiChatMessage("user", "Explain: \"$initialQuery\""))
            val res = aiService.explainPassage(initialQuery, bookTitle, bookAuthor)
            isGenerating = false
            if (res.isSuccess) {
                messages = messages + AiChatMessage("ai", res.getOrDefault("No explanation found."))
            } else {
                messages = messages + AiChatMessage("ai", "Error: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(colors.divider, RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = activeModeTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary
                        )
                        if (currentAiSettings.apiKey.isNotBlank()) {
                            Text(
                                text = "${currentAiSettings.provider.displayName} • ${currentAiSettings.model}",
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }

                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "AI Settings",
                        tint = colors.textPrimary
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = colors.divider)

            if (currentAiSettings.apiKey.isBlank()) {
                // Not configured state
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = colors.background,
                            border = BorderStroke(1.dp, colors.divider),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Unlock AI Reading Companion",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Add your free Gemini, Groq, or OpenAI API key to get instant spoiler-free character reminders, 'Catch Me Up' chapter recaps, and live plot explanations.",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            lineHeight = 18.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { showSettingsDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.textPrimary,
                                contentColor = colors.background
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Configure AI Key (Free)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Quick Action Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Catch Me Up
                    Surface(
                        onClick = {
                            isGenerating = true
                            activeModeTitle = "📖 Catch Me Up"
                            messages = listOf(AiChatMessage("user", "Catch me up on what happened so far"))
                            scope.launch {
                                val res = aiService.generateCatchMeUp(
                                    bookTitle = bookTitle,
                                    author = bookAuthor,
                                    currentChapterTitle = currentChapterTitle,
                                    currentChapterIndex = currentChapterIndex,
                                    totalChapters = totalChapters,
                                    bookProgressPercent = bookProgressPercent,
                                    recentExcerpt = currentExcerpt
                                )
                                isGenerating = false
                                if (res.isSuccess) {
                                    messages = messages + AiChatMessage("ai", res.getOrDefault("No recap generated."))
                                } else {
                                    messages = messages + AiChatMessage("ai", "Error: ${res.exceptionOrNull()?.message}")
                                }
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = colors.background,
                        border = BorderStroke(1.dp, colors.divider),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("📖", fontSize = 13.sp)
                            Text("Catch Me Up", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        }
                    }

                    // Who is this character?
                    Surface(
                        onClick = {
                            inputText = "Who is "
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = colors.background,
                        border = BorderStroke(1.dp, colors.divider),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("👤", fontSize = 13.sp)
                            Text("Who is this?", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        }
                    }

                    // Explain section
                    Surface(
                        onClick = {
                            isGenerating = true
                            activeModeTitle = "🔍 Explain Page"
                            messages = listOf(AiChatMessage("user", "Explain what is happening on this page"))
                            scope.launch {
                                val res = aiService.explainPassage(currentExcerpt, bookTitle, bookAuthor)
                                isGenerating = false
                                if (res.isSuccess) {
                                    messages = messages + AiChatMessage("ai", res.getOrDefault("No explanation."))
                                } else {
                                    messages = messages + AiChatMessage("ai", "Error: ${res.exceptionOrNull()?.message}")
                                }
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = colors.background,
                        border = BorderStroke(1.dp, colors.divider),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🔍", fontSize = 13.sp)
                            Text("Explain Page", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Messages / Answers
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (messages.isEmpty() && !isGenerating) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Ask anything about \"$bookTitle\" or tap an action above.",
                                    color = colors.textSecondary,
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    items(messages) { msg ->
                        if (msg.sender == "user") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp, 14.dp, 2.dp, 14.dp),
                                    color = colors.textPrimary,
                                    modifier = Modifier.widthIn(max = 280.dp)
                                ) {
                                    Text(
                                        text = msg.text,
                                        color = colors.background,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(14.dp, 14.dp, 14.dp, 2.dp),
                                color = colors.background,
                                border = BorderStroke(1.dp, colors.divider),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    SelectionContainer {
                                        Text(
                                            text = msg.text,
                                            color = colors.textPrimary,
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("AI Answer", msg.text))
                                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copy",
                                                tint = colors.textSecondary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isGenerating) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.background,
                                border = BorderStroke(1.dp, colors.divider),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "InkReader AI is thinking...",
                                        fontSize = 12.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Chat Input Box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask about character, plot, or lore...", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.textPrimary,
                            unfocusedBorderColor = colors.divider,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )

                    IconButton(
                        onClick = {
                            val q = inputText.trim()
                            if (q.isNotBlank()) {
                                isGenerating = true
                                val userMsg = AiChatMessage("user", q)
                                val history = messages
                                messages = messages + userMsg
                                inputText = ""
                                scope.launch {
                                    val res = if (q.startsWith("Who is", ignoreCase = true)) {
                                        val charName = q.removePrefix("Who is").removePrefix("who is").trim().trim('?', '.')
                                        aiService.explainCharacter(charName, bookTitle, bookAuthor, currentChapterTitle, currentExcerpt)
                                    } else {
                                        aiService.askBookQuestion(q, bookTitle, bookAuthor, currentChapterTitle, currentExcerpt, history)
                                    }
                                    isGenerating = false
                                    if (res.isSuccess) {
                                        messages = messages + AiChatMessage("ai", res.getOrDefault("No response."))
                                    } else {
                                        messages = messages + AiChatMessage("ai", "Error: ${res.exceptionOrNull()?.message}")
                                    }
                                }
                            }
                        },
                        enabled = inputText.isNotBlank() && !isGenerating,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (inputText.isNotBlank() && !isGenerating) colors.textPrimary else colors.divider,
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank() && !isGenerating) colors.background else colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        AiSettingsDialog(
            aiService = aiService,
            onDismiss = {
                showSettingsDialog = false
                currentAiSettings = aiService.getSettings()
            }
        )
    }
}
