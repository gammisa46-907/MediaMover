package com.example.mediamover

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Memindahkan file ke folder tujuan.
 * 1) Coba pindah langsung lewat provider (instan, tanpa menyalin data).
 * 2) Jika tidak didukung (mis. beda penyimpanan), salin lalu hapus file asal.
 * Nama file yang bentrok otomatis diberi akhiran " (1)", " (2)", dst.
 */
object FileMover {

    suspend fun move(
        context: Context,
        items: List<MediaItem>,
        destTree: Uri,
        onProgress: (done: Int, total: Int, name: String) -> Unit
    ): MoveResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val destDocId = DocumentsContract.getTreeDocumentId(destTree)
        val destParentUri = DocumentsContract.buildDocumentUriUsingTree(destTree, destDocId)
        val destAuthority = destTree.authority

        val existing = HashSet<String>(listExistingNames(resolver, destTree, destDocId))
        val moved = HashSet<String>()
        val failed = ArrayList<String>()
        var skipped = 0

        for ((index, item) in items.withIndex()) {
            onProgress(index, items.size, item.name)

            // File sudah berada di folder tujuan
            if (item.uri.authority == destAuthority && item.parentDocId == destDocId) {
                skipped++
                continue
            }

            val targetName = uniqueName(item.name, existing)
            var success = false

            if (targetName == item.name) {
                success = tryProviderMove(resolver, item, destParentUri)
            }
            if (!success) {
                success = copyAndDelete(resolver, item, destParentUri, targetName)
            }

            if (success) {
                existing.add(targetName.lowercase())
                moved.add(item.key)
            } else {
                failed.add(item.name)
            }
        }
        onProgress(items.size, items.size, "")
        MoveResult(moved, failed, skipped)
    }

    private fun tryProviderMove(resolver: ContentResolver, item: MediaItem, destParentUri: Uri): Boolean {
        return try {
            DocumentsContract.moveDocument(resolver, item.uri, item.parentUri, destParentUri) != null
        } catch (e: Exception) {
            false
        }
    }

    private fun copyAndDelete(
        resolver: ContentResolver,
        item: MediaItem,
        destParentUri: Uri,
        targetName: String
    ): Boolean {
        var created: Uri? = null
        return try {
            val target = DocumentsContract.createDocument(resolver, destParentUri, item.mime, targetName)
                ?: return false
            created = target

            val input = resolver.openInputStream(item.uri) ?: throw IOException("Tidak bisa membaca file")
            input.use { ins ->
                val output = resolver.openOutputStream(target, "w") ?: throw IOException("Tidak bisa menulis file")
                output.use { outs -> ins.copyTo(outs) }
            }

            // Hapus file asal hanya jika salinan berhasil
            if (!DocumentsContract.deleteDocument(resolver, item.uri)) {
                throw IOException("Gagal menghapus file asal")
            }
            true
        } catch (e: Exception) {
            // Bersihkan salinan yang tidak lengkap agar tidak ada duplikat
            val partial = created
            if (partial != null) {
                try {
                    DocumentsContract.deleteDocument(resolver, partial)
                } catch (ignored: Exception) {
                }
            }
            false
        }
    }

    private fun listExistingNames(resolver: ContentResolver, tree: Uri, docId: String): Set<String> {
        val names = HashSet<String>()
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        try {
            resolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(0)
                    if (n != null) names.add(n.lowercase())
                }
            }
        } catch (e: Exception) {
        }
        return names
    }

    private fun uniqueName(name: String, existing: Set<String>): String {
        if (name.lowercase() !in existing) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        for (i in 1..9999) {
            val candidate = "$base ($i)$ext"
            if (candidate.lowercase() !in existing) return candidate
        }
        return "${System.currentTimeMillis()}_$name"
    }
}
