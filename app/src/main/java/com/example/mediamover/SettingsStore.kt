package com.example.mediamover

import android.content.Context
import android.net.Uri

/** Menyimpan pengaturan (folder sumber, folder tujuan, filter, urutan) di SharedPreferences. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("media_mover", Context.MODE_PRIVATE)

    fun loadSources(): List<Uri> {
        val raw = prefs.getString(KEY_SOURCES, "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }.map { Uri.parse(it) }
    }

    fun saveSources(list: List<Uri>) {
        prefs.edit().putString(KEY_SOURCES, list.joinToString("\n") { it.toString() }).apply()
    }

    fun loadDestination(): Uri? {
        val raw = prefs.getString(KEY_DEST, null)
        return if (raw.isNullOrBlank()) null else Uri.parse(raw)
    }

    fun saveDestination(uri: Uri?) {
        prefs.edit().putString(KEY_DEST, uri?.toString() ?: "").apply()
    }

    var includeSubfolders: Boolean
        get() = prefs.getBoolean(KEY_SUBFOLDERS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SUBFOLDERS, value).apply()
        }

    var filter: MediaFilter
        get() = try {
            MediaFilter.valueOf(prefs.getString(KEY_FILTER, MediaFilter.ALL.name) ?: MediaFilter.ALL.name)
        } catch (e: Exception) {
            MediaFilter.ALL
        }
        set(value) {
            prefs.edit().putString(KEY_FILTER, value.name).apply()
        }

    var sort: SortOption
        get() = try {
            SortOption.valueOf(prefs.getString(KEY_SORT, SortOption.NEWEST.name) ?: SortOption.NEWEST.name)
        } catch (e: Exception) {
            SortOption.NEWEST
        }
        set(value) {
            prefs.edit().putString(KEY_SORT, value.name).apply()
        }

    private companion object {
        const val KEY_SOURCES = "sources"
        const val KEY_DEST = "destination"
        const val KEY_SUBFOLDERS = "include_subfolders"
        const val KEY_FILTER = "filter"
        const val KEY_SORT = "sort"
    }
}
