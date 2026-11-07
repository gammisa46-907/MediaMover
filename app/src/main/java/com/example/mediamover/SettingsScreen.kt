@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mediamover

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onAddSource: () -> Unit,
    onRemoveSource: (Uri) -> Unit,
    onChooseDestination: () -> Unit,
    onToggleSubfolders: (Boolean) -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (canGoBack) "Kelola folder" else "Media Mover") },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!canGoBack) {
                item {
                    Text(
                        "Selamat datang! Tambahkan minimal satu folder sumber untuk mulai " +
                            "menampilkan gambar dan video Anda.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            item {
                Text("Folder sumber", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Text(
                    "Gambar dan video dari folder-folder ini akan ditampilkan.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.sources.isEmpty()) {
                item {
                    Text("Belum ada folder sumber.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(items = state.sources, key = { it.toString() }) { uri ->
                FolderRow(
                    name = FolderUtil.displayName(uri),
                    onRemove = { onRemoveSource(uri) }
                )
            }
            item {
                OutlinedButton(onClick = onAddSource, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Tambah folder sumber")
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            item {
                Text("Folder tujuan", style = MaterialTheme.typography.titleMedium)
            }
            item {
                val dest = state.destination
                if (dest != null) {
                    FolderRow(name = FolderUtil.displayName(dest), onRemove = null)
                } else {
                    Text("Belum dipilih.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                OutlinedButton(onClick = onChooseDestination, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.destination == null) "Pilih folder tujuan" else "Ubah folder tujuan")
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sertakan subfolder", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Pindai juga semua folder di dalam folder sumber.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = state.includeSubfolders,
                        onCheckedChange = onToggleSubfolders
                    )
                }
            }

            item {
                Text(
                    "Catatan: Android 11 ke atas tidak mengizinkan memilih root penyimpanan, " +
                        "folder Download, atau Android/data secara langsung. Pilih subfolder " +
                        "seperti DCIM/Camera, Pictures, Movies, atau subfolder di dalam Download. " +
                        "Setelah masuk ke folder yang diinginkan, ketuk \"Gunakan folder ini\".",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (canGoBack) {
                item {
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Selesai")
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(name: String, onRemove: (() -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus folder")
                }
            }
        }
    }
}
