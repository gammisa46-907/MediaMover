package com.example.mediamover

import android.net.Uri

data class MediaItem(
    val key: String,
    val uri: Uri,
    val docId: String,
    val parentDocId: String,
    val parentUri: Uri,
    val name: String,
    val mime: String,
    val size: Long,
    val modified: Long,
    val isVideo: Boolean
)

enum class MediaFilter(val label: String) {
    ALL("Semua"),
    IMAGES("Gambar"),
    VIDEOS("Video")
}

enum class SortOption(val label: String) {
    NEWEST("Terbaru"),
    OLDEST("Terlama"),
    LARGEST("Terbesar"),
    SMALLEST("Terkecil"),
    NAME("Nama A-Z")
}

data class MoveProgress(val done: Int, val total: Int, val currentName: String)

data class MoveResult(
    val movedKeys: Set<String>,
    val failedNames: List<String>,
    val skipped: Int
)

data class UiState(
    val sources: List<Uri> = emptyList(),
    val destination: Uri? = null,
    val includeSubfolders: Boolean = true,
    val filter: MediaFilter = MediaFilter.ALL,
    val sort: SortOption = SortOption.NEWEST,
    val visible: List<MediaItem> = emptyList(),
    val totalCount: Int = 0,
    val imageCount: Int = 0,
    val videoCount: Int = 0,
    val selected: Set<String> = emptySet(),
    val loading: Boolean = false,
    val moving: MoveProgress? = null,
    val message: String? = null,
    val showSettings: Boolean = false
)
