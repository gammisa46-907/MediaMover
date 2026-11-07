@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mediamover

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DuplicatesScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRescan: () -> Unit,
    onCancel: () -> Unit,
    onAutoSelect: () -> Unit,
    onClearSelection: () -> Unit,
    onToggle: (String) -> Unit,
    onPreview: (List<MediaItem>, Int) -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onChooseDestination: () -> Unit
) {
    val context = LocalContext.current
    val dup = state.dup
    var confirmDelete by remember { mutableStateOf(false) }

    val selectedCount = dup.selected.size
    val selectedBytes = remember(dup.groups, dup.selected) {
        var total = 0L
        for (g in dup.groups) {
            for (item in g.items) {
                if (dup.selected.contains(item.key)) total += item.size
            }
        }
        total
    }
    val fullyChosenGroups = remember(dup.groups, dup.selected) {
        dup.groups.count { g -> g.items.all { dup.selected.contains(it.key) } }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Pencari duplikat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                },
                actions = {
                    IconButton(onClick = onRescan, enabled = !dup.running) {
                        Icon(Icons.Default.Refresh, contentDescription = "Pindai ulang")
                    }
                }
            )
        },
        bottomBar = {
            if (dup.groups.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "$selectedCount file dipilih - " +
                                Formatter.formatShortFileSize(context, selectedBytes),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (state.destination == null) onChooseDestination() else onMove()
                                },
                                enabled = selectedCount > 0 && !dup.running,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (state.destination == null) "Pilih tujuan" else "Pindahkan")
                            }
                            Button(
                                onClick = { confirmDelete = true },
                                enabled = selectedCount > 0 && !dup.running,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Hapus")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (dup.running) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (dup.total > 0) {
                        LinearProgressIndicator(
                            progress = { dup.done.toFloat() / dup.total.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (dup.total > 0) "Memeriksa ${dup.done} / ${dup.total} file kandidat..." else "Menyiapkan...",
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onCancel) { Text("Batal") }
                }
            } else if (!dup.scanned) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Cari gambar dan video yang isinya persis sama.",
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRescan) { Text("Mulai pindai") }
                }
            } else if (dup.groups.isEmpty()) {
                Text(
                    "Tidak ada file ganda yang ditemukan.",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                var wasted = 0L
                var extraFiles = 0
                for (g in dup.groups) {
                    wasted += g.size * (g.items.size - 1)
                    extraFiles += g.items.size - 1
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Column {
                            Text(
                                "${dup.groups.size} grup, $extraFiles file ganda. " +
                                    "Ruang yang bisa dihemat: ${Formatter.formatShortFileSize(context, wasted)}.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Pilihan otomatis menyimpan satu file tertua di setiap grup.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = onAutoSelect) { Text("Pilih otomatis") }
                                TextButton(onClick = onClearSelection) { Text("Kosongkan") }
                            }
                        }
                    }
                    items(items = dup.groups, key = { it.items[0].key }) { group ->
                        DupGroupCard(
                            group = group,
                            selected = dup.selected,
                            onToggle = onToggle,
                            onPreview = onPreview
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus $selectedCount file?") },
            text = {
                Text(
                    "File yang dipilih akan dihapus dari perangkat dan tidak bisa dikembalikan." +
                        if (fullyChosenGroups > 0) {
                            "\n\nPeringatan: di $fullyChosenGroups grup semua file ikut dipilih, " +
                                "sehingga tidak ada salinan yang tersisa."
                        } else {
                            ""
                        }
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun DupGroupCard(
    group: DupGroup,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onPreview: (List<MediaItem>, Int) -> Unit
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "${group.items.size} file - ${Formatter.formatShortFileSize(context, group.size)} per file",
                style = MaterialTheme.typography.titleSmall
            )
            for ((index, item) in group.items.withIndex()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selected.contains(item.key),
                        onCheckedChange = { onToggle(item.key) }
                    )
                    MediaThumb(
                        item = item,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onPreview(group.items, index) }
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            item.folderPath,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            DateFormat.format("dd MMM yyyy HH:mm", item.modified).toString(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
