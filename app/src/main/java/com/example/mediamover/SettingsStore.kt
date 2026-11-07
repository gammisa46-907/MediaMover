package com.example.mediamover

import android.content.Context
import android.net.Uri

/** Menyimpan pengaturan (folder tujuan, filter, urutan, tampilan) di SharedPreferences. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("media_mover", Context.MODE_PRIVATE)

    fun loadDestination(): Uri? {
        val raw = prefs.getString(KEY_DEST, null)
        return if (raw.isNullOrBlank()) null else Uri.parse(raw)
    }

    fun saveDestination(uri: Uri?) {
        prefs.edit().putString(KEY_DEST, uri?.toString() ?: "").apply()
    }

    var deepScan: Boolean
        get() = prefs.getBoolean(KEY_DEEP, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DEEP, value).apply()
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

    var viewMode: ViewMode
        get() = try {
            ViewMode.valueOf(prefs.getString(KEY_VIEW, ViewMode.GRID.name) ?: ViewMode.GRID.name)
        } catch (e: Exception) {
            ViewMode.GRID
        }
        set(value) {
            prefs.edit().putString(KEY_VIEW, value.name).apply()
        }

    private companion object {
        const val KEY_DEST = "destination"
        const val KEY_DEEP = "deep_scan"
        const val KEY_FILTER = "filter"
        const val KEY_SORT = "sort"
        const val KEY_VIEW = "view_mode"
    }
}
