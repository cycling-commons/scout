package org.cyclingcommons.scout.recording

import android.content.Context
import java.io.File

data class RideFile(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val photoCount: Int = 0,
)

object RideFiles {
    fun dir(context: Context): File =
        File(context.filesDir, "rides").also { it.mkdirs() }

    fun list(context: Context): List<RideFile> =
        dir(context)
            .listFiles { f -> f.isFile && f.name.endsWith(".fit", ignoreCase = true) }
            ?.map { toRideFile(it) }
            ?.sortedByDescending { it.modifiedMs }
            .orEmpty()

    fun toRideFile(file: File): RideFile {
        val photos = RidePhotoStore.count(file)
        return RideFile(
            file = file,
            name = if (photos > 0) RideZipPackager.zipNameFor(file) else file.name,
            sizeBytes = storedSize(file, photos),
            modifiedMs = file.lastModified(),
            photoCount = photos,
        )
    }

    fun displayName(fitFile: File): String = toRideFile(fitFile).name

    private fun storedSize(fitFile: File, photoCount: Int): Long {
        var total = fitFile.length()
        if (photoCount <= 0) return total
        RidePhotoStore.companionDir(fitFile).walkTopDown().forEach { child ->
            if (child.isFile) total += child.length()
        }
        return total
    }

    fun delete(ride: RideFile): Boolean {
        RidePhotoStore.deleteCompanion(ride.file)
        return try {
            !ride.file.exists() || ride.file.delete()
        } catch (_: Exception) {
            false
        }
    }

    fun deleteFitAndPhotos(fitFile: File): Boolean =
        delete(toRideFile(fitFile))
}
