package com.example.inkreader.model

data class Bookmark(
    val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val pageIndex: Int,
    val chapterTitle: String,
    val excerpt: String,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class Highlight(
    val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val pageIndex: Int = 0,
    val selectedText: String,
    val startCharOffset: Int = 0,
    val endCharOffset: Int = 0,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
