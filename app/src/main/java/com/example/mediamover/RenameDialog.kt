package com.example.mediamover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun RenameDialog(
    items: List<MediaItem>,
    onDismiss: () -> Unit,
    onConfirm: (pattern: String, start: Int, digits: Int) -> Unit
) {
    var pattern by remember { mutableStateOf("Media_{n}") }
    var startText by remember { mutableStateOf("1") }
    var digitsText by remember { mutableStateOf("3") }

    val start = startText.toIntOrNull() ?: 1
    val digits = (digitsText.toIntOrNull() ?: 1).coerceIn(1, 8)
    val preview = remember(pattern, start, digits, items) {
        BatchRename.generate(items.take(3), pattern, start, digits)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ganti nama ${items.size} file") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Pola nama") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("Mulai dari") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = digitsText,
                        onValueChange = { digitsText = it.filter { c -> c.isDigit() }.take(1) },
                        label = { Text("Digit") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "Token: {n} nomor urut, {name} nama asli, {date} tanggal, {time} jam, " +
                        "{folder} nama folder. Ekstensi file dipertahankan.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Contoh hasil:", style = MaterialTheme.typography.labelMedium)
                for (name in preview) {
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pattern, start, digits) },
                enabled = pattern.isNotBlank()
            ) {
                Text("Ganti nama")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
