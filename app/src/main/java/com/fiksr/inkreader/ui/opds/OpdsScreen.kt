package com.fiksr.inkreader.ui.opds

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fiksr.inkreader.data.ReaderRepository
import com.fiksr.inkreader.data.opds.OpdsCatalog
import com.fiksr.inkreader.data.opds.OpdsEntry
import com.fiksr.inkreader.data.opds.OpdsFeed
import com.fiksr.inkreader.model.Book
import com.fiksr.inkreader.theme.BookFontFamily
import com.fiksr.inkreader.theme.LocalEInkColors
import com.fiksr.inkreader.ui.components.DitherCover
import kotlinx.coroutines.launch
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsScreen(
    repository: ReaderRepository,
    onBack: () -> Unit,
    onBookDownloadedAndOpen: (Book) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEInkColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val opdsRepo = repository.opdsRepository
    val catalogs by opdsRepo.catalogs.collectAsState()
    val downloadProgress by opdsRepo.downloadProgress.collectAsState()
    val boDownloadProgress by repository.bookOrbitClient.downloadProgress.collectAsState()
    val syncSettings by repository.syncClient.settings.collectAsState()

    var selectedCatalog by remember { mutableStateOf<OpdsCatalog?>(catalogs.firstOrNull()) }
    var currentFeedUrl by remember { mutableStateOf(selectedCatalog?.url ?: "") }
    var currentFeed by remember { mutableStateOf<OpdsFeed?>(null) }
    var feedHistory by remember { mutableStateOf<List<String>>(emptyList()) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCatalogPicker by remember { mutableStateOf(false) }
    var showAddCatalogDialog by remember { mutableStateOf(false) }
    var catalogToEdit by remember { mutableStateOf<OpdsCatalog?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isSearchLoading by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<OpdsEntry>>(emptyList()) }

    val libraryBooks by repository.books.collectAsState()
    var selectedBookForDetail by remember { mutableStateOf<OpdsEntry?>(null) }

    fun performOpdsSearch(cat: OpdsCatalog, q: String) {
        coroutineScope.launch {
            try {
                val encoded = java.net.URLEncoder.encode(q, "UTF-8")
                val searchTemplate = currentFeed?.searchHref
                val searchUrl = when {
                    searchTemplate != null && searchTemplate.contains("{searchTerms}") ->
                        searchTemplate.replace("{searchTerms}", encoded)
                    searchTemplate != null ->
                        if (searchTemplate.contains("?")) "$searchTemplate&q=$encoded" else "$searchTemplate?q=$encoded"
                    cat.url.contains("?") -> "${cat.url}&q=$encoded"
                    else -> "${cat.url}?q=$encoded"
                }
                val result = opdsRepo.fetchFeed(searchUrl, cat.username, cat.password)
                isSearchLoading = false
                result.fold(
                    onSuccess = { feed ->
                        searchResults = feed.entries
                        if (feed.entries.isEmpty() && feed.navigationLinks.isNotEmpty()) {
                            currentFeed = feed
                            isSearching = false
                        }
                    },
                    onFailure = { err ->
                        errorMessage = "Search failed: ${err.localizedMessage ?: err.message}"
                    }
                )
            } catch (e: Exception) {
                isSearchLoading = false
                errorMessage = "Search error: ${e.message}"
            }
        }
    }

    fun executeSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            isSearching = false
            searchResults = emptyList()
            return
        }
        val cat = selectedCatalog ?: return
        isSearching = true
        isSearchLoading = true
        errorMessage = null

        coroutineScope.launch {
            val isBookOrbit = cat.id == "bookorbit_default" || cat.url.contains("buks.lol") || cat.url.contains("bookorbit")
            val authUser = cat.username.ifBlank { syncSettings.username }.trim()
            val authPass = cat.password.ifBlank { syncSettings.userKey }.trim()
            val catalogOrigin = if (cat.url.startsWith("http")) {
                try {
                    val u = URL(cat.url)
                    "${u.protocol}://${u.host}${if (u.port != -1 && u.port != 80 && u.port != 443) ":${u.port}" else ""}"
                } catch (_: Exception) { "https://buks.lol" }
            } else "https://buks.lol"

            if (isBookOrbit) {
                val res = repository.bookOrbitClient.searchBooks(q)
                isSearchLoading = false
                res.fold(
                    onSuccess = { books ->
                        searchResults = books.map { b ->
                            val dlUrl = b.downloadUrl
                                ?: b.fileId?.let { "$catalogOrigin/api/v1/koreader/plugin/catalog/files/$it/download" }
                                ?: "$catalogOrigin/api/v1/koreader/plugin/catalog/books/${b.id}"
                            val cover = b.thumbnailUrl
                                ?: "$catalogOrigin/api/v1/koreader/plugin/catalog/books/${b.id}/thumbnail"
                            OpdsEntry(
                                id = b.id.toString(),
                                title = b.title,
                                author = b.authors.joinToString(", ").ifEmpty { "Unknown Author" },
                                summary = b.description ?: (b.seriesName?.let { "Series: $it" } ?: ""),
                                coverUrl = cover,
                                thumbnailUrl = cover,
                                acquisitionLinks = listOf(
                                    com.fiksr.inkreader.data.opds.OpdsAcquisitionLink(
                                        href = dlUrl,
                                        type = "application/epub+zip",
                                        title = "Download EPUB"
                                    )
                                )
                            )
                        }
                    },
                    onFailure = {
                        performOpdsSearch(cat, q)
                    }
                )
            } else {
                performOpdsSearch(cat, q)
            }
        }
    }

    fun loadFeed(url: String, addToHistory: Boolean = true) {
        val cat = selectedCatalog ?: return
        if (addToHistory && currentFeedUrl.isNotEmpty() && currentFeedUrl != url) {
            feedHistory = feedHistory + currentFeedUrl
        }
        currentFeedUrl = url
        isLoading = true
        errorMessage = null
        isSearching = false
        coroutineScope.launch {
            val result = opdsRepo.fetchFeed(url, cat.username, cat.password)
            isLoading = false
            result.fold(
                onSuccess = { feed ->
                    currentFeed = feed
                },
                onFailure = { err ->
                    errorMessage = err.localizedMessage ?: "Failed to load OPDS catalog feed."
                }
            )
        }
    }

    LaunchedEffect(selectedCatalog) {
        selectedCatalog?.let { cat ->
            feedHistory = emptyList()
            isSearching = false
            searchQuery = ""
            loadFeed(cat.url, addToHistory = false)
        }
    }

    BackHandler {
        if (isSearching) {
            isSearching = false
            searchQuery = ""
            searchResults = emptyList()
        } else if (feedHistory.isNotEmpty()) {
            val prevUrl = feedHistory.last()
            feedHistory = feedHistory.dropLast(1)
            loadFeed(prevUrl, addToHistory = false)
        } else {
            onBack()
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(
                    onClick = {
                        if (isSearching) {
                            isSearching = false
                            searchQuery = ""
                            searchResults = emptyList()
                        } else if (feedHistory.isNotEmpty()) {
                            val prevUrl = feedHistory.last()
                            feedHistory = feedHistory.dropLast(1)
                            loadFeed(prevUrl, addToHistory = false)
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.text,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedCatalog?.name ?: "OPDS Catalogs",
                        fontFamily = BookFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isSearching) {
                        Text(
                            text = "Search: \"$searchQuery\"",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (currentFeed != null && currentFeed?.title != selectedCatalog?.name) {
                        Text(
                            text = currentFeed?.title ?: "",
                            fontFamily = BookFontFamily,
                            fontSize = 11.sp,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Edit Current Active Catalog
                if (selectedCatalog != null) {
                    IconButton(
                        onClick = { catalogToEdit = selectedCatalog },
                        modifier = Modifier
                            .border(1.dp, colors.border)
                            .size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Catalog",
                            tint = colors.text,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Catalog switch dropdown button
                IconButton(
                    onClick = { showCatalogPicker = true },
                    modifier = Modifier
                        .border(1.dp, colors.border)
                        .size(34.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.LibraryBooks,
                        contentDescription = "Switch Catalog",
                        tint = colors.text,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Add custom catalog
                IconButton(
                    onClick = { showAddCatalogDialog = true },
                    modifier = Modifier
                        .border(1.dp, colors.border)
                        .size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Catalog",
                        tint = colors.text,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // SEARCH BAR FOR 20k+ BOOKS
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                if (it.isEmpty() && isSearching) {
                    isSearching = false
                    searchResults = emptyList()
                }
            },
            placeholder = {
                Text(
                    "Search ${selectedCatalog?.name ?: "Catalog"} (title, author)...",
                    fontFamily = BookFontFamily,
                    fontSize = 13.sp,
                    color = colors.muted
                )
            },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = colors.muted)
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            isSearching = false
                            searchResults = emptyList()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = colors.muted)
                        }
                        IconButton(onClick = { executeSearch(searchQuery) }) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Search", tint = colors.text)
                        }
                    }
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { executeSearch(searchQuery) }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(0.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.text,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text,
                focusedContainerColor = colors.background,
                unfocusedContainerColor = colors.background
            )
        )

        HorizontalDivider(thickness = 1.dp, color = colors.border)
        Spacer(Modifier.height(8.dp))

        // FEED OR SEARCH CONTENT
        if (isSearching) {
            if (isSearchLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = colors.text, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Searching '${searchQuery}'...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = colors.muted
                        )
                    }
                }
            } else if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No Books Found",
                            fontFamily = BookFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = colors.text
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No results matched '${searchQuery}' in ${selectedCatalog?.name ?: "this catalog"}.",
                            fontFamily = BookFontFamily,
                            fontSize = 13.sp,
                            color = colors.muted
                        )
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                isSearching = false
                                searchQuery = ""
                                searchResults = emptyList()
                            },
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            Text("Clear Search", fontSize = 12.sp, color = colors.text)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SEARCH RESULTS (${searchResults.size})",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.muted
                            )
                            TextButton(onClick = {
                                isSearching = false
                                searchQuery = ""
                                searchResults = emptyList()
                            }) {
                                Text("Close Results", fontSize = 11.sp, color = colors.text)
                            }
                        }
                    }

                    items(searchResults, key = { it.id }) { entry ->
                        val cat = selectedCatalog
                        val authUser = cat?.username?.ifBlank { syncSettings.username } ?: syncSettings.username
                        val authPass = cat?.password?.ifBlank { syncSettings.userKey } ?: syncSettings.userKey
                        val catalogBaseUrl = cat?.url ?: "https://buks.lol"

                        val progress = downloadProgress[entry.id] ?: entry.id.toIntOrNull()?.let { boDownloadProgress[it] }
                        val isDownloading = progress != null

                        val koUser = syncSettings.username.ifBlank { "Kindle" }
                        val koKey = syncSettings.userKey.ifBlank { "f751f8e54f4c34b398941489c6585a08" }

                        OpdsBookRow(
                            entry = entry,
                            onClick = { selectedBookForDetail = entry },
                            isDownloading = isDownloading,
                            downloadProgress = progress ?: 0f,
                            username = authUser,
                            password = authPass,
                            xAuthUser = koUser,
                            xAuthKey = koKey,
                            catalogBaseUrl = catalogBaseUrl,
                            onDownload = {
                                coroutineScope.launch {
                                    val dlResult = opdsRepo.downloadEpub(
                                        entry = entry,
                                        username = authUser,
                                        password = authPass,
                                        baseUrl = catalogBaseUrl
                                    )
                                    dlResult.fold(
                                        onSuccess = { newBook ->
                                            repository.addBook(newBook)
                                            Toast.makeText(context, "Downloaded '${newBook.title}'", Toast.LENGTH_SHORT).show()
                                            onBookDownloadedAndOpen(newBook)
                                        },
                                        onFailure = { err ->
                                            Toast.makeText(context, "Download failed: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        } else if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.text, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Loading catalog feed...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = colors.muted
                    )
                }
            }
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Error Loading Feed",
                        fontFamily = BookFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colors.text
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = colors.muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { loadFeed(currentFeedUrl, addToHistory = false) },
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            Text("Retry", fontSize = 12.sp, color = colors.text)
                        }
                        if (selectedCatalog != null) {
                            Button(
                                onClick = { catalogToEdit = selectedCatalog },
                                shape = RoundedCornerShape(2.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.text, contentColor = colors.background)
                            ) {
                                Text("Edit Server URL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            val feed = currentFeed
            val entries = feed?.entries ?: emptyList()
            val navLinks = feed?.navigationLinks ?: emptyList()

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                // Category / Sub-feed Navigation links
                if (navLinks.isNotEmpty()) {
                    item {
                        Text(
                            text = "CATEGORIES & SHELVES",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.muted,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(navLinks) { link ->
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                            color = colors.card,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { loadFeed(link.href) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = link.title.ifEmpty { "Browse Section" },
                                    fontFamily = BookFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = colors.text
                                )
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = colors.text,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = colors.border)
                    }
                }

                // Book Items
                if (entries.isEmpty() && navLinks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No books or folders found in this catalog feed.",
                                fontFamily = BookFontFamily,
                                fontSize = 14.sp,
                                color = colors.muted
                            )
                        }
                    }
                } else {
                    items(entries, key = { it.id }) { entry ->
                        if (entry.isNavigationOnly && entry.navigationHref != null) {
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                                color = colors.card,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { loadFeed(entry.navigationHref) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.title,
                                            fontFamily = BookFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = colors.text
                                        )
                                        if (entry.summary.isNotEmpty()) {
                                            Text(
                                                text = entry.summary,
                                                fontFamily = BookFontFamily,
                                                fontSize = 11.sp,
                                                color = colors.muted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.text,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            val cat = selectedCatalog
                            val authUser = cat?.username?.ifBlank { syncSettings.username } ?: syncSettings.username
                            val authPass = cat?.password?.ifBlank { syncSettings.userKey } ?: syncSettings.userKey
                            val catalogBaseUrl = cat?.url ?: "https://buks.lol"

                            val progress = downloadProgress[entry.id] ?: entry.id.toIntOrNull()?.let { boDownloadProgress[it] }
                            val isDownloading = progress != null

                            val koUser = syncSettings.username.ifBlank { "Kindle" }
                            val koKey = syncSettings.userKey.ifBlank { "f751f8e54f4c34b398941489c6585a08" }

                            OpdsBookRow(
                                entry = entry,
                                onClick = { selectedBookForDetail = entry },
                                isDownloading = isDownloading,
                                downloadProgress = progress ?: 0f,
                                username = authUser,
                                password = authPass,
                                xAuthUser = koUser,
                                xAuthKey = koKey,
                                catalogBaseUrl = catalogBaseUrl,
                                onDownload = {
                                    coroutineScope.launch {
                                        val dlResult = opdsRepo.downloadEpub(
                                            entry = entry,
                                            username = authUser,
                                            password = authPass,
                                            baseUrl = catalogBaseUrl
                                        )
                                        dlResult.fold(
                                            onSuccess = { newBook ->
                                                repository.addBook(newBook)
                                                Toast.makeText(context, "Downloaded '${newBook.title}'", Toast.LENGTH_SHORT).show()
                                                onBookDownloadedAndOpen(newBook)
                                            },
                                            onFailure = { err ->
                                                Toast.makeText(context, "Download failed: ${err.message}", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                if (feed?.nextPageHref != null) {
                    item {
                        OutlinedButton(
                            onClick = { loadFeed(feed.nextPageHref) },
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text("Next Page →", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = colors.text)
                        }
                    }
                }
            }
        }
    }

    // Catalog Picker Bottom Sheet
    if (showCatalogPicker) {
        val pickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCatalogPicker = false },
            sheetState = pickerSheetState,
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
                        text = "Select OPDS Catalog",
                        fontFamily = BookFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.text
                    )
                    IconButton(
                        onClick = { showCatalogPicker = false },
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

                HorizontalDivider(thickness = 1.dp, color = colors.border)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    items(catalogs, key = { it.id }) { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCatalog = cat
                                    showCatalogPicker = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cat.name,
                                    fontFamily = BookFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = colors.text
                                )
                                Text(
                                    text = cat.url,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = colors.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Edit Catalog button for ANY catalog
                                IconButton(
                                    onClick = {
                                        showCatalogPicker = false
                                        catalogToEdit = cat
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = colors.text,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        opdsRepo.removeCatalog(cat.id)
                                        if (selectedCatalog?.id == cat.id) {
                                            selectedCatalog = opdsRepo.catalogs.value.firstOrNull()
                                            currentFeed = null
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = colors.muted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = colors.border)
                    }
                }

                Button(
                    onClick = {
                        showCatalogPicker = false
                        showAddCatalogDialog = true
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.text, contentColor = colors.background),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("+ Add New Catalog", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(10.dp))
            }
        }
    }

    // Edit Catalog Bottom Sheet
    catalogToEdit?.let { cat ->
        var editName by remember(cat.id) { mutableStateOf(cat.name) }
        var editUrl by remember(cat.id) { mutableStateOf(cat.url) }
        var editDesc by remember(cat.id) { mutableStateOf(cat.description) }
        var editUser by remember(cat.id) { mutableStateOf(cat.username) }
        var editPass by remember(cat.id) { mutableStateOf(cat.password) }
        val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { catalogToEdit = null },
            sheetState = editSheetState,
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit OPDS Catalog",
                        fontFamily = BookFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.text
                    )
                    IconButton(
                        onClick = { catalogToEdit = null },
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

                HorizontalDivider(thickness = 1.dp, color = colors.border)

                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Catalog Name", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editUrl,
                    onValueChange = { editUrl = it },
                    label = { Text("OPDS Feed URL (e.g. https://buks.lol/api/v1/opds)", fontSize = 11.sp) },
                    singleLine = false,
                    maxLines = 3,
                    minLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editUser,
                    onValueChange = { editUser = it },
                    label = { Text("Username (Optional)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editPass,
                    onValueChange = { editPass = it },
                    label = { Text("Password (Optional)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        if (editName.isNotBlank() && editUrl.isNotBlank()) {
                            val updated = cat.copy(
                                name = editName.trim(),
                                url = editUrl.trim(),
                                description = editDesc.trim(),
                                username = editUser.trim(),
                                password = editPass.trim()
                            )
                            opdsRepo.updateCatalog(updated)
                            if (selectedCatalog?.id == cat.id) {
                                selectedCatalog = updated
                                loadFeed(updated.url, addToHistory = false)
                            }
                            catalogToEdit = null
                        }
                    },
                    enabled = editName.isNotBlank() && editUrl.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.text, contentColor = colors.background),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(10.dp))
            }
        }
    }

    // Add Custom Catalog Bottom Sheet
    if (showAddCatalogDialog) {
        var newName by remember { mutableStateOf("") }
        var newUrl by remember { mutableStateOf("https://") }
        var newDesc by remember { mutableStateOf("") }
        var newUser by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showAddCatalogDialog = false },
            sheetState = addSheetState,
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add OPDS Catalog",
                        fontFamily = BookFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.text
                    )
                    IconButton(
                        onClick = { showAddCatalogDialog = false },
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

                HorizontalDivider(thickness = 1.dp, color = colors.border)

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Catalog Name (e.g. My Calibre / BookOrbit)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    label = { Text("OPDS Feed URL (e.g. https://buks.lol/api/v1/opds)", fontSize = 11.sp) },
                    singleLine = false,
                    maxLines = 3,
                    minLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newUser,
                    onValueChange = { newUser = it },
                    label = { Text("Username (Optional)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it },
                    label = { Text("Password (Optional)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newUrl.isNotBlank()) {
                            opdsRepo.addCatalog(newName, newUrl, newDesc, newUser, newPass)
                            showAddCatalogDialog = false
                        }
                    },
                    enabled = newName.isNotBlank() && newUrl.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.text, contentColor = colors.background),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Save Catalog", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(10.dp))
            }
        }
    }

    // Book Detail Modal Dialog (tapping book from search or catalog)
    selectedBookForDetail?.let { entry ->
        val cat = selectedCatalog
        val authUser = cat?.username?.ifBlank { syncSettings.username } ?: syncSettings.username
        val authPass = cat?.password?.ifBlank { syncSettings.userKey } ?: syncSettings.userKey
        val catalogBaseUrl = cat?.url ?: "https://buks.lol"
        val koUser = syncSettings.username.ifBlank { "Kindle" }
        val koKey = syncSettings.userKey.ifBlank { "f751f8e54f4c34b398941489c6585a08" }
        val progress = downloadProgress[entry.id] ?: entry.id.toIntOrNull()?.let { boDownloadProgress[it] }
        val isDl = progress != null
        val existingBook = libraryBooks.find { b ->
            b.title.equals(entry.title, ignoreCase = true) ||
            (entry.id.isNotBlank() && b.filePath?.contains(entry.id) == true)
        }

        OpdsBookDetailDialog(
            entry = entry,
            onDismiss = { selectedBookForDetail = null },
            onDownload = {
                coroutineScope.launch {
                    val dlResult = opdsRepo.downloadEpub(
                        entry = entry,
                        username = authUser,
                        password = authPass,
                        baseUrl = catalogBaseUrl
                    )
                    dlResult.fold(
                        onSuccess = { newBook ->
                            repository.addBook(newBook)
                            Toast.makeText(context, "Downloaded '${newBook.title}'", Toast.LENGTH_SHORT).show()
                            selectedBookForDetail = null
                            onBookDownloadedAndOpen(newBook)
                        },
                        onFailure = { err ->
                            Toast.makeText(context, "Download failed: ${err.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            },
            onOpenBook = { b ->
                selectedBookForDetail = null
                onBookDownloadedAndOpen(b)
            },
            existingBook = existingBook,
            isDownloading = isDl,
            downloadProgress = progress ?: 0f,
            username = authUser,
            password = authPass,
            xAuthUser = koUser,
            xAuthKey = koKey,
            catalogBaseUrl = catalogBaseUrl,
            bookOrbitClient = repository.bookOrbitClient
        )
    }
}

@Composable
fun OpdsBookRow(
    entry: OpdsEntry,
    isDownloading: Boolean,
    downloadProgress: Float,
    onDownload: () -> Unit,
    onClick: () -> Unit = {},
    username: String = "",
    password: String = "",
    xAuthUser: String = "",
    xAuthKey: String = "",
    catalogBaseUrl: String = ""
) {
    val colors = LocalEInkColors.current

    Surface(
        shape = RoundedCornerShape(2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
        color = colors.card,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isBookOrbit = catalogBaseUrl.contains("buks.lol") || catalogBaseUrl.contains("bookorbit") || entry.id.toIntOrNull() != null
            val host = if (catalogBaseUrl.startsWith("http")) {
                try {
                    val u = URL(catalogBaseUrl)
                    "${u.protocol}://${u.host}${if (u.port != -1 && u.port != 80 && u.port != 443) ":${u.port}" else ""}"
                } catch (_: Exception) { "https://buks.lol" }
            } else "https://buks.lol"

            val rawCover = entry.coverUrl ?: entry.thumbnailUrl
            val cover = when {
                !rawCover.isNullOrBlank() && (rawCover.startsWith("http://") || rawCover.startsWith("https://")) -> rawCover
                !rawCover.isNullOrBlank() -> "$host${if (rawCover.startsWith("/")) "" else "/"}$rawCover"
                isBookOrbit && entry.id.toIntOrNull() != null -> "$host/api/v1/koreader/plugin/catalog/books/${entry.id}/thumbnail"
                else -> null
            }

            DitherCover(
                title = entry.title,
                author = entry.author,
                coverImagePath = null,
                coverUrl = cover,
                username = username,
                password = password,
                xAuthUser = xAuthUser,
                xAuthKey = xAuthKey,
                pattern = kotlin.math.abs(entry.title.hashCode() % 4),
                isDitherEnabled = true,
                modifier = Modifier.size(width = 46.dp, height = 66.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    fontFamily = BookFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.author,
                    fontFamily = BookFontFamily,
                    fontSize = 12.sp,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (entry.summary.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = entry.summary,
                        fontFamily = BookFontFamily,
                        fontSize = 11.sp,
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isDownloading) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        color = colors.text,
                        trackColor = colors.border,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick = onDownload,
                enabled = !isDownloading,
                modifier = Modifier
                    .border(1.dp, colors.border)
                    .size(36.dp)
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        color = colors.text,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download EPUB",
                        tint = colors.text,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OpdsBookDetailDialog(
    entry: OpdsEntry,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onOpenBook: ((Book) -> Unit)? = null,
    existingBook: Book? = null,
    isDownloading: Boolean,
    downloadProgress: Float,
    username: String = "",
    password: String = "",
    xAuthUser: String = "",
    xAuthKey: String = "",
    catalogBaseUrl: String = "",
    bookOrbitClient: com.fiksr.inkreader.data.bookorbit.BookOrbitClient? = null
) {
    val colors = LocalEInkColors.current
    var detailedBook by remember(entry.id) { mutableStateOf<com.fiksr.inkreader.data.bookorbit.BookOrbitBook?>(null) }
    var isLoadingDetail by remember(entry.id) { mutableStateOf(false) }

    LaunchedEffect(entry.id) {
        val numId = entry.id.toIntOrNull()
        if (numId != null && bookOrbitClient != null) {
            isLoadingDetail = true
            val res = bookOrbitClient.fetchBookDetail(numId)
            isLoadingDetail = false
            res.onSuccess { detailedBook = it }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = colors.background,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, colors.border),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Book Details",
                        fontFamily = BookFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colors.text
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.text)
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(thickness = 1.dp, color = colors.border)
                Spacer(Modifier.height(12.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    // Top Hero: Cover + Info
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val host = if (catalogBaseUrl.startsWith("http")) {
                            try {
                                val u = URL(catalogBaseUrl)
                                "${u.protocol}://${u.host}${if (u.port != -1 && u.port != 80 && u.port != 443) ":${u.port}" else ""}"
                            } catch (_: Exception) { "https://buks.lol" }
                        } else "https://buks.lol"

                        val rawCover = detailedBook?.thumbnailUrl ?: entry.coverUrl ?: entry.thumbnailUrl
                        val cover = when {
                            !rawCover.isNullOrBlank() && (rawCover.startsWith("http://") || rawCover.startsWith("https://")) -> rawCover
                            !rawCover.isNullOrBlank() -> "$host${if (rawCover.startsWith("/")) "" else "/"}$rawCover"
                            entry.id.toIntOrNull() != null -> "$host/api/v1/koreader/plugin/catalog/books/${entry.id}/thumbnail"
                            else -> null
                        }

                        DitherCover(
                            title = detailedBook?.title ?: entry.title,
                            author = detailedBook?.authors?.joinToString(", ") ?: entry.author,
                            coverImagePath = null,
                            coverUrl = cover,
                            username = username,
                            password = password,
                            xAuthUser = xAuthUser,
                            xAuthKey = xAuthKey,
                            pattern = kotlin.math.abs(entry.title.hashCode() % 4),
                            isDitherEnabled = true,
                            modifier = Modifier.size(width = 90.dp, height = 130.dp)
                        )

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = detailedBook?.title ?: entry.title,
                                fontFamily = BookFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = colors.text,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = detailedBook?.authors?.joinToString(", ") ?: entry.author,
                                fontFamily = BookFontFamily,
                                fontSize = 13.sp,
                                color = colors.muted
                            )

                            detailedBook?.seriesName?.let { series ->
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Series: $series${detailedBook?.seriesIndex?.let { " #$it" } ?: ""}",
                                    fontFamily = BookFontFamily,
                                    fontSize = 11.sp,
                                    color = colors.text,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            detailedBook?.publishedYear?.let { yr ->
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Year: $yr",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = colors.muted
                                )
                            }

                            detailedBook?.publisher?.let { pub ->
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Publisher: $pub",
                                    fontFamily = BookFontFamily,
                                    fontSize = 11.sp,
                                    color = colors.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            val formats = detailedBook?.formats?.joinToString(", ")?.uppercase()
                            if (!formats.isNullOrBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Format: $formats",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = colors.muted
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = colors.border)
                    Spacer(Modifier.height(10.dp))

                    // Synopsis / Description Header
                    Text(
                        text = "SYNOPSIS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.muted
                    )
                    Spacer(Modifier.height(6.dp))

                    val desc = detailedBook?.description ?: entry.summary
                    if (desc.isNotBlank()) {
                        Text(
                            text = desc,
                            fontFamily = BookFontFamily,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = colors.text
                        )
                    } else {
                        Text(
                            text = if (isLoadingDetail) "Loading book details..." else "No synopsis available for this book.",
                            fontFamily = BookFontFamily,
                            fontSize = 12.sp,
                            color = colors.muted
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(thickness = 1.dp, color = colors.border)
                Spacer(Modifier.height(12.dp))

                // Bottom Action Buttons
                if (isDownloading) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Downloading...", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = colors.text)
                            Text("${(downloadProgress * 100).toInt()}%", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = colors.muted)
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            color = colors.text,
                            trackColor = colors.border,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                    }
                } else if (existingBook != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDownload,
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Re-Download", fontSize = 12.sp, color = colors.text)
                        }
                        Button(
                            onClick = {
                                onOpenBook?.invoke(existingBook)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.text, contentColor = colors.background),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open in Reader", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = onDownload,
                        shape = RoundedCornerShape(2.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.text, contentColor = colors.background),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Download Book (EPUB)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
