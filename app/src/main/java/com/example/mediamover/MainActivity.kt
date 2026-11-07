package com.example.mediamover

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediaMoverTheme {
                MediaMoverRoot(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Memperbarui mode akses jika pengguna mengubah izin di Pengaturan sistem
        vm.onResume()
    }
}

@Composable
fun MediaMoverRoot(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var renameItems by remember { mutableStateOf<List<MediaItem>?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        vm.onPermissionResult()
    }
    val destinationPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) vm.onDestinationPicked(uri)
    }
    val requestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        vm.onRequestResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(state.message) {
        val msg = state.message
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            vm.consumeMessage()
        }
    }

    // Meluncurkan dialog konfirmasi sistem (hapus/ubah file milik aplikasi lain) tepat sekali
    LaunchedEffect(state.pending?.id) {
        val p = state.pending
        if (p != null && !p.launched) {
            vm.markRequestLaunched()
            try {
                requestLauncher.launch(IntentSenderRequest.Builder(p.sender).build())
            } catch (e: Exception) {
                vm.onRequestResult(false)
            }
        }
    }

    val viewer = state.viewer

    if (state.access == AccessMode.NONE) {
        PermissionScreen(
            onRequest = { permissionLauncher.launch(StorageAccess.mediaPermissions()) },
            onOpenSettings = {
                try {
                    context.startActivity(StorageAccess.appSettingsIntent(context))
                } catch (e: Exception) {
                    Toast.makeText(context, "Tidak bisa membuka pengaturan", Toast.LENGTH_SHORT).show()
                }
            }
        )
    } else if (viewer != null) {
        ViewerScreen(
            viewer = viewer,
            selected = state.selected,
            destination = state.destination,
            onClose = { vm.closeViewer() },
            onToggleSelect = { vm.toggleSelect(it) },
            onMove = { item ->
                vm.closeViewer()
                vm.moveItems(listOf(item))
            },
            onChooseDestination = { destinationPicker.launch(null) }
        )
    } else if (state.screen == Screen.SETTINGS) {
        SettingsScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onBack = { vm.setScreen(Screen.GALLERY) },
            onOpenAllFiles = {
                try {
                    context.startActivity(StorageAccess.allFilesIntent(context))
                } catch (e: Exception) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    } catch (e2: Exception) {
                        Toast.makeText(context, "Tidak bisa membuka pengaturan", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onChooseDestination = { destinationPicker.launch(null) },
            onToggleDeepScan = { vm.setDeepScan(it) }
        )
    } else if (state.screen == Screen.DUPLICATES) {
        DuplicatesScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onBack = { vm.setScreen(Screen.GALLERY) },
            onRescan = { vm.startDuplicateScan() },
            onCancel = { vm.cancelDuplicateScan() },
            onAutoSelect = { vm.autoSelectDuplicates() },
            onClearSelection = { vm.clearDuplicateSelection() },
            onToggle = { vm.toggleDuplicate(it) },
            onPreview = { items, index -> vm.openViewer(items, index) },
            onMove = { vm.moveItems(vm.selectedDuplicateItems()) },
            onDelete = { vm.deleteItems(vm.selectedDuplicateItems()) },
            onChooseDestination = { destinationPicker.launch(null) }
        )
    } else {
        GalleryScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onRefresh = { vm.refresh() },
            onOpenSettings = { vm.setScreen(Screen.SETTINGS) },
            onOpenDuplicates = { vm.setScreen(Screen.DUPLICATES) },
            onRename = { renameItems = vm.selectedItemsSorted() },
            onSetSearching = { vm.setSearching(it) },
            onQuery = { vm.setQuery(it) },
            onFilter = { vm.setFilter(it) },
            onSort = { vm.setSort(it) },
            onViewMode = { vm.setViewMode(it) },
            onOpenAlbum = { vm.openAlbum(it) },
            onCloseAlbum = { vm.closeAlbum() },
            onToggleAlbum = { vm.toggleAlbumSelection(it) },
            onOpenItem = { index -> vm.openViewer(state.visible, index) },
            onToggleSelect = { vm.toggleSelect(it) },
            onSelectAll = { vm.selectAllVisible() },
            onClearSelection = { vm.clearSelection() },
            onMove = { vm.moveSelected() },
            onChooseDestination = { destinationPicker.launch(null) }
        )
    }

    // Tombol kembali: layar sekunder -> galeri, penampil -> tutup
    BackHandler(
        enabled = state.access != AccessMode.NONE && viewer == null && state.screen != Screen.GALLERY
    ) {
        vm.setScreen(Screen.GALLERY)
    }
    BackHandler(enabled = viewer != null) {
        vm.closeViewer()
    }
    // Saat proses berjalan, tombol kembali diabaikan
    BackHandler(enabled = state.busy != null) { }

    val items = renameItems
    if (items != null) {
        RenameDialog(
            items = items,
            onDismiss = { renameItems = null },
            onConfirm = { pattern, start, digits ->
                renameItems = null
                vm.renameSelected(pattern, start, digits)
            }
        )
    }

    val busy = state.busy
    if (busy != null) {
        val fraction = if (busy.total == 0) 0f else busy.done.toFloat() / busy.total.toFloat()
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            title = { Text(busy.title) },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${busy.done} / ${busy.total}")
                    if (busy.current.isNotEmpty()) {
                        Text(
                            busy.current,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        )
    }
}
