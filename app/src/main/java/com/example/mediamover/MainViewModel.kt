package com.example.mediamover

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application
    private val resolver: ContentResolver get() = app.contentResolver
    private val store = SettingsStore(app)

    private var allItems: List<MediaItem> = emptyList()
    private var scanJob: Job? = null
    private var dupJob: Job? = null
    private var requestCounter = 0L

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val dest = store.loadDestination()?.takeIf { hasUriPermission(it) }
        if (dest == null) store.saveDestination(null)

        _state.value = UiState(
            access = StorageAccess.currentMode(app),
            canRequestAllFiles = StorageAccess.canRequestAllFiles(),
            destination = dest,
            deepScan = store.deepScan,
            filter = store.filter,
            sort = store.sort,
            viewMode = store.viewMode
        )
        if (_state.value.access != AccessMode.NONE) refresh()
    }

    // ---------------------------------------------------------------- akses

    /** Dipanggil saat aplikasi kembali ke depan (mis. setelah pengguna mengubah izin di Pengaturan). */
    fun onResume() {
        val mode = StorageAccess.currentMode(app)
        val previous = _state.value.access
        if (mode != previous) {
            _state.update { it.copy(access = mode) }
            if (mode == AccessMode.NONE) {
                allItems = emptyList()
                applyView()
            } else {
                refresh()
            }
        }
    }

    fun onPermissionResult() {
        onResume()
    }

    // ---------------------------------------------------------------- pengaturan

    fun onDestinationPicked(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            postMessage("Gagal mendapatkan izin untuk folder ini")
            return
        }
        val old = _state.value.destination
        store.saveDestination(uri)
        if (old != null && old != uri) releaseUriPermission(old)
        _state.update { it.copy(destination = uri) }
    }

    fun setDeepScan(value: Boolean) {
        store.deepScan = value
        _state.update { it.copy(deepScan = value) }
        refresh()
    }

    fun setScreen(screen: Screen) {
        _state.update { it.copy(screen = screen) }
        if (screen == Screen.DUPLICATES) {
            val d = _state.value.dup
            if (!d.scanned && !d.running) startDuplicateScan()
        }
    }

    // ---------------------------------------------------------------- tampilan

    fun setFilter(filter: MediaFilter) {
        store.filter = filter
        _state.update { it.copy(filter = filter) }
        applyView()
    }

    fun setSort(sort: SortOption) {
        store.sort = sort
        _state.update { it.copy(sort = sort) }
        applyView()
    }

    fun setViewMode(mode: ViewMode) {
        store.viewMode = mode
        _state.update { it.copy(viewMode = mode, folderFilter = null) }
        applyView()
    }

    fun setSearching(searching: Boolean) {
        _state.update {
            if (searching) it.copy(searching = true) else it.copy(searching = false, query = "")
        }
        applyView()
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        applyView()
    }

    fun openAlbum(key: String) {
        _state.update { it.copy(folderFilter = key, viewMode = ViewMode.GRID) }
        applyView()
    }

    fun closeAlbum() {
        _state.update { it.copy(folderFilter = null, viewMode = ViewMode.ALBUMS) }
        applyView()
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    // ---------------------------------------------------------------- viewer

    fun openViewer(items: List<MediaItem>, index: Int) {
        if (items.isEmpty()) return
        _state.update { it.copy(viewer = ViewerState(items, index.coerceIn(0, items.size - 1))) }
    }

    fun closeViewer() {
        _state.update { it.copy(viewer = null) }
    }

    // ---------------------------------------------------------------- seleksi

    fun toggleSelect(key: String) {
        _state.update {
            val next = if (it.selected.contains(key)) it.selected - key else it.selected + key
            it.copy(selected = next)
        }
    }

    fun selectAllVisible() {
        _state.update { it.copy(selected = it.selected + it.visible.map { item -> item.key }) }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = emptySet()) }
    }

    /** Menambah/menghapus seluruh file satu album dari seleksi. */
    fun toggleAlbumSelection(folderKey: String) {
        val s = _state.value
        val keys = s.albums.firstOrNull { it.key == folderKey }?.keys ?: return
        if (keys.isEmpty()) return
        val allSelected = keys.all { s.selected.contains(it) }
        _state.update {
            if (allSelected) it.copy(selected = it.selected - keys.toSet())
            else it.copy(selected = it.selected + keys)
        }
    }

    /** File terpilih (termasuk yang tersembunyi oleh filter), urut sesuai pilihan urutan. */
    fun selectedItemsSorted(): List<MediaItem> {
        val s = _state.value
        return sortItems(allItems.filter { s.selected.contains(it.key) }, s.sort)
    }

    // ---------------------------------------------------------------- pindai

    fun refresh() {
        val s = _state.value
        if (s.access == AccessMode.NONE) return
        scanJob?.cancel()
        val mode = s.access
        val deep = s.deepScan && mode == AccessMode.FULL

        _state.update { it.copy(loading = true) }
        scanJob = viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                try {
                    val base = MediaRepository.scanMediaStore(app, mode == AccessMode.FULL)
                    if (deep) {
                        val known = HashSet<String>()
                        for (i in base) {
                            val p = i.path
                            if (p != null) known.add(p)
                        }
                        val extra = MediaRepository.deepScan(known) { isActive }
                        base + extra
                    } else {
                        base
                    }
                } catch (e: Exception) {
                    emptyList<MediaItem>()
                }
            }
            allItems = items
            val valid = items.map { it.key }.toHashSet()
            _state.update { st ->
                st.copy(loading = false, selected = st.selected.filter { k -> valid.contains(k) }.toSet())
            }
            applyView()
        }
    }

    private fun filteredItems(s: UiState): List<MediaItem> {
        val typed = when (s.filter) {
            MediaFilter.ALL -> allItems
            MediaFilter.IMAGES -> allItems.filter { !it.isVideo }
            MediaFilter.VIDEOS -> allItems.filter { it.isVideo }
        }
        val q = s.query.trim().lowercase()
        if (q.isEmpty()) return typed
        return typed.filter {
            it.name.lowercase().contains(q) || it.folderLabel.lowercase().contains(q)
        }
    }

    private fun sortItems(items: List<MediaItem>, sort: SortOption): List<MediaItem> {
        return when (sort) {
            SortOption.NEWEST -> items.sortedByDescending { it.modified }
            SortOption.OLDEST -> items.sortedBy { it.modified }
            SortOption.LARGEST -> items.sortedByDescending { it.size }
            SortOption.SMALLEST -> items.sortedBy { it.size }
            SortOption.NAME -> items.sortedBy { it.name.lowercase() }
        }
    }

    private fun buildAlbums(items: List<MediaItem>): List<Album> {
        val map = LinkedHashMap<String, MutableList<MediaItem>>()
        for (item in items) {
            map.getOrPut(item.folderKey) { ArrayList() }.add(item)
        }
        val albums = ArrayList<Album>()
        for (list in map.values) {
            var cover = list[0]
            var total = 0L
            for (m in list) {
                if (m.modified > cover.modified) cover = m
                total += m.size
            }
            albums.add(
                Album(
                    cover.folderKey, cover.folderLabel, cover.folderPath, list.size, total, cover,
                    list.map { it.key }
                )
            )
        }
        return albums.sortedByDescending { it.cover.modified }
    }

    private fun applyView() {
        val s = _state.value
        val images = allItems.count { !it.isVideo }
        val videos = allItems.size - images

        val filtered = filteredItems(s)
        val albums = buildAlbums(filtered)

        var folder = s.folderFilter
        var viewMode = s.viewMode
        if (folder != null && albums.none { it.key == folder }) {
            folder = null
            viewMode = ViewMode.ALBUMS
        }
        val inFolder = if (folder == null) filtered else filtered.filter { it.folderKey == folder }
        val sorted = sortItems(inFolder, s.sort)

        _state.update {
            it.copy(
                visible = sorted,
                albums = albums,
                folderFilter = folder,
                viewMode = viewMode,
                totalCount = allItems.size,
                imageCount = images,
                videoCount = videos
            )
        }
    }

    // ---------------------------------------------------------------- pindahkan

    fun moveSelected() {
        moveItems(selectedItemsSorted())
    }

    fun moveItems(items: List<MediaItem>) {
        val s = _state.value
        val dest = s.destination
        if (dest == null) {
            postMessage("Pilih folder tujuan dulu")
            return
        }
        if (s.busy != null || items.isEmpty()) return
        val mode = s.access
        val title = "Memindahkan file..."
        setBusy(title, 0, items.size, "")

        viewModelScope.launch {
            val outcome = try {
                FileOps.move(app, items, dest, mode) { d, t, n -> setBusy(title, d, t, n) }
            } catch (e: Exception) {
                null
            }
            if (outcome == null) {
                _state.update { it.copy(busy = null) }
                postMessage("Gagal memindahkan file. Periksa folder tujuan.")
                return@launch
            }

            removeItems(outcome.moved)

            val extra = ArrayList<String>()
            if (outcome.skipped > 0) extra.add("${outcome.skipped} dilewati (sudah di folder tujuan)")
            if (outcome.failed.isNotEmpty()) extra.add("${outcome.failed.size} gagal")
            if (outcome.copiedKept > 0) extra.add("${outcome.copiedKept} disalin tapi file asal tidak bisa dihapus")
            val suffix = if (extra.isEmpty()) "" else ", " + extra.joinToString(", ")

            if (outcome.pendingDelete.isNotEmpty()) {
                requestSystemDelete(outcome.pendingDelete, "move", outcome.moved.size, suffix)
            } else {
                _state.update { it.copy(busy = null) }
                postMessage("${outcome.moved.size} file dipindahkan$suffix")
                refresh()
            }
        }
    }

    // ---------------------------------------------------------------- hapus

    fun deleteItems(items: List<MediaItem>) {
        val s = _state.value
        if (s.busy != null || items.isEmpty()) return
        val mode = s.access
        val title = "Menghapus file..."
        setBusy(title, 0, items.size, "")

        viewModelScope.launch {
            val outcome = try {
                FileOps.delete(app, items, mode) { d, t, n -> setBusy(title, d, t, n) }
            } catch (e: Exception) {
                null
            }
            if (outcome == null) {
                _state.update { it.copy(busy = null) }
                postMessage("Gagal menghapus file.")
                return@launch
            }

            removeItems(outcome.deleted)
            val suffix = if (outcome.failed.isEmpty()) "" else ", ${outcome.failed.size} gagal"

            if (outcome.pending.isNotEmpty()) {
                requestSystemDelete(outcome.pending, "delete", outcome.deleted.size, suffix)
            } else {
                _state.update { it.copy(busy = null) }
                postMessage("${outcome.deleted.size} file dihapus$suffix")
                refresh()
            }
        }
    }

    private fun requestSystemDelete(items: List<MediaItem>, purpose: String, doneBefore: Int, suffix: String) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) throw IllegalStateException("API < 30")
            val pi = MediaStore.createDeleteRequest(resolver, items.map { it.uri })
            requestCounter++
            val request = PendingRequest(
                id = requestCounter,
                kind = RequestKind.DELETE,
                purpose = purpose,
                sender = pi.intentSender,
                items = items,
                newNames = emptyList(),
                doneBefore = doneBefore,
                suffix = suffix
            )
            _state.update { it.copy(busy = null, pending = request) }
        } catch (e: Exception) {
            _state.update { it.copy(busy = null) }
            postMessage("Gagal menyiapkan konfirmasi penghapusan file asal")
            refresh()
        }
    }

    private fun requestSystemWrite(items: List<MediaItem>, names: List<String>, doneBefore: Int, suffix: String) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) throw IllegalStateException("API < 30")
            val pi = MediaStore.createWriteRequest(resolver, items.map { it.uri })
            requestCounter++
            val request = PendingRequest(
                id = requestCounter,
                kind = RequestKind.WRITE,
                purpose = "rename",
                sender = pi.intentSender,
                items = items,
                newNames = names,
                doneBefore = doneBefore,
                suffix = suffix
            )
            _state.update { it.copy(busy = null, pending = request) }
        } catch (e: Exception) {
            _state.update { it.copy(busy = null) }
            postMessage("Gagal menyiapkan izin untuk mengganti nama")
        }
    }

    fun markRequestLaunched() {
        _state.update { st ->
            val p = st.pending
            if (p == null) st else st.copy(pending = p.copy(launched = true))
        }
    }

    /** Hasil dialog konfirmasi sistem (hapus/ubah file milik aplikasi lain). */
    fun onRequestResult(ok: Boolean) {
        val p = _state.value.pending ?: return
        _state.update { it.copy(pending = null) }

        if (p.kind == RequestKind.DELETE) {
            if (ok) {
                removeItems(p.items.map { it.key }.toSet())
                val total = p.doneBefore + p.items.size
                val verb = if (p.purpose == "move") "dipindahkan" else "dihapus"
                postMessage("$total file $verb${p.suffix}")
            } else if (p.purpose == "move") {
                postMessage("${p.items.size} file sudah disalin ke tujuan, tetapi file asal tidak dihapus karena izin ditolak${p.suffix}")
            } else {
                postMessage("Penghapusan dibatalkan")
            }
            refresh()
        } else {
            if (!ok) {
                postMessage("Penggantian nama dibatalkan")
                return
            }
            setBusy("Mengganti nama...", 0, p.items.size, "")
            viewModelScope.launch {
                val result = withContext(Dispatchers.IO) {
                    try {
                        FileOps.applyMediaStoreRenames(app, p.items, p.newNames)
                    } catch (e: Exception) {
                        Pair(0, p.items.size)
                    }
                }
                _state.update { it.copy(busy = null, selected = emptySet()) }
                val total = p.doneBefore + result.first
                val bad = result.second
                postMessage("$total file diganti namanya${if (bad > 0) ", $bad gagal" else ""}${p.suffix}")
                refresh()
            }
        }
    }

    // ---------------------------------------------------------------- ganti nama massal

    fun renameSelected(pattern: String, start: Int, digits: Int) {
        val s = _state.value
        if (s.busy != null) return
        val items = selectedItemsSorted()
        if (items.isEmpty()) return

        val names = BatchRename.generate(items, pattern, start, digits)
        val changedItems = ArrayList<MediaItem>()
        val changedNames = ArrayList<String>()
        for ((i, item) in items.withIndex()) {
            if (item.name != names[i]) {
                changedItems.add(item)
                changedNames.add(names[i])
            }
        }
        if (changedItems.isEmpty()) {
            postMessage("Tidak ada nama yang berubah")
            return
        }

        val mode = s.access
        val title = "Mengganti nama..."
        setBusy(title, 0, changedItems.size, "")

        viewModelScope.launch {
            val outcome = try {
                FileOps.rename(app, changedItems, changedNames, mode) { d, t, n -> setBusy(title, d, t, n) }
            } catch (e: Exception) {
                null
            }
            if (outcome == null) {
                _state.update { it.copy(busy = null) }
                postMessage("Gagal mengganti nama file.")
                return@launch
            }
            val suffix = if (outcome.failed > 0) ", ${outcome.failed} gagal" else ""
            if (outcome.pending.isNotEmpty()) {
                requestSystemWrite(outcome.pending, outcome.pendingNames, outcome.renamed, suffix)
            } else {
                _state.update { it.copy(busy = null, selected = emptySet()) }
                postMessage("${outcome.renamed} file diganti namanya$suffix")
                refresh()
            }
        }
    }

    // ---------------------------------------------------------------- duplikat

    fun startDuplicateScan() {
        if (_state.value.dup.running) return
        dupJob?.cancel()
        val snapshot = allItems
        _state.update { it.copy(dup = DupState(running = true)) }

        dupJob = viewModelScope.launch {
            val groups = withContext(Dispatchers.IO) {
                try {
                    DuplicateFinder.find(
                        app,
                        snapshot,
                        { d, t -> _state.update { it.copy(dup = it.dup.copy(done = d, total = t)) } },
                        { isActive }
                    )
                } catch (e: Exception) {
                    emptyList<DupGroup>()
                }
            }
            _state.update {
                it.copy(dup = DupState(running = false, scanned = true, groups = groups, selected = keepOldest(groups)))
            }
        }
    }

    fun cancelDuplicateScan() {
        dupJob?.cancel()
        _state.update { it.copy(dup = DupState(scanned = false)) }
    }

    /** Memilih semua duplikat kecuali satu file tertua di tiap grup. */
    private fun keepOldest(groups: List<DupGroup>): Set<String> {
        val keys = HashSet<String>()
        for (g in groups) {
            for ((i, item) in g.items.withIndex()) {
                if (i > 0) keys.add(item.key)
            }
        }
        return keys
    }

    fun autoSelectDuplicates() {
        _state.update { it.copy(dup = it.dup.copy(selected = keepOldest(it.dup.groups))) }
    }

    fun clearDuplicateSelection() {
        _state.update { it.copy(dup = it.dup.copy(selected = emptySet())) }
    }

    fun toggleDuplicate(key: String) {
        _state.update {
            val sel = it.dup.selected
            val next = if (sel.contains(key)) sel - key else sel + key
            it.copy(dup = it.dup.copy(selected = next))
        }
    }

    fun selectedDuplicateItems(): List<MediaItem> {
        val d = _state.value.dup
        val out = ArrayList<MediaItem>()
        for (g in d.groups) {
            for (item in g.items) {
                if (d.selected.contains(item.key)) out.add(item)
            }
        }
        return out
    }

    // ---------------------------------------------------------------- util

    private fun removeItems(keys: Set<String>) {
        if (keys.isEmpty()) return
        allItems = allItems.filter { !keys.contains(it.key) }
        _state.update { st ->
            val groups = st.dup.groups
                .map { g -> DupGroup(g.size, g.items.filter { !keys.contains(it.key) }) }
                .filter { it.items.size >= 2 }
            val validDup = HashSet<String>()
            for (g in groups) for (item in g.items) validDup.add(item.key)
            st.copy(
                selected = st.selected.filter { !keys.contains(it) }.toSet(),
                dup = st.dup.copy(
                    groups = groups,
                    selected = st.dup.selected.filter { validDup.contains(it) }.toSet()
                )
            )
        }
        applyView()
    }

    private fun setBusy(title: String, done: Int, total: Int, current: String) {
        _state.update { it.copy(busy = BusyState(title, done, total, current)) }
    }

    private fun postMessage(text: String) {
        _state.update { it.copy(message = text) }
    }

    private fun hasUriPermission(uri: Uri): Boolean {
        return resolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
    }

    private fun releaseUriPermission(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
        }
    }
}
