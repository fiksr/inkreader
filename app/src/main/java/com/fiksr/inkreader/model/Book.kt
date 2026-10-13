package com.fiksr.inkreader.model

data class TocItem(
    val id: String = "",
    val title: String,
    val href: String = "",
    val targetChapterIndex: Int = 0,
    val targetCharOffset: Int = 0,
    val level: Int = 0
)

data class Chapter(
    val id: String,
    val title: String,
    val content: String,
    val wordCount: Int = content.split("\\s+".toRegex()).size
)

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverPattern: Int = 0,
    val coverImagePath: String? = null,
    val progressPercent: Int = 0,
    val currentChapterIndex: Int = 0,
    val currentPageIndex: Int = 0,
    val chapters: List<Chapter>,
    val isCustomImported: Boolean = false,
    val filePath: String? = null,
    val toc: List<TocItem> = emptyList()
) {
    val totalWords: Int get() = chapters.sumOf { it.wordCount }
    val currentChapter: Chapter get() = chapters.getOrElse(currentChapterIndex) { chapters.firstOrNull() ?: Chapter("0", "Empty", "") }
}
