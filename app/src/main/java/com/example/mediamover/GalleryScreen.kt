@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.mediamover

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GalleryScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onRename: () -> Unit,
    onSetSearching: (Boolean) -> Unit,
    onQuery: (String) -> Unit,
    onFilter: (MediaFilter) -> Unit,
    onSort: (SortOption) -> Unit,
    onViewMode: (ViewMode) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onCloseAlbum: () -> Unit,
    onToggleAlbum: (String) -> Unit,
    onOpenItem: (Int) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onMove: () -> Unit,
    onChooseDestination: () -> Unit
) {
    val selecting = state.selected.isNotEmpty()

    // Urutan: yang dideklarasikan terakhir diproses lebih dulu
    BackHandler(enabled = state.searching) { onSetSearching(false) }
    BackHandler(enabled = state.folderFilter != null) { onCloseAlbum() }
    BackHandler(enabled = selecting) { onClearSelection() }

    val visibleKeys = remember(state.visible) {
        state.visible.mapTo(HashSet<String>()) { it.key }
    }
    val hiddenSelected = if (selecting) state.selected.count { !visibleKeys.contains(it) } else 0

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selecting) {
                SelectionTopBar(
                    count = state.selected.size,
                    allVisibleSelected = state.visible.isNotEmpty() && hiddenSelected == 0 &&
                        state.selected.size == state.visible.size,
                    onClear = onClearSelection,
                    onSelectAll = onSelectAll,
                    onRename = onRename
                )
            } else if (state.searching) {
                SearchTopBar(
                    query = state.query,
                    onQuery = onQuery,
                    onClose = { onSetSearching(false) }
                )
            } else {
                val album = state.albums.firstOrNull { it.key == state.folderFilter }
                MainTopBar(
                    title = album?.label ?: "Media Mover",
                    onSearch = { onSetSearching(true) },
                    onRefresh = onRefresh,
                    onOpenDuplicates = onOpenDuplicates,
                    onOpenSettings = onOpenSettings
                )
            }
        },
        bottomBar = {
            if (selecting) {
                MoveBar(
                    count = state.selected.size,
                    hidden = hiddenSelected,
                    destination = state.destination,
                    onMove = onMove,
                    onChooseDestination = onChooseDestination
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            FilterRow(
                state = state,
                onFilter = onFilter,
                onSort = onSort,
                onViewMode = onViewMode
            )

            val currentAlbum = state.albums.firstOrNull { it.key == state.folderFilter }
            if (currentAlbum != null) {
                AlbumHeader(album = currentAlbum, onClose = onCloseAlbum)
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (state.visible.isEmpty() && state.albums.isEmpty() && !state.loading) {
                    Text(
                        text = if (state.totalCount == 0) {
                            "Tidak ada gambar atau video di penyimpanan."
                        } else {
                            "Tidak ada file yang cocok."
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        textAlign = TextAlign.Center
                    )
                } else if (state.viewMode == ViewMode.ALBUMS) {
                    AlbumGrid(
                        state = state,
                        onOpenAlbum = onOpenAlbum,
                        onToggleAlbum = onToggleAlbum
                    )
                } else {
                    MediaGrid(
                        state = state,
                        selecting = selecting,
                        onOpenItem = onOpenItem,
                        onToggleSelect = onToggleSelect
                    )
                }

                if (state.loading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Top bars

@Composable
private fun MainTopBar(
    title: String,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = "Cari")
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Muat ulang")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu lainnya")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Pencari duplikat") },
                        onClick = {
                            menuOpen = false
                            onOpenDuplicates()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Pengaturan") },
                        onClick = {
                            menuOpen = false
                            onOpenSettings()
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun SearchTopBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
        }
    }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Tutup pencarian")
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text("Cari nama file atau folder") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Hapus teks")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }
    )
}

@Composable
private fun SelectionTopBar(
    count: Int,
    allVisibleSelected: Boolean,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onRename: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("$count dipilih") },
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "Batal pilih")
            }
        },
        actions = {
            TextButton(onClick = { if (allVisibleSelected) onClear() else onSelectAll() }) {
                Text(if (allVisibleSelected) "Batalkan semua" else "Pilih semua")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu lainnya")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Ganti nama massal") },
                        onClick = {
                            menuOpen = false
                            onRename()
                        }
                    )
                }
            }
        }
    )
}

// ------------------------------------------------------------------ Filter

