package com.example.mediamover

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediaMoverTheme {
                MediaMoverRoot()
            }
        }
    }
}

@Composable
fun MediaMoverRoot(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val sourcePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) vm.onSourcePicked(uri)
    }
    val destinationPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) vm.onDestinationPicked(uri)
    }

    LaunchedEffect(state.message) {
        val msg = state.message
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            vm.consumeMessage()
        }
    }

    val inSettings = state.showSettings || state.sources.isEmpty()

    BackHandler(enabled = state.showSettings && state.sources.isNotEmpty()) {
        vm.setShowSettings(false)
    }
    // Saat proses pemindahan berjalan, tombol kembali diabaikan
    BackHandler(enabled = state.moving != null) { }

    if (inSettings) {
        SettingsScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            canGoBack = state.sources.isNotEmpty(),
            onBack = { vm.setShowSettings(false) },
            onAddSource = { sourcePicker.launch(null) },
            onRemoveSource = { vm.removeSource(it) },
            onChooseDestination = { destinationPicker.launch(null) },
            onToggleSubfolders = { vm.setIncludeSubfolders(it) }
        )
    } else {
        GalleryScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onRefresh = { vm.refresh() },
            onOpenSettings = { vm.setShowSettings(true) },
            onFilter = { vm.setFilter(it) },
            onSort = { vm.setSort(it) },
            onToggleSelect = { vm.toggleSelect(it) },
            onSelectAll = { vm.selectAll() },
            onClearSelection = { vm.clearSelection() },
            onMove = { vm.moveSelected() },
            onChooseDestination = { destinationPicker.launch(null) }
        )
    }

    val moving = state.moving
    if (moving != null) {
        val fraction = if (moving.total == 0) 0f else moving.done.toFloat() / moving.total.toFloat()
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            title = { Text("Memindahkan file...") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${moving.done} / ${moving.total}")
                    if (moving.currentName.isNotEmpty()) {
                        Text(
                            moving.currentName,
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
