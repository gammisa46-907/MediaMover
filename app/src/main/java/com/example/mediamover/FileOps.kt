package com.example.mediamover

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * @param moved file yang sudah selesai dipindahkan (file asal sudah hilang)
 * @param pendingDelete file yang sudah disalin ke tujuan dan menunggu konfirmasi sistem untuk menghapus file asal (Android 11+)
 * @param copiedKept file yang sudah disalin tetapi file asalnya tidak bisa dihapus
 */
data class MoveOutcome(
    val moved: Set<String>,
    val failed: List<String>,
    val skipped: Int,
    val pendingDelete: List<MediaItem>,
    val copiedKept: Int
)

data class DeleteOutcome(
    val deleted: Set<String>,
    val failed: List<String>,
    val pending: List<MediaItem>
)

data class RenameOutcome(
    val renamed: Int,
    val failed: Int,
    val pending: List<MediaItem>,
    val pendingNames: List<String>
)

/**
 * Operasi file: pindah, hapus, ganti nama.
 *
 * - Mode FULL (akses semua file): memakai File API, pindah instan (renameTo) dan tanpa dialog tambahan.
 * - Mode MEDIA: menyalin ke folder tujuan (Storage Access Framework) lalu menghapus file asal lewat
 *   MediaStore. Di Android 11+ penghapusan butuh satu konfirmasi sistem untuk seluruh batch.
 */
object FileOps {

    // ------------------------------------------------------------------ Pindah

    suspend fun move(
        context: Context,
        items: List<MediaItem>,
        destTree: Uri,
        mode: AccessMode,
        onProgress: (done: Int, total: Int, name: String) -> Unit
    ): MoveOutcome = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val destPath = FolderUtil.treeToPath(destTree)
        val destDocId = DocumentsContract.getTreeDocumentId(destTree)
        val destParentUri = DocumentsContract.buildDocumentUriUsingTree(destTree, destDocId)

        val moved = HashSet<String>()
        val failed = ArrayList<String>()
        val pending = ArrayList<MediaItem>()
        val scanPaths = ArrayList<String>()
        val safNames = HashSet<String>()
        var safNamesLoaded = false
        var skipped = 0
        var copiedKept = 0

        for ((index, item) in items.withIndex()) {
            onProgress(index, items.size, item.name)
            val srcPath = item.path
            try {
                // Sudah berada di folder tujuan?
                if (srcPath != null && destPath != null) {
                    val srcParent = File(srcPath).parentFile
                    if (srcParent != null && srcParent.canonicalPath == File(destPath).canonicalPath) {
                        skipped++
                        continue
                    }
                }

                val useFileApi = mode == AccessMode.FULL && srcPath != null && destPath != null
                if (useFileApi) {
                    val src = File(srcPath!!)
                    val destDir = File(destPath!!)
                    if (!src.exists()) {
                        failed.add(item.name)
                        continue
                    }
                    val target = uniqueFile(destDir, item.name)
                    if (moveFile(src, target)) {
                        moved.add(item.key)
                        scanPaths.add(srcPath)
                        scanPaths.add(target.absolutePath)
                    } else {
                        failed.add(item.name)
                    }
                    continue
                }

                // Jalur Storage Access Framework: salin ke folder tujuan
                if (!safNamesLoaded) {
                    safNames.addAll(listChildNames(resolver, destTree, destDocId))
                    safNamesLoaded = true
                }
                val targetName = uniqueName(item.name, safNames)
                val created = copyToTree(resolver, item, destParentUri, targetName)
                if (created == null) {
                    failed.add(item.name)
                    continue
                }
                safNames.add(targetName.lowercase())
                if (destPath != null) scanPaths.add(File(destPath, targetName).absolutePath)

                // Hapus file asal setelah salinan berhasil
                if (mode == AccessMode.FULL && srcPath != null) {
                    if (File(srcPath).delete()) {
                        moved.add(item.key)
                        scanPaths.add(srcPath)
                    } else {
                        deleteQuietly(resolver, created)
                        failed.add(item.name)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pending.add(item)
                } else if (deleteViaResolver(resolver, item.uri)) {
                    moved.add(item.key)
                } else {
                    copiedKept++
                }
            } catch (e: Exception) {
                failed.add(item.name)
            }
        }

        scanAndWait(context, scanPaths)
        onProgress(items.size, items.size, "")
        MoveOutcome(moved, failed, skipped, pending, copiedKept)
    }

    // ------------------------------------------------------------------ Hapus

    suspend fun delete(
        context: Context,
        items: List<MediaItem>,
        mode: AccessMode,
        onProgress: (done: Int, total: Int, name: String) -> Unit
    ): DeleteOutcome = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val deleted = HashSet<String>()
        val failed = ArrayList<String>()
        val pending = ArrayList<MediaItem>()
        val scanPaths = ArrayList<String>()

        for ((index, item) in items.withIndex()) {
            onProgress(index, items.size, item.name)
            val path = item.path
            try {
                if (mode == AccessMode.FULL && path != null) {
                    val f = File(path)
                    if (!f.exists() || f.delete()) {
                        deleted.add(item.key)
                        scanPaths.add(path)
                    } else {
                        failed.add(item.name)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pending.add(item)
                } else if (deleteViaResolver(resolver, item.uri)) {
                    deleted.add(item.key)
                } else {
                    failed.add(item.name)
                }
            } catch (e: Exception) {
                failed.add(item.name)
            }
        }

        scanAndWait(context, scanPaths)
        onProgress(items.size, items.size, "")
        DeleteOutcome(deleted, failed, pending)
    }

