package com.example.inkreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.inkreader.data.ReaderRepository
import com.example.inkreader.model.Book
import com.example.inkreader.theme.BookFontFamily
import com.example.inkreader.theme.LocalEInkColors
import java.text.SimpleDateFormat
import java.util.*

enum class AnnotationTab {
    BOOKMARKS, HIGHLIGHTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationsSheet(
    repository: ReaderRepository,
    book: Book,
    onJumpToLocation: (chapterIndex: Int, pageIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalEInkColors.current
    val bookmarks by repository.bookmarks.collectAsState()
    val highlights by repository.highlights.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedTab by remember { mutableStateOf(AnnotationTab.BOOKMARKS) }

    val bookBookmarks = remember(bookmarks, book.id) {
        bookmarks.filter { it.bookId == book.id }
    }
    val bookHighlights = remember(highlights, book.id) {
        highlights.filter { it.bookId == book.id }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
                .fillMaxHeight(0.85f)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = colors.text,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Notes & Bookmarks",
                        fontFamily = BookFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.text
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.text, modifier = Modifier.size(20.dp))
                }
            }

                Spacer(Modifier.height(10.dp))

                // TABS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        AnnotationTab.BOOKMARKS to "Bookmarks (${bookBookmarks.size})",
                        AnnotationTab.HIGHLIGHTS to "Highlights (${bookHighlights.size})"
                    ).forEach { (tab, label) ->
                        val isSelected = selectedTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, colors.border)
                                .background(if (isSelected) colors.text else colors.background)
                                .clickable { selectedTab = tab }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colors.background else colors.text
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(thickness = 1.dp, color = colors.border)
                Spacer(Modifier.height(8.dp))

                // CONTENT LIST
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedTab == AnnotationTab.BOOKMARKS) {
                        if (bookBookmarks.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No bookmarks yet.\nTap the bookmark icon in the reading menu to save your spot.",
                                        fontFamily = BookFontFamily,
                                        fontSize = 13.sp,
                                        color = colors.muted,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            items(bookBookmarks, key = { it.id }) { bm ->
                                Surface(
                                    shape = RoundedCornerShape(2.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                                    color = colors.card,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onJumpToLocation(bm.chapterIndex, bm.pageIndex)
                                            onDismiss()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Bookmark,
                                            contentDescription = null,
                                            tint = colors.text,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = bm.chapterTitle,
                                                fontFamily = BookFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = colors.text
                                            )
                                            Text(
                                                text = bm.excerpt,
                                                fontFamily = BookFontFamily,
                                                fontSize = 12.sp,
                                                color = colors.muted,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(bm.timestamp))
                                            Text(
                                                text = "Page ${bm.pageIndex + 1} • $dateStr",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = colors.muted
                                            )
                                        }
                                        IconButton(
                                            onClick = { repository.removeBookmark(bm.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.muted, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (bookHighlights.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No highlights yet.\nLong press any word or sentence while reading to create a highlight.",
                                        fontFamily = BookFontFamily,
                                        fontSize = 13.sp,
                                        color = colors.muted,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            items(bookHighlights, key = { it.id }) { hl ->
                                Surface(
                                    shape = RoundedCornerShape(2.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                                    color = colors.card,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onJumpToLocation(hl.chapterIndex, hl.pageIndex)
                                            onDismiss()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Highlight,
                                            contentDescription = null,
                                            tint = colors.text,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "“${hl.selectedText}”",
                                                fontFamily = BookFontFamily,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 13.sp,
                                                color = colors.text
                                            )
                                            if (hl.note.isNotEmpty()) {
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = "Note: ${hl.note}",
                                                    fontFamily = BookFontFamily,
                                                    fontSize = 12.sp,
                                                    color = colors.muted
                                                )
                                            }
                                            val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(hl.timestamp))
                                            Text(
                                                text = "Ch. ${hl.chapterIndex + 1}, p. ${hl.pageIndex + 1} • $dateStr",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = colors.muted
                                            )
                                        }
                                        IconButton(
                                            onClick = { repository.removeHighlight(hl.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.muted, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
