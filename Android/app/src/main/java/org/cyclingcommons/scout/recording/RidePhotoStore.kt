package org.cyclingcommons.scout.recording

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One in-ride photo, stored next to the FIT (not inside it). Field names are the
 * future authenticated-upload payload — keep them stable.
 */
data class RidePhoto(
    val id: String,
    val fileName: String,
    val file: File,
    val capturedAtMs: Long,
    val lat: Double?,
    val lon: Double?,
    val accuracyM: Double?,
    val caption: String,
)

/**
 * On-device sidecar: `files/rides/<stem>/photos.json` + JPEGs.
 * Share export is the Scout ride bundle (`scout.json`) in [RideZipPackager].
 */
object RidePhotoStore {
    const val MANIFEST_NAME = "photos.json"
    const val CAPTION_MAX = 200

    fun companionDir(fitFile: File): File =
        File(requireNotNull(fitFile.parentFile), stem(fitFile))

    fun stem(fitFile: File): String =
        fitFile.name
            .removeSuffix(".fit")
            .removeSuffix(".FIT")

    fun count(fitFile: File): Int = list(fitFile).size

    fun list(fitFile: File): List<RidePhoto> {
        val dir = companionDir(fitFile)
        val manifest = File(dir, MANIFEST_NAME)
        if (!manifest.isFile) return emptyList()
        return runCatching { parseManifest(manifest.readText(), dir) }
            .getOrDefault(emptyList())
            .filter { it.file.isFile }
    }

    fun addPreparedJpeg(
        fitFile: File,
        jpegBytes: ByteArray,
        capturedAtMs: Long,
        lat: Double?,
        lon: Double?,
        accuracyM: Double?,
        caption: String,
    ): RidePhoto {
        val dir = companionDir(fitFile)
        dir.mkdirs()
        val existing = list(fitFile)
        val next = nextId(existing)
        val fileName = "$next.jpg"
        val jpeg = File(dir, fileName)
        jpeg.writeBytes(jpegBytes)
        val photo = RidePhoto(
            id = next,
            fileName = fileName,
            file = jpeg,
            capturedAtMs = capturedAtMs,
            lat = lat,
            lon = lon,
            accuracyM = accuracyM,
            caption = caption.trim().take(CAPTION_MAX),
        )
        writeManifest(dir, existing + photo)
        return photo
    }

    fun deleteCompanion(fitFile: File): Boolean {
        val dir = companionDir(fitFile)
        if (!dir.exists()) return true
        dir.walkBottomUp().forEach { child ->
            if (child != dir) child.delete()
        }
        return dir.delete() || !dir.exists()
    }

    internal fun encodeManifest(photos: List<RidePhoto>): String {
        val array = JSONArray()
        for (photo in photos) {
            val obj = JSONObject()
            obj.put("id", photo.id)
            obj.put("file", photo.fileName)
            obj.put("capturedAtMs", photo.capturedAtMs)
            if (photo.lat != null) obj.put("lat", photo.lat)
            if (photo.lon != null) obj.put("lon", photo.lon)
            if (photo.accuracyM != null) obj.put("accuracyM", photo.accuracyM)
            obj.put("caption", photo.caption)
            array.put(obj)
        }
        return JSONObject().put("photos", array).toString()
    }

    internal fun parseManifest(json: String, dir: File): List<RidePhoto> {
        val root = JSONObject(json)
        val array = root.optJSONArray("photos") ?: return emptyList()
        val photos = ArrayList<RidePhoto>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            if (id.isBlank()) continue
            val fileName = obj.optString("file").ifBlank { "$id.jpg" }
            photos += RidePhoto(
                id = id,
                fileName = fileName,
                file = File(dir, fileName),
                capturedAtMs = obj.optLong("capturedAtMs"),
                lat = obj.optionalDouble("lat"),
                lon = obj.optionalDouble("lon"),
                accuracyM = obj.optionalDouble("accuracyM"),
                caption = obj.optString("caption"),
            )
        }
        return photos
    }

    private fun writeManifest(dir: File, photos: List<RidePhoto>) {
        File(dir, MANIFEST_NAME).writeText(encodeManifest(photos))
    }

    private fun nextId(existing: List<RidePhoto>): String {
        val max = existing.mapNotNull { photo ->
            photo.id.removePrefix("p_").toIntOrNull()
        }.maxOrNull() ?: 0
        return "p_%03d".format(max + 1)
    }

    private fun JSONObject.optionalDouble(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeUnless { it.isNaN() } else null
}