@Composable
private fun FilterRow(
    state: UiState,
    onFilter: (MediaFilter) -> Unit,
    onSort: (SortOption) -> Unit,
    onViewMode: (ViewMode) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val albumMode = state.viewMode == ViewMode.ALBUMS

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = state.filter == MediaFilter.ALL,
            onClick = { onFilter(MediaFilter.ALL) },
            label = { Text("Semua (${state.totalCount})") }
        )
        FilterChip(
            selected = state.filter == MediaFilter.IMAGES,
            onClick = { onFilter(MediaFilter.IMAGES) },
            label = { Text("Gambar (${state.imageCount})") }
        )
        FilterChip(
            selected = state.filter == MediaFilter.VIDEOS,
            onClick = { onFilter(MediaFilter.VIDEOS) },
            label = { Text("Video (${state.videoCount})") }
        )
        FilterChip(
            selected = albumMode,
            onClick = { onViewMode(if (albumMode) ViewMode.GRID else ViewMode.ALBUMS) },
            label = { Text("Album") }
        )
        if (!albumMode) {
            Box {
                AssistChip(
                    onClick = { menuOpen = true },
                    label = { Text("Urut: ${state.sort.label}") }
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    SortOption.values().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                menuOpen = false
                                onSort(option)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(album: Album, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Kembali ke daftar album")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(album.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${album.count} file - ${album.path}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ------------------------------------------------------------------ Grid file

@Composable
private fun MediaGrid(
    state: UiState,
    selecting: Boolean,
    onOpenItem: (Int) -> Unit,
    onToggleSelect: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items = state.visible, key = { it.key }) { item ->
            MediaCell(
                item = item,
                selected = state.selected.contains(item.key),
                selecting = selecting,
                onClick = {
                    if (selecting) {
                        onToggleSelect(item.key)
                    } else {
                        onOpenItem(state.visible.indexOfFirst { it.key == item.key })
                    }
                },
                onLongClick = { onToggleSelect(item.key) }
            )
        }
    }
}

@Composable
private fun MediaCell(
    item: MediaItem,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val sizeText = remember(item.size) { Formatter.formatShortFileSize(context, item.size) }
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        MediaThumb(item = item, modifier = Modifier.fillMaxSize())

        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(primary.copy(alpha = 0.35f))
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.isVideo) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                if (item.durationMs > 0L) {
                    Text(text = formatDuration(item.durationMs) + " - ", color = Color.White, fontSize = 11.sp)
                }
            }
            Text(text = sizeText, color = Color.White, fontSize = 11.sp)
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Dipilih",
                    tint = onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else if (selecting) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(Color.Black.copy(alpha = 0.25f), CircleShape)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

// ------------------------------------------------------------------ Grid album

@Composable
private fun AlbumGrid(
    state: UiState,
    onOpenAlbum: (String) -> Unit,
    onToggleAlbum: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items = state.albums, key = { it.key }) { album ->
            val selectedCount = album.keys.count { state.selected.contains(it) }
            AlbumCard(
                album = album,
                selectedCount = selectedCount,
                onClick = { onOpenAlbum(album.key) },
                onLongClick = { onToggleAlbum(album.key) }
            )
        }
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    selectedCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val sizeText = remember(album.totalSize) { Formatter.formatShortFileSize(context, album.totalSize) }
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val allSelected = selectedCount > 0 && selectedCount == album.count

    Box(
        modifier = Modifier
            .aspectRatio(1.1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        MediaThumb(item = album.cover, modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
        )

        if (allSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(primary.copy(alpha = 0.35f))
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                text = album.label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${album.count} file - $sizeText",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                maxLines = 1
            )
        }

        if (selectedCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(26.dp)
                    .background(primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (allSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Dipilih",
                        tint = onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(text = "$selectedCount", color = onPrimary, fontSize = 11.sp)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Bar pindah

@Composable
private fun MoveBar(
    count: Int,
    hidden: Int,
    destination: android.net.Uri?,
    onMove: () -> Unit,
    onChooseDestination: () -> Unit
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (hidden > 0) {
                Text(
                    text = "$hidden file terpilih tidak terlihat karena filter atau pencarian",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (destination != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Tujuan: ${FolderUtil.displayName(destination)}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onChooseDestination) { Text("Ubah") }
                }
                Button(onClick = onMove, modifier = Modifier.fillMaxWidth()) {
                    Text("Pindahkan $count file")
                }
            } else {
                Button(onClick = onChooseDestination, modifier = Modifier.fillMaxWidth()) {
                    Text("Pilih folder tujuan")
                }
            }
        }
    }
}
