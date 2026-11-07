package com.example.mediamover

import android.net.Uri
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
}
