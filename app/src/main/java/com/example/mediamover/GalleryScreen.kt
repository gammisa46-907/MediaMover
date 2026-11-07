@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.mediamover

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun GalleryScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onFilter: (MediaFilter) -> Unit,
    onSort: (SortOption) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onMove: () -> Unit,
    onChooseDestination: () -> Unit
) {
    val selecting = state.selected.isNotEmpty()
    val context = LocalContext.current

    BackHandler(enabled = selecting) { onClearSelection() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selecting) {
                val allSelected = state.selected.size == state.visible.size
                TopAppBar(
                    title = { Text("${state.selected.size} dipilih") },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Batal pilih")
                        }
                    },
                    actions = {
                        TextButton(onClick = { if (allSelected) onClearSelection() else onSelectAll() }) {
                            Text(if (allSelected) "Batalkan semua" else "Pilih semua")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Media Mover") },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Muat ulang")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Kelola folder")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (selecting) {
                MoveBar(
                    count = state.selected.size,
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
            FilterRow(state = state, onFilter = onFilter, onSort = onSort)

            Box(modifier = Modifier.fillMaxSize()) {
                if (state.visible.isEmpty() && !state.loading) {
                    Text(
                        text = if (state.totalCount == 0) {
                            "Tidak ada gambar atau video di folder sumber."
                        } else {
                            "Tidak ada file untuk filter ini."
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        textAlign = TextAlign.Center
                    )
                } else {
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
                                    if (selecting) onToggleSelect(item.key) else openItem(context, item)
                                },
                                onLongClick = { onToggleSelect(item.key) }
                            )
                        }
                    }
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

@Composable
private fun FilterRow(
    state: UiState,
    onFilter: (MediaFilter) -> Unit,
    onSort: (SortOption) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

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
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(primary.copy(alpha = 0.35f))
            )
        }

        // Lencana ukuran file (dan ikon putar untuk video)
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

@Composable
private fun MoveBar(
    count: Int,
    destination: Uri?,
    onMove: () -> Unit,
    onChooseDestination: () -> Unit
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
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

private fun openItem(context: Context, item: MediaItem) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, item.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Tidak ada aplikasi untuk membuka file ini", Toast.LENGTH_SHORT).show()
    }
}
