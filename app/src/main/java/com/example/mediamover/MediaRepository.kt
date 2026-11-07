package com.example.mediamover

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.BaseColumns
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File

/** Sumber data media: MediaStore (cepat, tanpa izin berbahaya) + pemindaian mendalam opsional. */
object MediaRepository {

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")
    private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "3gp", "avi", "mov", "m4v", "ts", "flv", "wmv", "mpg", "mpeg")

    private val SKIP_DIR_NAMES = setOf(
        ".thumbnails", "thumbnails", ".thumbs", "cache", ".cache", ".trash", ".trashed", "lost+found"
    )

    // ------------------------------------------------------------------ MediaStore

    /**
     * Membaca seluruh gambar dan video dari MediaStore.
     * [verifyExists] = true membuang entri yang file-nya sudah tidak ada (hanya untuk mode akses penuh).
     */
    fun scanMediaStore(context: Context, verifyExists: Boolean): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        queryCollection(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, verifyExists, out)
        queryCollection(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, verifyExists, out)
        return out
    }

    private fun queryCollection(
        context: Context,
        collection: Uri,
        isVideo: Boolean,
        verifyExists: Boolean,
        out: MutableList<MediaItem>
    ) {
        val projection = ArrayList<String>()
        projection.add(BaseColumns._ID)
        projection.add(MediaStore.MediaColumns.DISPLAY_NAME)
        projection.add(MediaStore.MediaColumns.MIME_TYPE)
        projection.add(MediaStore.MediaColumns.SIZE)
        projection.add(MediaStore.MediaColumns.DATE_MODIFIED)
        projection.add(MediaStore.MediaColumns.DATA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
        }
        if (isVideo) {
            projection.add(MediaStore.Video.VideoColumns.DURATION)
        }

        val cursor = context.contentResolver.query(
            collection, projection.toTypedArray(), null, null, null
        ) ?: return

        cursor.use { c ->
            val iId = c.getColumnIndexOrThrow(BaseColumns._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iDate = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val iData = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val iRel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                -1
            }
            val iDur = if (isVideo) c.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION) else -1
            val prefix = if (isVideo) "v:" else "i:"

            while (c.moveToNext()) {
                val name = c.getString(iName) ?: continue
                if (name.startsWith(".")) continue

                val data = if (c.isNull(iData)) null else c.getString(iData)
                val path = if (data.isNullOrBlank()) null else data
                if (verifyExists && path != null && !File(path).exists()) continue

                val id = c.getLong(iId)
                val rawMime = if (c.isNull(iMime)) null else c.getString(iMime)
                val mime = if (rawMime.isNullOrBlank()) guessMime(name, isVideo) else rawMime
                val size = if (c.isNull(iSize)) 0L else c.getLong(iSize)
                val modified = (if (c.isNull(iDate)) 0L else c.getLong(iDate)) * 1000L
                val duration = if (iDur >= 0 && !c.isNull(iDur)) c.getLong(iDur) else 0L
                val rel = if (iRel >= 0 && !c.isNull(iRel)) c.getString(iRel) else null

                val folder = folderInfo(path, rel)

                out.add(
                    MediaItem(
                        key = prefix + id,
                        uri = ContentUris.withAppendedId(collection, id),
                        name = name,
                        mime = mime,
                        size = size,
                        modified = modified,
                        isVideo = isVideo,
                        durationMs = duration,
                        folderKey = folder.first,
                        folderLabel = folder.second,
                        folderPath = folder.third,
                        path = path
                    )
                )
            }
        }
    }

    private fun folderInfo(path: String?, relativePath: String?): Triple<String, String, String> {
        if (path != null) {
            val parent = File(path).parent
            if (parent != null) {
                return Triple(parent, lastSegment(parent), FolderUtil.prettyPath(parent))
            }
        }
        val rel = (relativePath ?: "").trimEnd('/')
        if (rel.isNotEmpty()) {
            return Triple("rel:$rel", lastSegment(rel), rel)
        }
        return Triple("?", "Lainnya", "Lainnya")
    }

    private fun lastSegment(path: String): String {
        val t = path.trimEnd('/')
        val s = t.substringAfterLast('/')
        return if (s.isEmpty()) t else s
    }

    private fun guessMime(name: String, isVideo: Boolean): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        val fromExt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (fromExt != null) return fromExt
        return if (isVideo) "video/*" else "image/*"
    }

    // ------------------------------------------------------------------ Pemindaian mendalam

    private class Node(val dir: File, val depth: Int)

    /**
     * Menelusuri seluruh penyimpanan lewat File API untuk menemukan gambar/video yang tidak
     * diindeks MediaStore (mis. di folder .nomedia atau folder tersembunyi).
     * Hanya dipakai pada mode akses penuh.
     */
    fun deepScan(knownPaths: Set<String>, shouldContinue: () -> Boolean): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        val stack = ArrayList<Node>()
        for (root in storageRoots()) {
            stack.add(Node(root, 0))
        }
        val maxItems = 100000
        val maxDepth = 16

        while (stack.isNotEmpty()) {
            if (!shouldContinue() || out.size >= maxItems) break
            val node = stack.removeAt(stack.size - 1)
            val children = try {
                node.dir.listFiles()
            } catch (e: Exception) {
                null
            } ?: continue

            for (f in children) {
                try {
                    val name = f.name
                    if (f.isDirectory) {
                        if (node.depth >= maxDepth) continue
                        if (SKIP_DIR_NAMES.contains(name.lowercase())) continue
                        // Folder Android/ (data, obb, media) tidak ditelusuri
                        if (name == "Android" && node.depth == 0) continue
                        // Hindari loop symlink
                        if (f.canonicalPath != f.absolutePath) continue
                        stack.add(Node(f, node.depth + 1))
                    } else {
                        if (name.startsWith(".")) continue
                        val ext = name.substringAfterLast('.', "").lowercase()
                        val isImage = IMAGE_EXT.contains(ext)
                        val isVideo = VIDEO_EXT.contains(ext)
                        if (!isImage && !isVideo) continue
                        val abs = f.absolutePath
                        if (knownPaths.contains(abs)) continue
                        val size = f.length()
                        if (size < 10 * 1024) continue

                        val parent = f.parent ?: continue
                        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                            ?: (if (isVideo) "video/*" else "image/*")
                        out.add(
                            MediaItem(
                                key = "f:$abs",
                                uri = Uri.fromFile(f),
                                name = name,
                                mime = mime,
                                size = size,
                                modified = f.lastModified(),
                                isVideo = isVideo,
                                durationMs = 0L,
                                folderKey = parent,
                                folderLabel = lastSegment(parent),
                                folderPath = FolderUtil.prettyPath(parent),
                                path = abs
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Lewati entri yang tidak bisa dibaca
                }
            }
        }
        return out
    }

    private fun storageRoots(): List<File> {
        val roots = ArrayList<File>()
        roots.add(Environment.getExternalStorageDirectory())
        try {
            val storage = File("/storage").listFiles()
            if (storage != null) {
                for (f in storage) {
                    if (f.isDirectory && f.name != "emulated" && f.name != "self" && f.canRead()) {
                        roots.add(f)
                    }
                }
            }
        } catch (e: Exception) {
        }
        return roots
    }
}
