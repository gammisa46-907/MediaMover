package com.example.mediamover

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

object FolderUtil {

    /** Nama yang mudah dibaca untuk sebuah folder (tree URI), mis. "Internal/DCIM/Camera". */
    fun displayName(treeUri: Uri): String {
        val id = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            return treeUri.lastPathSegment ?: treeUri.toString()
        }
        if (!id.contains(':')) return id
        val volume = id.substringBefore(':')
        val path = id.substringAfter(':', "")
        if (volume.equals("raw", ignoreCase = true)) return path
        val vol = if (volume.equals("primary", ignoreCase = true)) "Internal" else volume
        return if (path.isEmpty()) vol else "$vol/$path"
    }

    /** Hanya nama folder terakhir. */
    fun shortName(treeUri: Uri): String {
        val full = displayName(treeUri).trimEnd('/')
        return full.substringAfterLast('/')
    }

    /** Mengubah tree URI penyimpanan lokal menjadi path absolut, atau null jika bukan penyimpanan lokal. */
    fun treeToPath(treeUri: Uri): String? {
        if (treeUri.authority != "com.android.externalstorage.documents") return null
        val id = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            return null
        }
        if (!id.contains(':')) return null
        val volume = id.substringBefore(':')
        val rel = id.substringAfter(':', "")
        val base = if (volume.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$volume"
        }
        return if (rel.isEmpty()) base else "$base/$rel"
    }

    /** Path absolut -> teks ringkas, mis. "/storage/emulated/0/DCIM" menjadi "Internal/DCIM". */
    fun prettyPath(path: String): String {
        val internalPrefixes = listOf("/storage/emulated/0", "/storage/self/primary", "/sdcard", "/mnt/sdcard")
        for (p in internalPrefixes) {
            if (path == p || path.startsWith("$p/")) return "Internal" + path.removePrefix(p)
        }
        if (path.startsWith("/storage/")) {
            val rest = path.removePrefix("/storage/")
            val vol = rest.substringBefore('/')
            val tail = rest.substring(vol.length)
            return "SD ($vol)$tail"
        }
        return path
    }
}
