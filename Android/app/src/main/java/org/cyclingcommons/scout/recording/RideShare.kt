package org.cyclingcommons.scout.recording

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

enum class RideShareTarget { ATLAS, FILE }

data class PackedRide(
    val file: File,
    val mime: String,
)

object RideShare {
    fun packed(context: Context, fitFile: File): PackedRide =
        if (RideZipPackager.needsZip(fitFile)) {
            val zip = File(context.cacheDir, "shares/${RideZipPackager.zipNameFor(fitFile)}")
            PackedRide(RideZipPackager.pack(fitFile, zip), "application/zip")
        } else {
            PackedRide(fitFile, "application/octet-stream")
        }

    fun copyToDownloads(context: Context, packed: PackedRide): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                insertDownload(context, packed)
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                packed.file.copyTo(File(dir, packed.file.name), overwrite = true)
                true
            }
        }.getOrDefault(false)

    private fun insertDownload(context: Context, packed: PackedRide): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, packed.file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, packed.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { out ->
            packed.file.inputStream().use { it.copyTo(out) }
        } ?: return false
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )
        return true
    }
}
