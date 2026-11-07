package com.example.mediamover

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application
    private val resolver: ContentResolver get() = app.contentResolver
    private val store = SettingsStore(app)

    private var allItems: List<MediaItem> = emptyList()
    private var scanJob: Job? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val savedSources = store.loadSources()
        val sources = savedSources.filter { hasPermission(it) }
        if (sources.size != savedSources.size) store.saveSources(sources)

        val dest = store.loadDestination()?.takeIf { hasPermission(it) }
        if (dest == null) store.saveDestination(null)

        _state.value = UiState(
            sources = sources,
            destination = dest,
            includeSubfolders = store.includeSubfolders,
            filter = store.filter,
            sort = store.sort
        )
        refresh()
    }

    // ---------------------------------------------------------------- folder

    fun onSourcePicked(uri: Uri) {
        if (!takePermission(uri)) {
            postMessage("Gagal mendapatkan izin untuk folder ini")
            return
        }
        val current = _state.value.sources
        if (current.contains(uri)) {
            postMessage("Folder itu sudah ada di daftar")
            return
        }
        val updated = current + uri
        store.saveSources(updated)
        _state.update { it.copy(sources = updated) }
        refresh()
    }

    fun removeSource(uri: Uri) {
        val updated = _state.value.sources.filter { it != uri }
        store.saveSources(updated)
        if (uri != _state.value.destination) releasePermission(uri)
        _state.update { it.copy(sources = updated) }
        refresh()
    }

    fun onDestinationPicked(uri: Uri) {
        if (!takePermission(uri)) {
            postMessage("Gagal mendapatkan izin untuk folder ini")
            return
        }
        val old = _state.value.destination
        store.saveDestination(uri)
        if (old != null && old != uri && !_state.value.sources.contains(old)) {
            releasePermission(old)
        }
        _state.update { it.copy(destination = uri) }
    }

    fun setIncludeSubfolders(value: Boolean) {
        store.includeSubfolders = value
        _state.update { it.copy(includeSubfolders = value) }
        refresh()
    }

    // ---------------------------------------------------------------- tampilan

    fun setFilter(filter: MediaFilter) {
        store.filter = filter
        // Seleksi dikosongkan agar file tersembunyi tidak ikut terpindah
        _state.update { it.copy(filter = filter, selected = emptySet()) }
        applyView()
    }

    fun setSort(sort: SortOption) {
        store.sort = sort
        _state.update { it.copy(sort = sort) }
        applyView()
    }

    fun setShowSettings(show: Boolean) {
        _state.update { it.copy(showSettings = show) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    // ---------------------------------------------------------------- seleksi

    fun toggleSelect(key: String) {
        _state.update {
            val next = if (it.selected.contains(key)) it.selected - key else it.selected + key
            it.copy(selected = next)
        }
    }

    fun selectAll() {
        _state.update { it.copy(selected = it.visible.map { item -> item.key }.toSet()) }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = emptySet()) }
    }

    // ---------------------------------------------------------------- pindai

    fun refresh() {
        scanJob?.cancel()
        val sources = _state.value.sources
        val recursive = _state.value.includeSubfolders

        if (sources.isEmpty()) {
            allItems = emptyList()
            _state.update { it.copy(loading = false, selected = emptySet()) }
            applyView()
            return
        }

        _state.update { it.copy(loading = true) }
        scanJob = viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                sources
                    .flatMap { MediaScanner.scan(resolver, it, recursive) }
                    .distinctBy { it.key }
            }
            allItems = items
            val valid = items.map { it.key }.toHashSet()
            _state.update { s ->
                s.copy(loading = false, selected = s.selected.filter { k -> valid.contains(k) }.toSet())
            }
            applyView()
        }
    }

    private fun applyView() {
        val s = _state.value
        val images = allItems.count { !it.isVideo }
        val videos = allItems.size - images

        val filtered = when (s.filter) {
            MediaFilter.ALL -> allItems
            MediaFilter.IMAGES -> allItems.filter { !it.isVideo }
            MediaFilter.VIDEOS -> allItems.filter { it.isVideo }
        }
        val sorted = when (s.sort) {
            SortOption.NEWEST -> filtered.sortedByDescending { it.modified }
            SortOption.OLDEST -> filtered.sortedBy { it.modified }
            SortOption.LARGEST -> filtered.sortedByDescending { it.size }
            SortOption.SMALLEST -> filtered.sortedBy { it.size }
            SortOption.NAME -> filtered.sortedBy { it.name.lowercase() }
        }
        _state.update {
            it.copy(
                visible = sorted,
                totalCount = allItems.size,
                imageCount = images,
                videoCount = videos
            )
        }
    }

    // ---------------------------------------------------------------- pindahkan

    fun moveSelected() {
        val s = _state.value
        val dest = s.destination
        if (dest == null) {
            postMessage("Pilih folder tujuan dulu")
            return
        }
        if (s.moving != null) return

        val toMove = s.visible.filter { s.selected.contains(it.key) }
        if (toMove.isEmpty()) return

        _state.update { it.copy(moving = MoveProgress(0, toMove.size, "")) }

        viewModelScope.launch {
            val result = try {
                FileMover.move(app, toMove, dest) { done, total, name ->
                    _state.update { it.copy(moving = MoveProgress(done, total, name)) }
                }
            } catch (e: Exception) {
                null
            }

            if (result == null) {
                _state.update { it.copy(moving = null) }
                postMessage("Gagal memindahkan file. Cek izin folder tujuan.")
                return@launch
            }

            allItems = allItems.filter { !result.movedKeys.contains(it.key) }
            _state.update { it.copy(moving = null, selected = emptySet()) }
            applyView()

            val parts = ArrayList<String>()
            parts.add("${result.movedKeys.size} file dipindahkan")
            if (result.skipped > 0) parts.add("${result.skipped} dilewati (sudah di folder tujuan)")
            if (result.failedNames.isNotEmpty()) parts.add("${result.failedNames.size} gagal")
            postMessage(parts.joinToString(", "))

            // Sinkronkan dengan isi folder sebenarnya
            refresh()
        }
    }

    // ---------------------------------------------------------------- util

    private fun postMessage(text: String) {
        _state.update { it.copy(message = text) }
    }

    private fun hasPermission(uri: Uri): Boolean {
        return resolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
    }

    private fun takePermission(uri: Uri): Boolean {
        return try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun releasePermission(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
        }
    }
}
