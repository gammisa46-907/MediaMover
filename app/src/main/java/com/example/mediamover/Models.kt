package com.example.mediamover

import android.content.IntentSender
import android.net.Uri

/** NONE = belum ada izin baca media, MEDIA = lewat MediaStore, FULL = akses semua file (File API). */
enum class AccessMode { NONE, MEDIA, FULL }

enum class Screen { GALLERY, SETTINGS, DUPLICATES }

enum class ViewMode { GRID, ALBUMS }

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

data class MediaItem(
    /** Kunci unik: "i:<id>" (gambar MediaStore), "v:<id>" (video MediaStore), "f:<path>" (hasil pemindaian mendalam). */
    val key: String,
    val uri: Uri,
    val name: String,
    val mime: String,
    val size: Long,
    /** Waktu diubah dalam milidetik. */
    val modified: Long,
    val isVideo: Boolean,
    val durationMs: Long,
    /** Kunci folder (path absolut folder induk bila diketahui). */
    val folderKey: String,
    /** Nama folder terakhir, mis. "Camera". */
    val folderLabel: String,
    /** Path folder yang mudah dibaca, mis. "Internal/DCIM/Camera". */
    val folderPath: String,
    /** Path absolut file bila diketahui. */
    val path: String?
)

data class Album(
    val key: String,
    val label: String,
    val path: String,
    val count: Int,
    val totalSize: Long,
    val cover: MediaItem,
    /** Key semua file di album (sesuai filter/pencarian aktif). */
    val keys: List<String>
)

data class DupGroup(val size: Long, val items: List<MediaItem>)

data class DupState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val scanned: Boolean = false,
    val groups: List<DupGroup> = emptyList(),
    val selected: Set<String> = emptySet()
)

data class BusyState(
    val title: String,
    val done: Int,
    val total: Int,
    val current: String
)

enum class RequestKind { DELETE, WRITE }

/** Permintaan konfirmasi sistem (Android 11+) untuk menghapus/mengubah file milik aplikasi lain. */
data class PendingRequest(
    val id: Long,
    val kind: RequestKind,
    /** "move", "delete", atau "rename". */
    val purpose: String,
    val sender: IntentSender,
    val items: List<MediaItem>,
    val newNames: List<String>,
    val doneBefore: Int,
    val suffix: String,
    val launched: Boolean = false
)

data class ViewerState(val items: List<MediaItem>, val index: Int)

data class UiState(
    val access: AccessMode = AccessMode.NONE,
    val canRequestAllFiles: Boolean = false,
    val screen: Screen = Screen.GALLERY,
    val destination: Uri? = null,
    val deepScan: Boolean = false,
    val filter: MediaFilter = MediaFilter.ALL,
    val sort: SortOption = SortOption.NEWEST,
    val viewMode: ViewMode = ViewMode.GRID,
    val folderFilter: String? = null,
    val query: String = "",
    val searching: Boolean = false,
    val visible: List<MediaItem> = emptyList(),
    val albums: List<Album> = emptyList(),
    val totalCount: Int = 0,
    val imageCount: Int = 0,
    val videoCount: Int = 0,
    val selected: Set<String> = emptySet(),
    val loading: Boolean = false,
    val busy: BusyState? = null,
    val message: String? = null,
    val pending: PendingRequest? = null,
    val viewer: ViewerState? = null,
    val dup: DupState = DupState()
)
