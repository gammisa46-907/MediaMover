package com.example.mediamover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PermissionScreen(onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Media Mover", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                "Untuk menampilkan semua gambar dan video di perangkat Anda secara otomatis, " +
                    "aplikasi memerlukan izin akses media. Anda tidak perlu memilih folder satu per satu.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                Text("Izinkan akses media")
            }
            TextButton(onClick = onOpenSettings) {
                Text("Buka pengaturan aplikasi")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Jika dialog izin tidak muncul, buka pengaturan aplikasi lalu izinkan akses Foto dan video.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
