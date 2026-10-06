package com.fiksr.inkreader.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fiksr.inkreader.audio.AmbientSoundType
import com.fiksr.inkreader.audio.TtsEngineType
import com.fiksr.inkreader.audio.UnifiedTtsState
import com.fiksr.inkreader.data.AiExplanation
import com.fiksr.inkreader.data.DictionaryService
import com.fiksr.inkreader.data.ReaderRepository
import com.fiksr.inkreader.data.TranslationResult
import com.fiksr.inkreader.data.WordDefinition
import com.fiksr.inkreader.model.Book
import com.fiksr.inkreader.model.Bookmark
import com.fiksr.inkreader.model.Highlight
import com.fiksr.inkreader.theme.BookFontFamily
import com.fiksr.inkreader.theme.BookFontOption
import com.fiksr.inkreader.theme.CustomFontManager
import com.fiksr.inkreader.theme.FontItem
import com.fiksr.inkreader.theme.EInkThemeMode
import com.fiksr.inkreader.theme.LocalEInkColors
import com.fiksr.inkreader.ui.components.AmbientAudioSheet
import com.fiksr.inkreader.data.BionicReadingHelper
import com.fiksr.inkreader.ui.reader.AiCompanionSheet
import com.fiksr.inkreader.ui.reader.QuoteCardDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

data class PageChunk(val text: String, val startInChapter: Int, val endInChapter: Int)

fun paginateChapter(
    chapterText: String,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    widthPx: Int,
    heightPx: Int
): List<PageChunk> {
    if (chapterText.isBlank() || widthPx <= 0 || heightPx <= 0) {
        return listOf(PageChunk(chapterText, 0, chapterText.length))
    }

    val chunks = mutableListOf<PageChunk>()
    var startOffset = 0
    val totalLen = chapterText.length

    while (startOffset < totalLen) {
        // Skip leading whitespace or newlines at the beginning of a page
        while (startOffset < totalLen && chapterText[startOffset].isWhitespace()) {
            startOffset++
        }
        if (startOffset >= totalLen) break

        val remainingText = chapterText.substring(startOffset)
        // Measure up to 6000 chars for instant layout calculation
        val candidateText = if (remainingText.length > 6000) remainingText.substring(0, 6000) else remainingText

        val measureResult = textMeasurer.measure(
            text = AnnotatedString(candidateText),
            style = textStyle,
            constraints = Constraints(maxWidth = widthPx)
        )

        val lineCount = measureResult.lineCount
        if (lineCount == 0) break

        // Safety headroom: ensure line descenders and line spacing have 16px breathing room
        // so lines never get clipped horizontally at the bottom of the page
        val safeHeightPx = (heightPx - 16).coerceAtLeast(60)

        var lastFittingLine = -1
        for (line in 0 until lineCount) {
            val lineBottom = measureResult.getLineBottom(line)
            if (lineBottom <= safeHeightPx) {
                lastFittingLine = line
            } else {
                break
            }
        }

        val chunkCharCount = if (lastFittingLine >= 0) {
            measureResult.getLineEnd(lastFittingLine, visibleEnd = true)
        } else {
            measureResult.getLineEnd(0, visibleEnd = true).coerceAtLeast(1)
        }

        if (chunkCharCount <= 0) {
            val safeEnd = (startOffset + 100).coerceAtMost(totalLen)
            chunks.add(PageChunk(chapterText.substring(startOffset, safeEnd), startOffset, safeEnd))
            startOffset = safeEnd
        } else {
            val endOffset = (startOffset + chunkCharCount).coerceAtMost(totalLen)
            val chunkText = chapterText.substring(startOffset, endOffset).trimEnd()
            chunks.add(PageChunk(chunkText, startOffset, endOffset))
            startOffset = endOffset
        }
    }

    return if (chunks.isEmpty()) listOf(PageChunk(chapterText, 0, totalLen)) else chunks
}

fun getWordBoundsAt(text: String, offset: Int): Pair<Int, Int>? {
    if (text.isEmpty() || offset !in text.indices) return null
    var start = offset
    var end = offset
    while (start > 0 && !text[start - 1].isWhitespace()) {
        start--
    }
    while (end < text.length && !text[end].isWhitespace()) {
        end++
    }
    val punct = " \t\n\r\"'.,;:!?()[]{}—–-“”‘’`~«»"
    while (start < end && text[start] in punct) {
        start++
    }
    while (end > start && text[end - 1] in punct) {
        end--
    }
    return if (start < end) Pair(start, end) else null
}

fun expandSelectionLeft(text: String, start: Int): Int {
    if (start <= 0 || text.isEmpty()) return 0
    var s = (start - 1).coerceIn(0, text.length - 1)
    val punct = " \t\n\r\"'.,;:!?()[]{}—–-“”‘’`~«»"
    while (s > 0 && (text[s].isWhitespace() || text[s] in punct)) {
        s--
    }
    while (s > 0 && !text[s - 1].isWhitespace() && text[s - 1] !in punct) {
        s--
    }
    return s.coerceAtLeast(0)
}

fun expandSelectionRight(text: String, end: Int): Int {
    if (end >= text.length || text.isEmpty()) return text.length
    var e = end.coerceIn(0, text.length)
    val punct = " \t\n\r\"'.,;:!?()[]{}—–-“”‘’`~«»"
    while (e < text.length && (text[e].isWhitespace() || text[e] in punct)) {
        e++
    }
    while (e < text.length && !text[e].isWhitespace() && text[e] !in punct) {
        e++
    }
    return e.coerceAtMost(text.length)
}

