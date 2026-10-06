package com.fiksr.inkreader.data.bookorbit

data class BookOrbitBook(
    val id: Int,
    val title: String,
    val authors: List<String>,
    val seriesName: String? = null,
    val seriesIndex: String? = null,
    val progressPercentage: Double? = null,
    val readStatus: String? = null,
    val formats: List<String> = emptyList(),
    val hasCover: Boolean = false,
    val thumbnailUrl: String? = null,
    val detailUrl: String? = null,
    val description: String? = null,
    val publisher: String? = null,
    val publishedYear: Int? = null,
    val fileId: Int? = null,
    val downloadUrl: String? = null
)

data class BookOrbitSection(
    val id: String,
    val title: String,
    val section: String,
    val href: String? = null,
    val booksHref: String? = null
)

data class BookOrbitDashboard(
    val continueReading: List<BookOrbitBook> = emptyList(),
    val discover: List<BookOrbitBook> = emptyList(),
    val browseSections: List<BookOrbitSection> = emptyList(),
    val highlightOfTheDay: BookOrbitHighlight? = null,
    val currentStreak: Int = 0
)

data class BookOrbitHighlight(
    val text: String,
    val bookTitle: String,
    val chapterTitle: String?,
    val bookId: Int
)
