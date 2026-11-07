@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mediamover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
    onBack: () -> Unit,
    onOpenAllFiles: () -> Unit,
    onChooseDestination: () -> Unit,
    onToggleDeepScan: (Boolean) -> Unit
) {
    val full = state.access == AccessMode.FULL

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
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
            item {
                Text("Akses penyimpanan", style = MaterialTheme.typography.titleMedium)
            }
            item {
                InfoCard(
                    title = if (full) "Akses Semua File aktif" else "Mode MediaStore (aman)",
                    body = if (full) {
                        "Pemindahan file instan dan tanpa dialog konfirmasi tambahan. " +
                            "Pemindaian mendalam tersedia."
                    } else {
                        "Aplikasi membaca semua gambar dan video lewat izin media biasa. " +
                            "Memindahkan file dilakukan dengan menyalin ke folder tujuan lalu menghapus " +
                            "file asal. Di Android 11 ke atas, penghapusan meminta satu konfirmasi " +
                            "sistem untuk seluruh batch."
                    }
                )
            }
            if (state.canRequestAllFiles) {
                item {
                    OutlinedButton(onClick = onOpenAllFiles, modifier = Modifier.fillMaxWidth()) {
                        Text(if (full) "Kelola Akses Semua File" else "Aktifkan Akses Semua File")
                    }
                }
                item {
                    Text(
                        "Akses Semua File opsional. Aktifkan hanya jika Anda ingin pemindahan instan " +
                            "tanpa konfirmasi dan pemindaian mendalam. Setelah mengubahnya, kembali ke " +
                            "aplikasi dan daftar akan dimuat ulang otomatis.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pemindaian mendalam", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (full) {
                                "Sertakan gambar dan video di folder .nomedia dan folder tersembunyi. " +
                                    "Bisa menampilkan banyak file cache aplikasi."
                            } else {
                                "Hanya tersedia saat Akses Semua File aktif."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = state.deepScan && full,
                        onCheckedChange = onToggleDeepScan,
                        enabled = full
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            item {
                Text("Folder tujuan", style = MaterialTheme.typography.titleMedium)
            }
            item {
                val dest = state.destination
                if (dest != null) {
                    InfoCard(title = FolderUtil.displayName(dest), body = null)
                } else {
                    Text("Belum dipilih.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Button(onClick = onChooseDestination, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.destination == null) "Pilih folder tujuan" else "Ubah folder tujuan")
                }
            }
            item {
                Text(
                    "Catatan: Android 11 ke atas tidak mengizinkan memilih root penyimpanan, folder " +
                        "Download, atau Android/data secara langsung. Pilih subfolder, atau buat folder " +
                        "baru di layar pemilih folder, lalu ketuk \"Gunakan folder ini\".",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String?) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (body != null) {
                Spacer(Modifier.height(4.dp))
                Text(text = body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