fun getSentenceBoundsAt(text: String, offset: Int): Pair<Int, Int> {
    if (text.isEmpty()) return Pair(0, 0)
    val clamped = offset.coerceIn(0, text.length - 1)
    val terminators = charArrayOf('.', '!', '?', '\n')
    var start = 0
    for (i in (clamped - 1) downTo 0) {
        if (text[i] in terminators) {
            start = i + 1
            break
        }
    }
    while (start < clamped && text[start].isWhitespace()) {
        start++
    }
    var end = text.length
    for (i in clamped until text.length) {
        if (text[i] in terminators) {
            end = i + 1
            break
        }
    }
    return Pair(start.coerceAtLeast(0), end.coerceAtMost(text.length))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    repository: ReaderRepository,
    book: Book,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEInkColors.current
    val context = LocalContext.current
    val settings by repository.settings.collectAsState()
    val highlights by repository.highlights.collectAsState()
    val ambientSound by repository.audioEngine.currentSound.collectAsState()
    val activeCustomSoundId by repository.audioEngine.activeCustomSoundId.collectAsState()
    val activeSoundIcon by repository.audioEngine.activeSoundIcon.collectAsState()
    val sleepTimer by repository.audioEngine.sleepTimerMinutesLeft.collectAsState()
    val syncStatus by repository.syncClient.syncStatusMessage.collectAsState()
    val ttsState by repository.ttsEngine.state.collectAsState()

    var refreshFontTrigger by remember { mutableIntStateOf(0) }
    val currentFontFamily = remember(settings.selectedFontId, refreshFontTrigger) {
        CustomFontManager.resolveFontFamily(context, settings.selectedFontId)
    }

    var showDisplaySheet by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showAnnotationsSheet by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showTtsMiniPlayer by remember { mutableStateOf(false) }
    var showFlashOverlay by remember { mutableStateOf(false) }
    var showLookupDialog by remember { mutableStateOf(false) }
    var showScrubberBar by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }
    var showQuoteCardDialog by remember { mutableStateOf(false) }
    var aiInitialQuery by remember { mutableStateOf<String?>(null) }

    var selectedWordRange by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var selectedWordText by remember { mutableStateOf("") }
    var activeSavedHighlightId by remember { mutableStateOf<String?>(null) }
    var currentDefinition by remember { mutableStateOf<WordDefinition?>(null) }
    var currentTranslation by remember { mutableStateOf<TranslationResult?>(null) }
    var currentAiExplanation by remember { mutableStateOf<AiExplanation?>(null) }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val coroutineScope = rememberCoroutineScope()

    // Habit & stats tracking: 1 minute tick while reading
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            repository.statsRepository.recordReadingTime(1)
        }
    }

    // Page state
    val chapters = book.chapters
    var chapterIdx by remember(book.id, book.currentChapterIndex) {
        mutableIntStateOf(book.currentChapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)))
    }
    val currentChapter = chapters.getOrElse(chapterIdx) { chapters.first() }

    // Format chapter text based on paragraph indentation mode
    val displayChapterContent = remember(currentChapter.content, settings.paragraphIndentEnabled) {
        if (settings.paragraphIndentEnabled) {
            // Traditional book style:
            // Normalize standard paragraph breaks to single \n so paragraphs flow compactly with first-line indent.
            // Major scene breaks (3+ newlines) preserve a blank line (\n\n).
            currentChapter.content
                .replace(Regex("\n{3,}"), "\n\n")
                .replace(Regex("(?<!\n)\n\n(?!\n)"), "\n")
        } else {
            currentChapter.content
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val displayMetrics = context.resources.displayMetrics
    val defaultWidthPx = remember(displayMetrics) {
        (displayMetrics.widthPixels - with(density) { 36.dp.roundToPx() }).coerceAtLeast(100)
    }
    val defaultHeightPx = remember(displayMetrics) {
        (displayMetrics.heightPixels - with(density) { 130.dp.roundToPx() }).coerceAtLeast(100)
    }
    var measuredWidthPx by remember { mutableIntStateOf(defaultWidthPx) }
    var measuredHeightPx by remember { mutableIntStateOf(defaultHeightPx) }

    val currentTextStyle = remember(
        currentFontFamily,
        settings.fontSizeSp,
        settings.lineSpacingMultiplier,
        settings.usePublisherFormatting,
        settings.paragraphIndentEnabled
    ) {
        TextStyle(
            fontFamily = currentFontFamily,
            fontSize = settings.fontSizeSp.sp,
            lineHeight = (settings.fontSizeSp * settings.lineSpacingMultiplier).sp,
            textAlign = if (settings.usePublisherFormatting) TextAlign.Start else TextAlign.Justify,
            textIndent = if (settings.paragraphIndentEnabled) TextIndent(firstLine = (settings.fontSizeSp * 1.25f).sp) else TextIndent.None,
            lineBreak = LineBreak.Paragraph,
            hyphens = Hyphens.Auto
        )
    }

    // PIXEL-PERFECT SCREEN PAGINATION WITH ZERO CLIPPING
    val pageChunks = remember(displayChapterContent, measuredWidthPx, measuredHeightPx, currentTextStyle) {
        paginateChapter(
            chapterText = displayChapterContent,
            textMeasurer = textMeasurer,
            textStyle = currentTextStyle,
            widthPx = measuredWidthPx,
            heightPx = measuredHeightPx
        )
    }

    val pages = remember(pageChunks) { pageChunks.map { it.text } }

    var pageIdx by remember(book.id, chapterIdx) {
        mutableIntStateOf(book.currentPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
    }

    LaunchedEffect(pages.size) {
        if (pageIdx >= pages.size) {
            pageIdx = (pages.size - 1).coerceAtLeast(0)
        }
    }

    val currentChunk = pageChunks.getOrElse(pageIdx) { PageChunk("", 0, 0) }
    val currentPageText = currentChunk.text

    val nextPageText = remember(pageIdx, chapterIdx, pageChunks, chapters) {
        if (pageIdx < pageChunks.size - 1) {
            pageChunks[pageIdx + 1].text
        } else if (chapterIdx < chapters.size - 1) {
            chapters[chapterIdx + 1].content.take(1500)
        } else {
            ""
        }
    }

    LaunchedEffect(pageIdx, chapterIdx) {
        selectedWordRange = null
        selectedWordText = ""
        activeSavedHighlightId = null
    }

    // TOTAL BOOK PAGES ESTIMATE
    val totalPagesInBook = remember(chapters, pageChunks.size, displayChapterContent.length) {
        val avgPageLen = if (pageChunks.isNotEmpty() && displayChapterContent.isNotEmpty()) (displayChapterContent.length / pageChunks.size) else 1500
        val safeAvg = avgPageLen.coerceIn(400, 3500)
        chapters.sumOf { (it.content.length / safeAvg).coerceAtLeast(1) }
    }
    val currentBookPage = remember(chapterIdx, pageIdx, chapters, pageChunks.size, displayChapterContent.length) {
        val avgPageLen = if (pageChunks.isNotEmpty() && displayChapterContent.isNotEmpty()) (displayChapterContent.length / pageChunks.size) else 1500
        val safeAvg = avgPageLen.coerceIn(400, 3500)
        val priorPages = chapters.take(chapterIdx).sumOf { (it.content.length / safeAvg).coerceAtLeast(1) }
        (priorPages + pageIdx + 1).coerceAtMost(totalPagesInBook)
    }

    BackHandler {
        when {
            showLookupDialog -> {
                showLookupDialog = false
                selectedWordRange = null
                selectedWordText = ""
                activeSavedHighlightId = null
            }
            selectedWordRange != null -> {
                selectedWordRange = null
                selectedWordText = ""
                activeSavedHighlightId = null
            }
            showAnnotationsSheet -> showAnnotationsSheet = false
            showAudioSheet -> showAudioSheet = false
            showTocSheet -> showTocSheet = false
            showDisplaySheet -> showDisplaySheet = false
            showScrubberBar -> showScrubberBar = false
            showTtsMiniPlayer && !ttsState.isPlaying -> showTtsMiniPlayer = false
            else -> onBackToLibrary()
        }
    }

    fun triggerFlashThen(action: () -> Unit) {
        if (settings.pageFlashEnabled) {
            coroutineScope.launch {
                showFlashOverlay = true
                delay(75)
                action()
                delay(40)
                showFlashOverlay = false
            }
        } else {
            action()
        }
    }

    fun nextPage(isTts: Boolean = false) {
        if (isTts) {
            if (pageIdx < pages.size - 1) {
                pageIdx++
                repository.updateProgress(book.id, chapterIdx, pageIdx, pages.size)
                repository.statsRepository.recordPageTurn(currentPageText.split(Regex("\\s+")).size.coerceAtLeast(40))
            } else if (chapterIdx < chapters.size - 1) {
                chapterIdx++
                pageIdx = 0
                repository.updateProgress(book.id, chapterIdx, 0, pages.size)
                repository.statsRepository.recordPageTurn(currentPageText.split(Regex("\\s+")).size.coerceAtLeast(40))
            }
        } else {
            if (pageIdx < pages.size - 1) {
                triggerFlashThen {
                    pageIdx++
                    repository.updateProgress(book.id, chapterIdx, pageIdx, pages.size)
                    repository.statsRepository.recordPageTurn(currentPageText.split(Regex("\\s+")).size.coerceAtLeast(40))
                }
            } else if (chapterIdx < chapters.size - 1) {
                triggerFlashThen {
                    chapterIdx++
                    pageIdx = 0
                    repository.updateProgress(book.id, chapterIdx, 0, pages.size)
                    repository.statsRepository.recordPageTurn(currentPageText.split(Regex("\\s+")).size.coerceAtLeast(40))
                }
            }
        }
    }

    fun prevPage() {
        if (pageIdx > 0) {
            triggerFlashThen {
                pageIdx--
                repository.updateProgress(book.id, chapterIdx, pageIdx, pages.size)
            }
        } else if (chapterIdx > 0) {
            triggerFlashThen {
                chapterIdx--
                pageIdx = Int.MAX_VALUE // Auto clamped by LaunchedEffect(pages.size) to last page of prev chapter
                repository.updateProgress(book.id, chapterIdx, 0, pages.size)
            }
        }
    }

    // Automatically sync active page text to TTS engine and support gapless auto page turning
    LaunchedEffect(currentPageText, nextPageText) {
        if (currentPageText.isNotBlank()) {
            repository.ttsEngine.setContent(
                text = currentPageText,
                startIndex = 0,
                nextPageText = nextPageText,
                onPageEndReached = {
                    nextPage(isTts = true)
                }
            )
        }
    }

    // Automatically reveal floating mini-player whenever TTS starts playing
    LaunchedEffect(ttsState.isPlaying) {
        if (ttsState.isPlaying) {
            showTtsMiniPlayer = true
        }
    }

    // Physical Volume Button Page Turns
    LaunchedEffect(Unit) {
        repository.volumePageTurnEvents.collect { isNext ->
            if (isNext) nextPage() else prevPage()
        }
    }

    // Time calculations
    val estMinsInChapter = remember(pages.size, pageIdx) {
        ((pages.size - 1 - pageIdx).coerceAtLeast(0) * 1.5).toInt().coerceAtLeast(1)
    }

    val estMinsInBook = remember(pages.size, pageIdx, chapterIdx, chapters.size) {
        val remChapters = (chapters.size - 1 - chapterIdx).coerceAtLeast(0)
        ((pages.size - 1 - pageIdx) * 1.5 + remChapters * 8.0).toInt().coerceAtLeast(1)
    }

    val bookHighlights = remember(highlights, book.id) {
        highlights.filter { it.bookId == book.id }
    }

    val chapterHighlights = remember(bookHighlights, chapterIdx) {
        bookHighlights.filter { it.chapterIndex == chapterIdx }
    }

    val isDark = colors.background == Color.Black || colors.background == Color(0xFF111111)
    val persistentHighlightBg = if (isDark) Color(0xFF383838) else Color(0xFFE5DFC9)
    val ttsHighlightBg = if (isDark) Color(0xFF4A3B18) else Color(0xFFFFF0B3)
    val ttsHighlightText = if (isDark) Color(0xFFFFE082) else Color(0xFF1B1B1B)

    val annotatedPageText: AnnotatedString = remember(
        currentPageText,
        selectedWordRange,
        chapterHighlights,
        currentChunk,
        colors.text,
        colors.background,
        persistentHighlightBg,
        ttsHighlightBg,
        ttsHighlightText,
        settings.bionicReadingEnabled,
        ttsState.isPlaying,
        ttsState.currentStartOffset,
        ttsState.currentEndOffset
    ) {
        buildAnnotatedString {
            append(currentPageText)

            // Bionic Reading fixation points bolding
            if (settings.bionicReadingEnabled) {
                var idx = 0
                val len = currentPageText.length
                while (idx < len) {
                    while (idx < len && !currentPageText[idx].isLetterOrDigit()) {
                        idx++
                    }
                    if (idx >= len) break
                    val wStart = idx
                    while (idx < len && currentPageText[idx].isLetterOrDigit()) {
                        idx++
                    }
                    val wEnd = idx
                    val wLen = wEnd - wStart
                    val boldCount = when {
                        wLen <= 3 -> 1
                        wLen <= 5 -> 2
                        wLen <= 8 -> 3
                        wLen <= 11 -> 4
                        else -> (wLen * 0.45).toInt().coerceAtLeast(4)
                    }.coerceAtMost(wLen)

                    addStyle(
                        style = SpanStyle(fontWeight = FontWeight.Bold),
                        start = wStart,
                        end = wStart + boldCount
                    )
                }
            }

            // Render persistent saved highlights for this chapter that intersect current page
            for (hl in chapterHighlights) {
                if (hl.startCharOffset > 0 || hl.endCharOffset > 0) {
                    val intersectStart = maxOf(hl.startCharOffset, currentChunk.startInChapter)
                    val intersectEnd = minOf(hl.endCharOffset, currentChunk.endInChapter)
                    if (intersectStart < intersectEnd) {
                        val localStart = (intersectStart - currentChunk.startInChapter).coerceIn(0, currentPageText.length)
                        val localEnd = (intersectEnd - currentChunk.startInChapter).coerceIn(0, currentPageText.length)
                        if (localStart < localEnd) {
                            addStyle(
                                style = SpanStyle(
                                    background = persistentHighlightBg,
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = TextDecoration.Underline
                                ),
                                start = localStart,
                                end = localEnd
                            )
                        }
                    }
                } else if (hl.pageIndex == pageIdx) {
                    // Legacy fallback: highlight first occurrence on this page
                    val foundIdx = currentPageText.indexOf(hl.selectedText, ignoreCase = true)
                    if (foundIdx != -1) {
                        addStyle(
                            style = SpanStyle(
                                background = persistentHighlightBg,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.Underline
                            ),
                            start = foundIdx,
                            end = (foundIdx + hl.selectedText.length).coerceAtMost(currentPageText.length)
                        )
                    }
                }
            }

            // Real-time TTS Spoken Sentence Highlight
            if (ttsState.isPlaying && ttsState.currentStartOffset in 0..currentPageText.length && ttsState.currentEndOffset in 0..currentPageText.length && ttsState.currentStartOffset < ttsState.currentEndOffset) {
                addStyle(
                    style = SpanStyle(
                        background = ttsHighlightBg,
                        color = ttsHighlightText,
                        fontWeight = FontWeight.SemiBold
                    ),
                    start = ttsState.currentStartOffset,
                    end = ttsState.currentEndOffset
                )
            }

            // Render temporary active word selection on top
            selectedWordRange?.let { (start, end) ->
                if (start in 0..currentPageText.length && end in 0..currentPageText.length && start < end) {
                    addStyle(
                        style = SpanStyle(
                            background = colors.text,
                            color = colors.background,
                            fontWeight = FontWeight.Bold
                        ),
                        start = start,
                        end = end
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            // HAIRLINE HEADER
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBackToLibrary,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Library",
                            tint = colors.text,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Table of Contents
                    IconButton(
                        onClick = { showTocSheet = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.FormatListBulleted,
                            contentDescription = "Table of Contents",
                            tint = colors.text,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Bookmarks & Highlights
                    IconButton(
                        onClick = { showAnnotationsSheet = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.BookmarkBorder,
                            contentDescription = "Notes & Bookmarks",
                            tint = colors.text,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Ambient Audio & Sound Settings (Sheet)
                    IconButton(
                        onClick = {
                            showAudioSheet = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (ambientSound != AmbientSoundType.OFF || activeCustomSoundId != null) {
                            Text(activeSoundIcon, fontSize = 14.sp)
                        } else {
                            Icon(
                                Icons.Default.Headphones,
                                contentDescription = "Audio & Ambient Sounds",
                                tint = colors.text,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // TTS Read Aloud / Mini-Player Play Button
                    IconButton(
                        onClick = {
                            if (!showTtsMiniPlayer) {
                                showTtsMiniPlayer = true
                                if (!ttsState.isPlaying && currentPageText.isNotBlank()) {
                                    val nextText = if (pageIdx + 1 < pages.size) pages[pageIdx + 1] else ""
                                    repository.ttsEngine.setContent(
                                        currentPageText,
                                        startIndex = ttsState.currentSentenceIndex.coerceAtLeast(0),
                                        nextPageText = nextText
                                    ) {
                                        coroutineScope.launch { nextPage() }
                                    }
                                    repository.ttsEngine.play()
                                }
                            } else {
                                if (ttsState.isPlaying) {
                                    repository.ttsEngine.pause()
                                } else {
                                    if (currentPageText.isNotBlank() && ttsState.totalSentences == 0) {
                                        val nextText = if (pageIdx + 1 < pages.size) pages[pageIdx + 1] else ""
                                        repository.ttsEngine.setContent(
                                            currentPageText,
                                            startIndex = ttsState.currentSentenceIndex.coerceAtLeast(0),
                                            nextPageText = nextText
                                        ) {
                                            coroutineScope.launch { nextPage() }
                                        }
                                    }
                                    repository.ttsEngine.play()
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (ttsState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (ttsState.isPlaying) "Pause TTS" else "Read Aloud",
                            tint = if (ttsState.isPlaying || showTtsMiniPlayer) colors.text else colors.muted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // AI Companion
                    IconButton(
                        onClick = {
                            aiInitialQuery = null
                            showAiSheet = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "AI Reading Companion",
                            tint = colors.text,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Appearance
                    IconButton(
                        onClick = { showDisplaySheet = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Appearance & Settings",
                            tint = colors.text,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Scrubber
                    IconButton(
                        onClick = { showScrubberBar = !showScrubberBar },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.FastForward,
                            contentDescription = "Fast Scrubber",
                            tint = if (showScrubberBar) colors.text else colors.muted,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = currentChapter.title,
                        fontFamily = currentFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${book.progressPercent}%",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                }
            }
        }

            HorizontalDivider(thickness = 1.dp, color = colors.border)
            Spacer(Modifier.height(8.dp))

            // MAIN READING VIEW
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(pageIdx, chapterIdx, pages.size) {
                        var totalDragX = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDragX = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDragX += dragAmount
                            },
                            onDragEnd = {
                                val threshold = 35.dp.toPx()
                                if (totalDragX < -threshold) {
                                    nextPage()
                                } else if (totalDragX > threshold) {
                                    prevPage()
                                }
                            }
                        )
                    }
                    .pointerInput(pageIdx, chapterIdx, currentPageText, chapterHighlights, currentChunk) {
                        detectTapGestures(
                            onLongPress = { offset ->
                                val layout = textLayoutResult
                                if (layout != null) {
                                    val charOffset = layout.getOffsetForPosition(offset)
                                    val globalOffset = currentChunk.startInChapter + charOffset

                                    // 1. Check if user tapped inside an existing saved highlight
                                    val matchedHl = chapterHighlights.find { hl ->
                                        if (hl.startCharOffset > 0 || hl.endCharOffset > 0) {
                                            globalOffset in hl.startCharOffset until hl.endCharOffset
                                        } else {
                                            hl.pageIndex == pageIdx && hl.selectedText.isNotEmpty() &&
                                                currentPageText.indexOf(hl.selectedText).let { idx ->
                                                    idx != -1 && charOffset in idx until (idx + hl.selectedText.length)
                                                }
                                        }
                                    }

                                    if (matchedHl != null) {
                                        val localStart = if (matchedHl.startCharOffset > 0) {
                                            (matchedHl.startCharOffset - currentChunk.startInChapter).coerceIn(0, currentPageText.length)
                                        } else {
                                            currentPageText.indexOf(matchedHl.selectedText).coerceAtLeast(0)
                                        }
                                        val localEnd = if (matchedHl.endCharOffset > 0) {
                                            (matchedHl.endCharOffset - currentChunk.startInChapter).coerceIn(0, currentPageText.length)
                                        } else {
                                            (localStart + matchedHl.selectedText.length).coerceAtMost(currentPageText.length)
                                        }

                                        selectedWordRange = Pair(localStart, localEnd)
                                        selectedWordText = currentPageText.substring(localStart, localEnd)
                                        activeSavedHighlightId = matchedHl.id
                                    } else {
                                        // 2. Select word under touch
                                        val bounds = getWordBoundsAt(currentPageText, charOffset)
                                        if (bounds != null) {
                                            selectedWordRange = bounds
                                            selectedWordText = currentPageText.substring(bounds.first, bounds.second)
                                            activeSavedHighlightId = null
                                        }
                                    }
                                }
                            },
                            onTap = { offset ->
                                val width = size.width
                                val leftZone = width * 0.20f
                                val rightZone = width * 0.80f
                                when {
                                    offset.x < leftZone -> prevPage()
                                    offset.x > rightZone -> nextPage()
                                    else -> {
                                        // Center tap does NOT open menu! Only closes active popups/scrubber/selection
                                        if (showScrubberBar) showScrubberBar = false
                                        if (selectedWordRange != null) {
                                            selectedWordRange = null
                                            selectedWordText = ""
                                            activeSavedHighlightId = null
                                        }
                                    }
                                }
                            }
                        )
                    }
            ) {
                val boxWidthPx = constraints.maxWidth
                val boxHeightPx = (constraints.maxHeight - with(density) { 12.dp.roundToPx() }).coerceAtLeast(100)

                LaunchedEffect(boxWidthPx, boxHeightPx) {
                    if (boxWidthPx > 0 && boxHeightPx > 0 && (measuredWidthPx != boxWidthPx || measuredHeightPx != boxHeightPx)) {
                        measuredWidthPx = boxWidthPx
                        measuredHeightPx = boxHeightPx
                    }
                }

                Text(
                    text = annotatedPageText,
                    style = currentTextStyle,
                    color = colors.text,
                    onTextLayout = { textLayoutResult = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .padding(bottom = 8.dp)
                )

                // FLOATING READER SELECTION & HIGHLIGHT BAR
                if (selectedWordRange != null && selectedWordText.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = colors.card,
                        shadowElevation = 10.dp,
                        border = BorderStroke(1.5.dp, colors.border)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.card)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            // Top Row: Selected snippet preview + close
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "“${selectedWordText.take(40)}${if (selectedWordText.length > 40) "…" else ""}”",
                                    fontFamily = BookFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = colors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        selectedWordRange = null
                                        selectedWordText = ""
                                        activeSavedHighlightId = null
                                    },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.text, modifier = Modifier.size(16.dp))
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            // Middle Row: Expand selection controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        selectedWordRange?.let { (s, e) ->
                                            val newStart = expandSelectionLeft(currentPageText, s)
                                            selectedWordRange = Pair(newStart, e)
                                            selectedWordText = currentPageText.substring(newStart, e)
                                            activeSavedHighlightId = null
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                                    border = BorderStroke(1.dp, colors.border),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("◀ Word", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        selectedWordRange?.let { (s, e) ->
                                            val newEnd = expandSelectionRight(currentPageText, e)
                                            selectedWordRange = Pair(s, newEnd)
                                            selectedWordText = currentPageText.substring(s, newEnd)
                                            activeSavedHighlightId = null
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                                    border = BorderStroke(1.dp, colors.border),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Word ▶", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        selectedWordRange?.let { (s, _) ->
                                            val (sentStart, sentEnd) = getSentenceBoundsAt(currentPageText, s)
                                            selectedWordRange = Pair(sentStart, sentEnd)
                                            selectedWordText = currentPageText.substring(sentStart, sentEnd)
                                            activeSavedHighlightId = null
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                                    border = BorderStroke(1.dp, colors.border),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("◀ Sentence ▶", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(thickness = 0.5.dp, color = colors.border)
                            Spacer(Modifier.height(8.dp))

                            // Bottom Row: Actions (Highlight/Remove, Define, Translate, Copy)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (activeSavedHighlightId != null) {
                                    Button(
                                        onClick = {
                                            activeSavedHighlightId?.let { repository.removeHighlight(it) }
                                            selectedWordRange = null
                                            selectedWordText = ""
                                            activeSavedHighlightId = null
                                            Toast.makeText(context, "Highlight removed", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.text, contentColor = colors.background),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Remove", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            selectedWordRange?.let { (s, e) ->
                                                val snippet = currentPageText.substring(s, e).trim()
                                                if (snippet.isNotBlank()) {
                                                    val globalStart = currentChunk.startInChapter + s
                                                    val globalEnd = currentChunk.startInChapter + e
                                                    val hl = Highlight(
                                                        id = UUID.randomUUID().toString(),
                                                        bookId = book.id,
                                                        chapterIndex = chapterIdx,
                                                        pageIndex = pageIdx,
                                                        selectedText = snippet,
                                                        startCharOffset = globalStart,
                                                        endCharOffset = globalEnd
                                                    )
                                                    repository.addHighlight(hl)
                                                    Toast.makeText(context, "Highlight saved", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            selectedWordRange = null
                                            selectedWordText = ""
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.text, contentColor = colors.background),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Default.Highlight, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Highlight", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        val word = selectedWordText.trim()
                                        currentDefinition = DictionaryService.lookup(word)
                                        currentTranslation = null
                                        currentAiExplanation = null
                                        showLookupDialog = true
                                        // Auto-record to Vocabulary Deck
                                        repository.vocabularyRepository.addWord(
                                            word = word,
                                            definition = currentDefinition?.definition ?: "",
                                            contextSentence = currentPageText.take(140).replace("\n", " "),
                                            bookTitle = book.title,
                                            bookAuthor = book.author
                                        )
                                        coroutineScope.launch {
                                            val live = DictionaryService.lookupLive(word)
                                            currentDefinition = live
                                            if (live.isLiveFetched) {
                                                repository.vocabularyRepository.addWord(
                                                    word = word,
                                                    phonetic = live.phonetic,
                                                    partOfSpeech = live.partOfSpeech,
                                                    definition = live.definition,
                                                    contextSentence = currentPageText.take(140).replace("\n", " "),
                                                    bookTitle = book.title,
                                                    bookAuthor = book.author
                                                )
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                                    border = BorderStroke(1.dp, colors.border),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Define", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val word = selectedWordText.trim()
                                        currentTranslation = DictionaryService.translate(word, "en", "sr")
                                        currentDefinition = null
                                        currentAiExplanation = null
                                        showLookupDialog = true
                                        repository.vocabularyRepository.addWord(
                                            word = word,
                                            definition = "Prevod: ${currentTranslation?.translatedText ?: ""}",
                                            contextSentence = currentPageText.take(140).replace("\n", " "),
                                            bookTitle = book.title,
                                            bookAuthor = book.author
                                        )
                                        coroutineScope.launch {
                                            val live = DictionaryService.translateLive(word, "en", "sr")
                                            currentTranslation = live
                                            repository.vocabularyRepository.addWord(
                                                word = word,
                                                definition = "Prevod: ${live.translatedText}",
                                                contextSentence = currentPageText.take(140).replace("\n", " "),
                                                bookTitle = book.title,
                                                bookAuthor = book.author
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                                    border = BorderStroke(1.dp, colors.border),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Translate", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        aiInitialQuery = selectedWordText.trim()
                                        showAiSheet = true
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                                    border = BorderStroke(1.dp, colors.border),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("AI", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        showQuoteCardDialog = true
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                                    border = BorderStroke(1.dp, colors.border),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.FormatQuote, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("Card", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("InkReader Selection", selectedWordText))
                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        selectedWordRange = null
                                        selectedWordText = ""
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                                    border = BorderStroke(1.dp, colors.border),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Copy", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val textToSpeak = selectedWordText.trim()
                                        if (textToSpeak.isNotBlank()) {
                                            repository.ttsEngine.speak(textToSpeak)
                                            showTtsMiniPlayer = true
                                        }
                                        selectedWordRange = null
                                        selectedWordText = ""
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                                    border = BorderStroke(1.dp, colors.border),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("Speak", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // FLOATING ON-SCREEN TTS MINI-PLAYER OVERLAY
                if (showTtsMiniPlayer) {
                    TtsMiniPlayerOverlay(
                        ttsState = ttsState,
                        onPlayPause = { repository.ttsEngine.togglePlayPause() },
                        onPrevious = { repository.ttsEngine.skipPrevious() },
                        onNext = { repository.ttsEngine.skipNext() },
                        onStop = {
                            repository.ttsEngine.pause()
                            showTtsMiniPlayer = false
                        },
                        onCycleSpeed = {
                            val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                            val nextIdx = (speeds.indexOfFirst { kotlin.math.abs(it - ttsState.speed) < 0.05f } + 1) % speeds.size
                            repository.ttsEngine.setSpeed(speeds[nextIdx])
                        },
                        onOpenAudioSheet = { showAudioSheet = true },
                        onClose = {
                            showTtsMiniPlayer = false
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // 100% OPAQUE FLOATING PAGE SCRUBBER OVERLAY
                if (showScrubberBar) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = colors.card,
                        shadowElevation = 10.dp,
                        border = BorderStroke(1.5.dp, colors.border)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.card)
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Pg ${pageIdx + 1}/${pages.size} • Book $currentBookPage/$totalPagesInBook",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.text
                                )
                                IconButton(
                                    onClick = { showScrubberBar = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.text, modifier = Modifier.size(16.dp))
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            Slider(
                                value = pageIdx.toFloat(),
                                onValueChange = { newPage ->
                                    pageIdx = newPage.toInt().coerceIn(0, pages.size - 1)
                                    repository.updateProgress(book.id, chapterIdx, pageIdx, pages.size)
                                },
                                valueRange = 0f..(pages.size - 1).coerceAtLeast(1).toFloat(),
                                steps = (pages.size - 2).coerceAtLeast(0),
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.text,
                                    activeTrackColor = colors.text,
                                    inactiveTrackColor = colors.muted
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (chapterIdx > 0) {
                                            chapterIdx--
                                            pageIdx = 0
                                            repository.updateProgress(book.id, chapterIdx, 0, pages.size)
                                        }
                                    },
                                    enabled = chapterIdx > 0,
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                                    border = ButtonDefaults.outlinedButtonBorder(enabled = chapterIdx > 0).copy(brush = SolidColor(colors.border))
                                ) {
                                    Text("◀ Prev Ch.", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }

                                Text(
                                    text = "Ch ${chapterIdx + 1} / ${chapters.size}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = colors.muted
                                )

                                OutlinedButton(
                                    onClick = {
                                        if (chapterIdx < chapters.size - 1) {
                                            chapterIdx++
                                            pageIdx = 0
                                            repository.updateProgress(book.id, chapterIdx, 0, pages.size)
                                        }
                                    },
                                    enabled = chapterIdx < chapters.size - 1,
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                                    border = ButtonDefaults.outlinedButtonBorder(enabled = chapterIdx < chapters.size - 1).copy(brush = SolidColor(colors.border))
                                ) {
                                    Text("Next Ch. ▶", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // CUSTOMIZABLE HAIRLINE FOOTER
            HorizontalDivider(thickness = 1.dp, color = colors.border)
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDisplaySheet = true }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (settings.footerShowChapterPages) {
                        Text(
                            text = "Ch. ${pageIdx + 1}/${pages.size}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = colors.muted
                        )
                    }
                    if (settings.footerShowBookPages) {
                        Text(
                            text = "• Book $currentBookPage/$totalPagesInBook",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = colors.muted
                        )
                    }
                }

                IconButton(
                    onClick = { showScrubberBar = !showScrubberBar },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.FastForward,
                        contentDescription = "Timeline Scrubber",
                        tint = if (showScrubberBar) colors.text else colors.muted,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (settings.footerShowChapterTime) {
                        Text(
                            text = "$estMinsInChapter min in ch",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = colors.muted
                        )
                    }
                    if (settings.footerShowBookTime) {
                        val hours = estMinsInBook / 60
                        val mins = estMinsInBook % 60
                        val bookTimeStr = if (hours > 0) "${hours}h ${mins}m left" else "${mins}m left"
                        Text(
                            text = "• $bookTimeStr",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = colors.muted
                        )
                    }
                }
            }
        }

        if (showFlashOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        if (showTocSheet) {
            ModalBottomSheet(
                onDismissRequest = { showTocSheet = false },
                containerColor = colors.card,
                contentColor = colors.text,
                shape = RoundedCornerShape(0.dp)
            ) {
                TableOfContentsSheet(
                    book = book,
                    currentChapterIndex = chapterIdx,
                    onChapterSelect = { idx: Int ->
                        chapterIdx = idx
                        pageIdx = 0
                        repository.jumpToChapter(book.id, idx)
                        showTocSheet = false
                    },
                    onDismiss = { showTocSheet = false }
                )
            }
        }

        if (showAnnotationsSheet) {
            AnnotationsSheet(
                repository = repository,
                book = book,
                onJumpToLocation = { chIdx, pIdx ->
                    chapterIdx = chIdx.coerceIn(0, chapters.size - 1)
                    pageIdx = pIdx.coerceIn(0, pages.size - 1)
                    repository.updateProgress(book.id, chapterIdx, pageIdx, pages.size)
                },
                onDismiss = { showAnnotationsSheet = false }
            )
        }

        if (showAudioSheet) {
            AmbientAudioSheet(
                audioEngine = repository.audioEngine,
                ttsEngine = repository.ttsEngine,
                currentText = currentPageText,
                onPageEndReached = {
                    if (pageIdx < pages.size - 1) {
                        pageIdx++
                        repository.updateProgress(book.id, chapterIdx, pageIdx, pages.size)
                    } else if (chapterIdx < chapters.size - 1) {
                        chapterIdx++
                        pageIdx = 0
                        repository.updateProgress(book.id, chapterIdx, 0, pages.size)
                    }
                },
                onDismiss = { 
                    showAudioSheet = false
                    if (ttsState.isPlaying) {
                        showTtsMiniPlayer = true
                    }
                }
            )
        }

        if (showDisplaySheet) {
            ModalBottomSheet(
                onDismissRequest = { showDisplaySheet = false },
                containerColor = colors.card,
                contentColor = colors.text,
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .width(40.dp)
                            .height(3.dp)
                            .background(colors.border)
                    )
                }
            ) {
                DisplayAndSoundSheetContent(
                    repository = repository,
                    book = book,
                    currentChapterTitle = currentChapter.title,
                    currentChapterText = currentChapter.content,
                    onOpenLookup = { def: WordDefinition?, trans: TranslationResult?, ai: AiExplanation? ->
                        currentDefinition = def
                        currentTranslation = trans
                        currentAiExplanation = ai
                        showDisplaySheet = false
                        showLookupDialog = true
                    },
                    onOpenToc = {
                        showDisplaySheet = false
                        showTocSheet = true
                    },
                    onOpenScrubber = {
                        showDisplaySheet = false
                        showScrubberBar = true
                    },
                    onOpenAnnotations = {
                        showDisplaySheet = false
                        showAnnotationsSheet = true
                    },
                    onOpenAudio = {
                        showDisplaySheet = false
                        showAudioSheet = true
                    },
                    onDismiss = { showDisplaySheet = false }
                )
            }
        }

        if (showLookupDialog) {
            LookupDialog(
                definition = currentDefinition,
                translation = currentTranslation,
                aiExplanation = currentAiExplanation,
                selectedText = selectedWordText,
                onBookmarkPage = {
                    val excerpt = currentPageText.take(120).replace("\n", " ") + "..."
                    val bm = Bookmark(
                        id = UUID.randomUUID().toString(),
                        bookId = book.id,
                        chapterIndex = chapterIdx,
                        pageIndex = pageIdx,
                        chapterTitle = currentChapter.title,
                        excerpt = excerpt
                    )
                    repository.addBookmark(bm)
                    Toast.makeText(context, "Page bookmarked", Toast.LENGTH_SHORT).show()
                },
                onHighlightText = {
                    if (selectedWordText.isNotBlank()) {
                        val (s, e) = selectedWordRange ?: Pair(0, selectedWordText.length)
                        val globalStart = currentChunk.startInChapter + s
                        val globalEnd = currentChunk.startInChapter + e
                        val hl = Highlight(
                            id = UUID.randomUUID().toString(),
                            bookId = book.id,
                            chapterIndex = chapterIdx,
                            pageIndex = pageIdx,
                            selectedText = selectedWordText.trim(),
                            startCharOffset = globalStart,
                            endCharOffset = globalEnd
                        )
                        repository.addHighlight(hl)
                        Toast.makeText(context, "Highlight saved", Toast.LENGTH_SHORT).show()
                    }
                },
                onCopyText = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("InkReader Selection", selectedWordText))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                onDismiss = {
                    showLookupDialog = false
                    selectedWordRange = null
                    selectedWordText = ""
                    activeSavedHighlightId = null
                    currentDefinition = null
                    currentTranslation = null
                    currentAiExplanation = null
                }
            )
        }

        if (showAiSheet) {
            val currentProgress = if (book.progressPercent > 0) {
                book.progressPercent
            } else {
                (((chapterIdx + 1).toFloat() / maxOf(1, book.chapters.size)) * 100).toInt()
            }
            AiCompanionSheet(
                aiService = repository.aiCompanionService,
                bookTitle = book.title,
                bookAuthor = book.author,
                currentChapterTitle = currentChapter.title,
                currentChapterIndex = chapterIdx + 1,
                totalChapters = (book.chapters.size).coerceAtLeast(1),
                bookProgressPercent = currentProgress.coerceIn(0, 100),
                currentExcerpt = currentPageText,
                initialQuery = aiInitialQuery,
                onDismiss = {
                    showAiSheet = false
                    aiInitialQuery = null
                }
            )
        }

        if (showQuoteCardDialog) {
            QuoteCardDialog(
                quote = selectedWordText.ifBlank { currentPageText.take(160) },
                bookTitle = book.title,
                bookAuthor = book.author,
                onDismiss = {
                    showQuoteCardDialog = false
                }
            )
        }
    }
}

@Composable
fun TableOfContentsSheet(
    book: Book,
    currentChapterIndex: Int,
    onChapterSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalEInkColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Table of Contents",
                fontFamily = BookFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = colors.text
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.text)
            }
        }

        Text(
            text = "${book.title} • ${book.chapters.size} Chapters",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = colors.muted
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(thickness = 1.dp, color = colors.border)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            itemsIndexed(book.chapters) { idx, chapter ->
                val isCurrent = idx == currentChapterIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isCurrent) 1.5.dp else 0.5.dp,
                            color = if (isCurrent) colors.text else colors.border,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .background(if (isCurrent) colors.background else colors.card)
                        .clickable { onChapterSelect(idx) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chapter.title,
                            fontFamily = BookFontFamily,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp,
                            color = colors.text
                        )
                        Text(
                            text = "${chapter.wordCount} words",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = colors.muted
                        )
                    }

                    if (isCurrent) {
                        Text(
                            text = "● Reading",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = colors.text
                        )
                    } else if (idx < currentChapterIndex) {
                        Text(
                            text = "✓ Read",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = colors.muted
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun DisplayAndSoundSheetContent(
    repository: ReaderRepository,
    book: Book,
    currentChapterTitle: String,
    currentChapterText: String,
    onOpenLookup: (WordDefinition?, TranslationResult?, AiExplanation?) -> Unit,
    onOpenToc: () -> Unit,
    onOpenScrubber: () -> Unit,
    onOpenAnnotations: () -> Unit,
    onOpenAudio: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalEInkColors.current
    val context = LocalContext.current
    val settings by repository.settings.collectAsState()
    var fontTrigger by remember { mutableIntStateOf(0) }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val installed = CustomFontManager.installFontFromUri(context, uri)
            if (installed != null) {
                repository.setFontId(installed.id)
                fontTrigger++
                Toast.makeText(context, "Installed font: ${installed.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Could not load font file (.ttf / .otf)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val availableFonts = remember(context, fontTrigger) {
        CustomFontManager.getAvailableFonts(context)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Display & Typography",
                    fontFamily = BookFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colors.text
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.text)
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(thickness = 1.dp, color = colors.border)
            Spacer(Modifier.height(14.dp))

            // NAVIGATION BUTTONS (TOC, Notes, Scrubber, Audio)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenToc,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = SolidColor(colors.border))
                ) {
                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("TOC", fontFamily = BookFontFamily, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onOpenAnnotations,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = SolidColor(colors.border))
                ) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Notes", fontFamily = BookFontFamily, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onOpenAudio,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = SolidColor(colors.border))
                ) {
                    Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Audio", fontFamily = BookFontFamily, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(14.dp))

            // KOREADER-STYLE FONT PICKER & USER CUSTOM FONTS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Font Family", fontFamily = BookFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.text)
                OutlinedButton(
                    onClick = {
                        fontPickerLauncher.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-opentype", "application/octet-stream", "*/*"))
                    },
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.text)
                    Spacer(Modifier.width(4.dp))
                    Text("Install Font (.ttf/.otf)", fontSize = 11.sp, color = colors.text)
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                availableFonts.forEach { fontItem ->
                    val isSelected = settings.selectedFontId == fontItem.id
                    Box(
                        modifier = Modifier
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) colors.text else colors.border,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .background(if (isSelected) colors.background else colors.card)
                            .clickable { repository.setFontId(fontItem.id) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = fontItem.name,
                                fontFamily = fontItem.fontFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                                color = colors.text
                            )
                            if (fontItem.isCustom) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove font",
                                    tint = colors.muted,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            CustomFontManager.deleteCustomFont(context, fontItem.id)
                                            if (settings.selectedFontId == fontItem.id) {
                                                repository.setFontId("serif")
                                            }
                                            fontTrigger++
                                        }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // LINE SPACING MULTIPLIER
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Line Spacing", fontFamily = BookFontFamily, fontSize = 15.sp, color = colors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1.30f to "Compact", 1.50f to "Normal", 1.80f to "Relaxed").forEach { (mult, label) ->
                        val isCurrent = kotlin.math.abs(settings.lineSpacingMultiplier - mult) < 0.05f
                        Box(
                            modifier = Modifier
                                .border(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = if (isCurrent) colors.text else colors.border,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .background(if (isCurrent) colors.background else colors.card)
                                .clickable { repository.setLineSpacingMultiplier(mult) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = colors.text
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // TEXT SIZE
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Text size", fontFamily = BookFontFamily, fontSize = 15.sp, color = colors.text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A", fontFamily = BookFontFamily, fontSize = 13.sp, color = colors.text)
                    Slider(
                        value = settings.fontSizeSp,
                        onValueChange = { repository.setFontSize(it) },
                        valueRange = 13f..26f,
                        steps = 5,
                        modifier = Modifier.width(170.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = colors.text,
                            activeTrackColor = colors.text,
                            inactiveTrackColor = colors.muted
                        )
                    )
                    Text("A", fontFamily = BookFontFamily, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = colors.text)
                }
            }

            Spacer(Modifier.height(12.dp))

            // THEME SELECTION
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Theme", fontFamily = BookFontFamily, fontSize = 15.sp, color = colors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EInkThemeMode.entries.forEach { mode ->
                        val isSelected = settings.themeMode == mode
                        Box(
                            modifier = Modifier
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = colors.text,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .background(
                                    when (mode) {
                                        EInkThemeMode.PAPER -> Color(0xFFF3F0E8)
                                        EInkThemeMode.WHITE -> Color(0xFFFFFFFF)
                                        EInkThemeMode.OLED_DARK -> Color(0xFF111111)
                                        EInkThemeMode.SEPIA -> Color(0xFFEFE7D8)
                                    }
                                )
                                .clickable { repository.setThemeMode(mode) }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = mode.displayName,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (mode == EInkThemeMode.OLED_DARK) Color.White else Color.Black
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(thickness = 1.dp, color = colors.border)
            Spacer(Modifier.height(14.dp))

            // FORMATTING STYLE
            Text(
                text = "Formatting & Layout",
                fontFamily = BookFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = colors.text
            )
            Spacer(Modifier.height(8.dp))

            SettingToggleRow(
                title = "⚡ Bionic Reading (Speed & Focus)",
                checked = settings.bionicReadingEnabled,
                onCheckedChange = { repository.toggleBionicReading(it) }
            )
            SettingToggleRow(
                title = "Book paragraph indent (Novel style)",
                checked = settings.paragraphIndentEnabled,
                onCheckedChange = { repository.toggleParagraphIndent(it) }
            )
            SettingToggleRow(
                title = "Publisher formatting (Left aligned)",
                checked = settings.usePublisherFormatting,
                onCheckedChange = { repository.togglePublisherFormatting(it) }
            )
            SettingToggleRow(
                title = "Page flash (e-ink refresh)",
                checked = settings.pageFlashEnabled,
                onCheckedChange = { repository.togglePageFlash(it) }
            )
            SettingToggleRow(
                title = "Focus mode (Keep screen on)",
                checked = settings.focusMode,
                onCheckedChange = { repository.toggleFocusMode(it) }
            )

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(thickness = 1.dp, color = colors.border)
            Spacer(Modifier.height(14.dp))

            // FOOTER DISPLAY OPTIONS
            Text(
                text = "Footer Display Options",
                fontFamily = BookFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = colors.text
            )
            Spacer(Modifier.height(8.dp))

            SettingToggleRow(
                title = "Show Pages in Chapter (Ch. 1 / 18)",
                checked = settings.footerShowChapterPages,
                onCheckedChange = { repository.toggleFooterChapterPages(it) }
            )
            SettingToggleRow(
                title = "Show Pages in Book (Book 42 / 380)",
                checked = settings.footerShowBookPages,
                onCheckedChange = { repository.toggleFooterBookPages(it) }
            )
            SettingToggleRow(
                title = "Show Minutes Left in Chapter",
                checked = settings.footerShowChapterTime,
                onCheckedChange = { repository.toggleFooterChapterTime(it) }
            )
            SettingToggleRow(
                title = "Show Total Minutes in Book",
                checked = settings.footerShowBookTime,
                onCheckedChange = { repository.toggleFooterBookTime(it) }
            )

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalEInkColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, fontFamily = BookFontFamily, fontSize = 14.sp, color = colors.text)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.background,
                checkedTrackColor = colors.text,
                uncheckedThumbColor = colors.text,
                uncheckedTrackColor = colors.background,
                uncheckedBorderColor = colors.border
            )
        )
    }
}

@Composable
fun LookupDialog(
    definition: WordDefinition?,
    translation: TranslationResult?,
    aiExplanation: AiExplanation?,
    selectedText: String = "",
    onBookmarkPage: () -> Unit = {},
    onHighlightText: () -> Unit = {},
    onCopyText: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val colors = LocalEInkColors.current
    var selectedTab by remember(definition, translation, aiExplanation) {
        mutableIntStateOf(if (definition != null) 0 else if (translation != null) 1 else 2)
    }

    val wordTitle = definition?.word ?: translation?.originalText ?: aiExplanation?.title ?: selectedText.ifEmpty { "Lookup" }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    onHighlightText()
                    onDismiss()
                }) {
                    Text("Highlight", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = colors.text)
                }
                TextButton(onClick = {
                    onCopyText()
                    onDismiss()
                }) {
                    Text("Copy", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = colors.text)
                }
                TextButton(onClick = onDismiss) {
                    Text("Done", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.text)
                }
            }
        },
        containerColor = colors.card,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.border(1.5.dp, colors.border, RoundedCornerShape(12.dp)),
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = wordTitle,
                    fontFamily = BookFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Spacer(Modifier.height(8.dp))
                // Tabs: [ 📖 Definition | 🌐 Translation | ✦ Context ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (definition != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, if (selectedTab == 0) colors.text else colors.border, RoundedCornerShape(4.dp))
                                .background(if (selectedTab == 0) colors.text else colors.card)
                                .clickable { selectedTab = 0 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "📖 Definition",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) colors.background else colors.text
                            )
                        }
                    }

                    if (translation != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, if (selectedTab == 1) colors.text else colors.border, RoundedCornerShape(4.dp))
                                .background(if (selectedTab == 1) colors.text else colors.card)
                                .clickable { selectedTab = 1 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "🌐 EN ⇄ SR",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) colors.background else colors.text
                            )
                        }
                    }

                    if (aiExplanation != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, if (selectedTab == 2) colors.text else colors.border, RoundedCornerShape(4.dp))
                                .background(if (selectedTab == 2) colors.text else colors.card)
                                .clickable { selectedTab = 2 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✦ Context",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 2) colors.background else colors.text
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (selectedTab) {
                    0 -> {
                        if (definition != null) {
                            Text(
                                text = "${definition.phonetic} • ${definition.partOfSpeech}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = colors.muted
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = definition.definition,
                                fontFamily = BookFontFamily,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                            if (definition.example != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "\"${definition.example}\"",
                                    fontFamily = BookFontFamily,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 13.sp,
                                    color = colors.muted
                                )
                            }
                        }
                    }
                    1 -> {
                        if (translation != null) {
                            Text(
                                text = "Original (${translation.sourceLang}):",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = colors.muted
                            )
                            Text(
                                text = translation.originalText,
                                fontFamily = BookFontFamily,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Serbian Translation (${translation.targetLang}):",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = colors.muted
                            )
                            Text(
                                text = translation.translatedText,
                                fontFamily = BookFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                    2 -> {
                        if (aiExplanation != null) {
                            Text(
                                text = aiExplanation.summary,
                                fontFamily = BookFontFamily,
                                fontSize = 14.sp,
                                lineHeight = 21.sp
                            )
                            if (aiExplanation.contextNote != null) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = aiExplanation.contextNote,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = colors.muted
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun TtsMiniPlayerOverlay(
    ttsState: UnifiedTtsState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onCycleSpeed: () -> Unit,
    onOpenAudioSheet: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEInkColors.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.card,
        shadowElevation = 10.dp,
        border = BorderStroke(1.5.dp, colors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Top Row: Voice & Status + Speed + Sheet + Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = when (ttsState.engineType) {
                            TtsEngineType.SUPERTONIC -> "${ttsState.supertonicVoice.emoji} ${ttsState.supertonicVoice.name} (${ttsState.supertonicQuality.steps} steps)"
                            TtsEngineType.PIPER -> "🚀 ${ttsState.piperVoice.name}"
                            TtsEngineType.KOKORO -> "${ttsState.kokoroVoice.emoji} ${ttsState.kokoroVoice.name}"
                            TtsEngineType.SYSTEM -> "⚡ System"
                        },
                        fontFamily = BookFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (ttsState.totalSentences > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colors.background,
                            border = BorderStroke(1.dp, colors.border)
                        ) {
                            Text(
                                text = "${ttsState.currentSentenceIndex + 1}/${ttsState.totalSentences}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = colors.muted,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (ttsState.isSynthesizing) {
                        Text(
                            text = "• buffering...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = colors.muted
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Speed Chip (Clickable)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colors.background,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.clickable { onCycleSpeed() }
                    ) {
                        Text(
                            text = "${ttsState.speed}x",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = colors.text,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // Open Full Audio Sheet
                    IconButton(
                        onClick = onOpenAudioSheet,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Voice & Sound Settings",
                            tint = colors.text,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Close / Dismiss Mini-Player
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close Mini-Player",
                            tint = colors.text,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Middle: Spoken snippet text preview
            if (ttsState.currentSentenceText.isNotBlank()) {
                Text(
                    text = "“${ttsState.currentSentenceText.replace('\n', ' ')}”",
                    fontFamily = BookFontFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 12.sp,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
            }

            // Bottom Row: Playback Controls [⏮ Prev | ⏯ Play/Pause | Next ⏭ | Stop ⏹]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prev Sentence
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous Sentence",
                        tint = colors.text,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Play / Pause Button (Prominent)
                Button(
                    onClick = onPlayPause,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.text,
                        contentColor = colors.background
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(
                        imageVector = if (ttsState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (ttsState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (ttsState.isPlaying) "Pause" else "Play",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // Next Sentence
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next Sentence",
                        tint = colors.text,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Stop Button
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop TTS",
                        tint = colors.text,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
