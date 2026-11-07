package com.example.mediamover

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Membuat nama baru dari pola. Token yang didukung:
 * {n} nomor urut, {name} nama asli (tanpa ekstensi), {date} yyyyMMdd, {time} HHmmss, {folder} nama folder.
 * Ekstensi file selalu dipertahankan.
 */
object BatchRename {

    private val INVALID_CHARS = Regex("[\\\\/:*?\"<>|]")

    fun generate(items: List<MediaItem>, pattern: String, start: Int, digits: Int): List<String> {
        val names = ArrayList<String>()
        val used = HashSet<String>()
        val dateFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        val timeFmt = SimpleDateFormat("HHmmss", Locale.US)

        for ((i, item) in items.withIndex()) {
            val dot = item.name.lastIndexOf('.')
            val base = if (dot > 0) item.name.substring(0, dot) else item.name
            val ext = if (dot > 0) item.name.substring(dot) else ""
            val date = Date(item.modified)

            var stem = pattern
                .replace("{n}", (start + i).toString().padStart(digits, '0'))
                .replace("{name}", base)
                .replace("{date}", dateFmt.format(date))
                .replace("{time}", timeFmt.format(date))
                .replace("{folder}", item.folderLabel)
            stem = INVALID_CHARS.replace(stem, "_").trim()
            if (stem.isEmpty()) stem = base

            var candidate = stem + ext
            var k = 1
            while (!used.add(candidate.lowercase())) {
                candidate = "$stem ($k)$ext"
                k++
            }
            names.add(candidate)
        }
        return names
    }
}
