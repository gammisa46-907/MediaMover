@file:OptIn(ExperimentalFoundationApi::class)

package com.example.mediamover

import android.net.Uri
import android.text.format.Formatter
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** Penampil layar penuh: gambar (zoom) dan video (pemutar bawaan Android), geser untuk berpindah file. */
@Composable
fun ViewerScreen(
    viewer: ViewerState,
    selected: Set<String>,
    destination: Uri?,
    onClose: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onMove: (MediaItem) -> Unit,
    onChooseDestination: () -> Unit
) {
    val context = LocalContext.current
    val items = viewer.items
    val pagerState = rememberPagerState(initialPage = viewer.index, pageCount = { items.size })
    var showUi by remember { mutableStateOf(true) }
    val safePage = pagerState.currentPage.coerceIn(0, items.size - 1)
    val current = items[safePage]
    val sizeText = remember(current.size) { Formatter.formatShortFileSize(context, current.size) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { index -> items[index].key }
        ) { page ->
            val item = items[page]
            if (item.isVideo) {
                if (page == pagerState.currentPage) {
                    VideoPlayer(uri = item.uri)
                } else {
                    VideoPlaceholder(item = item)
                }
            } else {
                ZoomableImage(item = item, onTap = { showUi = !showUi })
            }
        }

        if (showUi) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current.name,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${safePage + 1} / ${items.size} - $sizeText",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(12.dp)
            ) {
                Text(
                    text = current.folderPath,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onToggleSelect(current.key) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (selected.contains(current.key)) "Batal pilih" else "Pilih")
                    }
                    Button(
                        onClick = {
                            if (destination == null) onChooseDestination() else onMove(current)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (destination == null) "Pilih tujuan" else "Pindahkan")
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlaceholder(item: MediaItem) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        MediaThumb(item = item, modifier = Modifier.fillMaxSize(), sizePx = 512)
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Video",
            tint = Color.White,
            modifier = Modifier.size(64.dp)
        )
    }
}

@Composable
private fun VideoPlayer(uri: Uri) {
    val context = LocalContext.current
    val videoView = remember(uri) { VideoView(context) }
    val container = remember(uri) {
        FrameLayout(context).apply {
            addView(
                videoView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
        }
    }

    DisposableEffect(uri) {
        val controller = MediaController(context)
        controller.setAnchorView(videoView)
        videoView.setMediaController(controller)
        videoView.setOnPreparedListener { videoView.start() }
        videoView.setOnErrorListener { _, _, _ ->
            Toast.makeText(context, "Tidak bisa memutar video ini", Toast.LENGTH_SHORT).show()
            true
        }
        videoView.setVideoURI(uri)
        onDispose {
            try {
                controller.hide()
            } catch (e: Exception) {
            }
            videoView.stopPlayback()
        }
    }

    AndroidView(factory = { container }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun ZoomableImage(item: MediaItem, onTap: () -> Unit) {
    val context = LocalContext.current
    var scale by remember(item.key) { mutableStateOf(1f) }
    var offset by remember(item.key) { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val request = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(2048)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(item.key) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .pointerInput(item.key) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multiTouch = event.changes.size > 1
                        if (multiTouch || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            val maxX = boxSize.width * (newScale - 1f) / 2f
                            val maxY = boxSize.height * (newScale - 1f) / 2f
                            val moved = offset + pan
                            offset = if (newScale > 1f) {
                                Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
                            } else {
                                Offset.Zero
                            }
                            scale = newScale
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        AsyncImage(
            model = request,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}
