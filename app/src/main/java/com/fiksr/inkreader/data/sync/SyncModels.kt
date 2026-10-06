package com.fiksr.inkreader.data.sync

data class SyncSettings(
    val serverUrl: String = "https://buks.lol/api/v1/koreader",
    val username: String = "",
    val userKey: String = "",
    val deviceName: String = "InkReader Android",
    val autoSyncOnOpen: Boolean = true,
    val autoSyncOnClose: Boolean = true,
    val lastSyncTimestamp: Long = 0L
)

data class SyncProgress(
    val documentHash: String,
    val progressPercent: Int,
    val chapterIndex: Int,
    val pageIndex: Int,
    val device: String,
    val deviceId: String,
    val timestamp: Long
)

sealed class SyncResult {
    data class Success(val message: String, val remoteProgress: SyncProgress? = null) : SyncResult()
    data class Conflict(val remoteProgress: SyncProgress) : SyncResult()
    data class Error(val error: String) : SyncResult()
}