    // ------------------------------------------------------------------ Ganti nama

    suspend fun rename(
        context: Context,
        items: List<MediaItem>,
        newNames: List<String>,
        mode: AccessMode,
        onProgress: (done: Int, total: Int, name: String) -> Unit
    ): RenameOutcome = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var renamed = 0
        var failed = 0
        val pendingItems = ArrayList<MediaItem>()
        val pendingNames = ArrayList<String>()
        val scanPaths = ArrayList<String>()

        for ((index, item) in items.withIndex()) {
            onProgress(index, items.size, item.name)
            val newName = newNames[index]
            val path = item.path
            try {
                if (mode == AccessMode.FULL && path != null) {
                    val src = File(path)
                    val parent = src.parentFile
                    if (parent == null || !src.exists()) {
                        failed++
                        continue
                    }
                    val target = uniqueFile(parent, newName)
                    if (src.renameTo(target)) {
                        renamed++
                        scanPaths.add(path)
                        scanPaths.add(target.absolutePath)
                    } else {
                        failed++
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pendingItems.add(item)
                    pendingNames.add(newName)
                } else if (renameViaResolver(resolver, item.uri, newName)) {
                    renamed++
                } else {
                    failed++
                }
            } catch (e: Exception) {
                failed++
            }
        }

        scanAndWait(context, scanPaths)
        onProgress(items.size, items.size, "")
        RenameOutcome(renamed, failed, pendingItems, pendingNames)
    }

    /** Dipanggil setelah pengguna menyetujui permintaan tulis sistem (Android 11+). Berjalan di thread latar. */
    fun applyMediaStoreRenames(context: Context, items: List<MediaItem>, names: List<String>): Pair<Int, Int> {
        val resolver = context.contentResolver
        var ok = 0
        var bad = 0
        for ((i, item) in items.withIndex()) {
            if (renameViaResolver(resolver, item.uri, names[i])) ok++ else bad++
        }
        return Pair(ok, bad)
    }

    // ------------------------------------------------------------------ Util

    private fun moveFile(src: File, target: File): Boolean {
        try {
            if (src.renameTo(target)) return true
        } catch (e: Exception) {
        }
        // Beda penyimpanan: salin lalu hapus file asal
        return try {
            FileInputStream(src).use { ins ->
                FileOutputStream(target).use { outs -> ins.copyTo(outs, 64 * 1024) }
            }
            target.setLastModified(src.lastModified())
            if (src.delete()) {
                true
            } else {
                target.delete()
                false
            }
        } catch (e: Exception) {
            try {
                target.delete()
            } catch (ignored: Exception) {
            }
            false
        }
    }

    private fun copyToTree(
        resolver: ContentResolver,
        item: MediaItem,
        destParentUri: Uri,
        targetName: String
    ): Uri? {
        var created: Uri? = null
        try {
            val target = DocumentsContract.createDocument(resolver, destParentUri, item.mime, targetName)
                ?: return null
            created = target

            val input = resolver.openInputStream(item.uri) ?: throw IOException("Tidak bisa membaca file")
            input.use { ins ->
                val output = resolver.openOutputStream(target, "w")
                    ?: throw IOException("Tidak bisa menulis file")
                output.use { outs -> ins.copyTo(outs, 64 * 1024) }
            }
            return target
        } catch (e: Exception) {
            // Bersihkan salinan yang tidak lengkap agar tidak ada duplikat
            val partial = created
            if (partial != null) deleteQuietly(resolver, partial)
            return null
        }
    }

    private fun deleteQuietly(resolver: ContentResolver, uri: Uri) {
        try {
            DocumentsContract.deleteDocument(resolver, uri)
        } catch (e: Exception) {
        }
    }

    private fun deleteViaResolver(resolver: ContentResolver, uri: Uri): Boolean {
        return try {
            resolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            false
        }
    }

    private fun renameViaResolver(resolver: ContentResolver, uri: Uri, newName: String): Boolean {
        return try {
            val values = ContentValues()
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
            resolver.update(uri, values, null, null) > 0
        } catch (e: Exception) {
            false
        }
    }

    private fun listChildNames(resolver: ContentResolver, tree: Uri, docId: String): Set<String> {
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

    private fun uniqueFile(dir: File, name: String): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        for (i in 1..9999) {
            val f = File(dir, "$base ($i)$ext")
            if (!f.exists()) return f
        }
        return File(dir, "${System.currentTimeMillis()}_$name")
    }

    /** Memberi tahu MediaStore tentang file yang berubah dan menunggu sampai selesai (maks. 15 detik). */
    fun scanAndWait(context: Context, paths: List<String>) {
        val distinct = paths.filter { it.isNotEmpty() }.distinct()
        if (distinct.isEmpty()) return
        val latch = CountDownLatch(distinct.size)
        try {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                distinct.toTypedArray(),
                null
            ) { _, _ -> latch.countDown() }
            latch.await(15, TimeUnit.SECONDS)
        } catch (e: Exception) {
        }
    }
}
