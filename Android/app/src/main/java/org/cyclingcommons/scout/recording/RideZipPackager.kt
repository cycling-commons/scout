package org.cyclingcommons.scout.recording

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Scout ride bundle: `ride.fit` + `scout.json` + `photos/` (docs/SCOUT-BUNDLE.md). */
object RideZipPackager {
    const val FIT_ENTRY = "ride.fit"
    const val MANIFEST_ENTRY = "scout.json"

    fun needsZip(fitFile: File): Boolean = RidePhotoStore.count(fitFile) > 0

    fun pack(fitFile: File, destZip: File): File {
        require(fitFile.isFile) { "FIT missing: ${fitFile.absolutePath}" }
        destZip.parentFile?.mkdirs()
        if (destZip.exists()) destZip.delete()

        val photos = RidePhotoStore.list(fitFile)
        val tags = bundleTags(photos)
        ZipOutputStream(destZip.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(FIT_ENTRY))
            fitFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(encodeScoutJson(tags).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            for (tag in tags) {
                for ((path, file) in tag.files) {
                    if (!file.isFile) continue
                    zip.putNextEntry(ZipEntry(path))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return destZip
    }

    fun zipNameFor(fitFile: File): String = RidePhotoStore.stem(fitFile) + ".zip"

    internal fun utcIso(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atOffset(ZoneOffset.UTC)
            .withNano(0)
            .format(ISO_UTC)

    internal fun compactUtc(iso: String): String =
        iso.replace("-", "").replace(":", "")

    internal fun bundleTags(photos: List<RidePhoto>): List<BundleTag> =
        photos
            .groupBy { utcIso(it.capturedAtMs) }
            .entries
            .sortedBy { it.key }
            .map { (at, group) ->
                val compact = compactUtc(at)
                val files =
                    group.mapIndexed { index, photo ->
                        "photos/$compact-${index + 1}.jpg" to photo.file
                    }
                val note =
                    group
                        .map { it.caption.trim() }
                        .firstOrNull { it.isNotEmpty() }
                        ?.take(RidePhotoStore.CAPTION_MAX)
                BundleTag(at = at, note = note, files = files)
            }

    internal fun encodeScoutJson(tags: List<BundleTag>): String {
        val tagsJson = JSONArray()
        for (tag in tags) {
            val obj = JSONObject()
            obj.put("at", tag.at)
            if (!tag.note.isNullOrEmpty()) obj.put("note", tag.note)
            if (tag.files.isNotEmpty()) {
                val paths = JSONArray()
                for ((path, _) in tag.files) paths.put(path)
                obj.put("photos", paths)
            }
            tagsJson.put(obj)
        }
        return JSONObject()
            .put("format", "scout-bundle")
            .put("version", 1)
            .put("fit", FIT_ENTRY)
            .put("tags", tagsJson)
            .toString()
    }

    internal data class BundleTag(
        val at: String,
        val note: String?,
        val files: List<Pair<String, File>>,
    )

    private val ISO_UTC: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
}
