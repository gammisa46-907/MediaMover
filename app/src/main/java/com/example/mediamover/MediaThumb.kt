package com.example.mediamover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.util.Locale

/** Thumbnail gambar/video dengan ukuran decode dibatasi agar hemat memori untuk ribuan file. */
@Composable
fun MediaThumb(item: MediaItem, modifier: Modifier = Modifier, sizePx: Int = 256) {
    val context = LocalContext.current
    val request = remember(item.uri, sizePx) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(sizePx)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = item.name,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}
