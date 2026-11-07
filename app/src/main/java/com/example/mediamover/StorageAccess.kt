package com.example.mediamover

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/** Mendeteksi tingkat akses penyimpanan yang tersedia di perangkat. */
object StorageAccess {

    /** Izin baca media yang perlu diminta sesuai versi Android. */
    fun mediaPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else if (Build.VERSION.SDK_INT >= 29) {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    fun hasMediaPermission(context: Context): Boolean {
        return mediaPermissions().all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** True jika Android 11+ sehingga "Akses Semua File" bisa diminta lewat pengaturan. */
    fun canRequestAllFiles(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun hasAllFiles(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            false
        } else {
            // Android 9 ke bawah: izin tulis storage sudah memberi akses File API penuh
            hasMediaPermission(context)
        }
    }

    fun currentMode(context: Context): AccessMode {
        if (!hasMediaPermission(context)) return AccessMode.NONE
        return if (hasAllFiles(context)) AccessMode.FULL else AccessMode.MEDIA
    }

    fun appSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + context.packageName)
        )
    }

    fun allFilesIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + context.packageName)
            )
        } else {
            appSettingsIntent(context)
        }
    }
}
