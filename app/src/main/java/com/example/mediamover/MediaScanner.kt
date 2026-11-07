package com.example.mediamover

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap

/** Memindai folder (Storage Access Framework) dan mengumpulkan file gambar/video. */
object MediaScanner {

    private val COLUMNS = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    fun scan(resolver: ContentResolver, treeUri: Uri, recursive: Boolean): List<MediaItem> {
        val result = ArrayList<MediaItem>()
        val authority = treeUri.authority ?: return result
        val rootId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            return result
        }

        val stack = ArrayList<String>()
        val visited = HashSet<String>()
        stack.add(rootId)

        while (stack.isNotEmpty()) {
            val parentId = stack.removeAt(stack.size - 1)
            if (!visited.add(parentId)) continue

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)

            try {
                resolver.query(childrenUri, COLUMNS, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val docId = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        // Lewati file/folder tersembunyi (.thumbnails, .trashed-..., dll)
                        if (name.startsWith(".")) continue

                        val rawMime = c.getString(2) ?: ""
                        val size = if (c.isNull(3)) 0L else c.getLong(3)
                        val modified = if (c.isNull(4)) 0L else c.getLong(4)

                        if (rawMime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (recursive) stack.add(docId)
                            continue
                        }

                        val mime = resolveMime(name, rawMime)
                        val isImage = mime.startsWith("image/")
                        val isVideo = mime.startsWith("video/")
                        if (!isImage && !isVideo) continue

                        result.add(
                            MediaItem(
                                key = "$authority|$docId",
                                uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                                docId = docId,
                                parentDocId = parentId,
                                parentUri = parentUri,
                                name = name,
                                mime = mime,
                                size = size,
                                modified = modified,
                                isVideo = isVideo
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Folder tidak bisa dibaca: lewati saja
            }
        }
        return result
    }

    private fun resolveMime(name: String, mime: String): String {
        if (mime.isNotEmpty() && mime != "application/octet-stream") return mime
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: mime
    }
}
