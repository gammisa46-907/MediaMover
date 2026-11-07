package com.example.mediamover

import android.content.Context
import java.security.MessageDigest

/**
 * Mencari file ganda dengan 3 tahap agar hemat I/O:
 * 1) ukuran file sama, 2) hash 64 KB pertama sama, 3) hash SHA-256 seluruh isi sama.
 */
object DuplicateFinder {

    fun find(
        context: Context,
        items: List<MediaItem>,
        onProgress: (done: Int, total: Int) -> Unit,
        shouldContinue: () -> Boolean
    ): List<DupGroup> {
        val bySize = HashMap<Long, MutableList<MediaItem>>()
        for (item in items) {
            if (item.size <= 0L) continue
            bySize.getOrPut(item.size) { ArrayList() }.add(item)
        }

        var total = 0
        val candidates = ArrayList<Pair<Long, List<MediaItem>>>()
        for ((size, group) in bySize) {
            if (group.size >= 2) {
                candidates.add(Pair(size, group))
                total += group.size
            }
        }

        val result = ArrayList<DupGroup>()
        var done = 0
        onProgress(0, total)

        for ((size, group) in candidates) {
            if (!shouldContinue()) break

            val byQuick = HashMap<String, MutableList<MediaItem>>()
            for (item in group) {
                if (!shouldContinue()) break
                val q = hashOf(context, item, 64L * 1024L) ?: continue
                byQuick.getOrPut(q) { ArrayList() }.add(item)
            }

            for (quickGroup in byQuick.values) {
                if (quickGroup.size < 2) continue
                val byFull = HashMap<String, MutableList<MediaItem>>()
                for (item in quickGroup) {
                    if (!shouldContinue()) break
                    val h = hashOf(context, item, Long.MAX_VALUE) ?: continue
                    byFull.getOrPut(h) { ArrayList() }.add(item)
                }
                for (g in byFull.values) {
                    if (g.size >= 2) result.add(DupGroup(size, g.sortedBy { it.modified }))
                }
            }

            done += group.size
            onProgress(done, total)
        }

        return result.sortedByDescending { it.size * (it.items.size - 1) }
    }

    private fun hashOf(context: Context, item: MediaItem, limit: Long): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val input = context.contentResolver.openInputStream(item.uri) ?: return null
            input.use { ins ->
                val buf = ByteArray(64 * 1024)
                var remaining = limit
                while (remaining > 0L) {
                    val toRead = if (remaining < buf.size.toLong()) remaining.toInt() else buf.size
                    val n = ins.read(buf, 0, toRead)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                    remaining -= n.toLong()
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
