package org.cyclingcommons.scout.recording

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.util.zip.ZipFile

class RidePhotoStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun saveListAndDeleteCompanion() {
        val fit = tmp.newFile("scout-20260918-214400.fit")
        fit.writeText("fit")
        val first = RidePhotoStore.addPreparedJpeg(
            fitFile = fit,
            jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte()),
            capturedAtMs = 1_726_680_000_000L,
            lat = 52.0016,
            lon = 4.0024,
            accuracyM = 4.2,
            caption = "closure sign",
        )
        assertEquals("p_001", first.id)
        assertEquals("p_001.jpg", first.fileName)
        assertTrue(first.file.isFile)

        val second = RidePhotoStore.addPreparedJpeg(
            fitFile = fit,
            jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01),
            capturedAtMs = 1_726_680_001_000L,
            lat = null,
            lon = null,
            accuracyM = null,
            caption = "  trimmed  ",
        )
        assertEquals("p_002", second.id)
        assertEquals("trimmed", second.caption)

        val listed = RidePhotoStore.list(fit)
        assertEquals(2, listed.size)
        assertEquals(52.0016, listed[0].lat)
        assertEquals("trimmed", listed[1].caption)
        val bundled = RideFiles.toRideFile(fit)
        assertEquals(2, bundled.photoCount)
        assertEquals("scout-20260918-214400.zip", bundled.name)

        assertTrue(RidePhotoStore.deleteCompanion(fit))
        assertEquals(0, RidePhotoStore.count(fit))
        assertTrue(fit.isFile)
        assertEquals("scout-20260918-214400.fit", RideFiles.toRideFile(fit).name)
    }

    @Test
    fun captionIsCappedAtBundleLimit() {
        val fit = tmp.newFile("scout-cap.fit")
        val photo = RidePhotoStore.addPreparedJpeg(
            fitFile = fit,
            jpegBytes = byteArrayOf(1),
            capturedAtMs = 1L,
            lat = null,
            lon = null,
            accuracyM = null,
            caption = "x".repeat(250),
        )
        assertEquals(200, RidePhotoStore.CAPTION_MAX)
        assertEquals(200, photo.caption.length)
    }

    @Test
    fun deleteRideRemovesFitAndPhotos() {
        val fit = tmp.newFile("scout-del.fit")
        RidePhotoStore.addPreparedJpeg(
            fitFile = fit,
            jpegBytes = byteArrayOf(1),
            capturedAtMs = 1L,
            lat = 1.0,
            lon = 2.0,
            accuracyM = 3.0,
            caption = "a",
        )
        val ride = RideFiles.toRideFile(fit)
        assertEquals(1, ride.photoCount)
        assertTrue(RideFiles.delete(ride))
        assertFalse(fit.exists())
        assertFalse(RidePhotoStore.companionDir(fit).exists())
    }

    @Test
    fun zipIsScoutBundleWithRideFitAndScoutJson() {
        val fit = tmp.newFile("scout-zip.fit")
        fit.writeText("FITBYTES")
        val atMs = Instant.parse("2026-09-12T14:46:33Z").toEpochMilli()
        RidePhotoStore.addPreparedJpeg(
            fitFile = fit,
            jpegBytes = byteArrayOf(9, 8, 7),
            capturedAtMs = atMs,
            lat = 51.0,
            lon = 4.0,
            accuracyM = 5.0,
            caption = "view",
        )
        val zipFile = File(tmp.root, "out.zip")
        RideZipPackager.pack(fit, zipFile)
        assertTrue(RideZipPackager.needsZip(fit))

        ZipFile(zipFile).use { zip ->
            val names = zip.entries().toList().map { it.name }.toSet()
            assertEquals(
                setOf(
                    "ride.fit",
                    "scout.json",
                    "photos/20260912T144633Z-1.jpg",
                ),
                names,
            )
            zip.getInputStream(zip.getEntry("ride.fit")).use {
                assertEquals("FITBYTES", it.readBytes().toString(Charsets.UTF_8))
            }
            val manifest = zip.getInputStream(zip.getEntry("scout.json")).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
            val json = JSONObject(manifest)
            assertEquals("scout-bundle", json.getString("format"))
            assertEquals(1, json.getInt("version"))
            assertEquals("ride.fit", json.getString("fit"))
            val tag = json.getJSONArray("tags").getJSONObject(0)
            assertEquals("2026-09-12T14:46:33Z", tag.getString("at"))
            assertEquals("view", tag.getString("note"))
            assertEquals("photos/20260912T144633Z-1.jpg", tag.getJSONArray("photos").getString(0))
            assertFalse(tag.has("n"))
        }
    }

    @Test
    fun twoPhotosInTheSameSecondShareOneTag() {
        val fit = tmp.newFile("scout-same-sec.fit")
        fit.writeText("x")
        val atMs = Instant.parse("2026-09-12T14:46:33Z").toEpochMilli()
        RidePhotoStore.addPreparedJpeg(
            fitFile = fit,
            jpegBytes = byteArrayOf(1),
            capturedAtMs = atMs,
            lat = null,
            lon = null,
            accuracyM = null,
            caption = "first",
        )
        RidePhotoStore.addPreparedJpeg(
            fitFile = fit,
            jpegBytes = byteArrayOf(2),
            capturedAtMs = atMs + 400,
            lat = null,
            lon = null,
            accuracyM = null,
            caption = "second",
        )
        val zipFile = File(tmp.root, "same.zip")
        RideZipPackager.pack(fit, zipFile)
        ZipFile(zipFile).use { zip ->
            val json = JSONObject(
                zip.getInputStream(zip.getEntry("scout.json")).use {
                    it.readBytes().toString(Charsets.UTF_8)
                },
            )
            val tags = json.getJSONArray("tags")
            assertEquals(1, tags.length())
            val photos = tags.getJSONObject(0).getJSONArray("photos")
            assertEquals(2, photos.length())
            assertEquals("photos/20260912T144633Z-1.jpg", photos.getString(0))
            assertEquals("photos/20260912T144633Z-2.jpg", photos.getString(1))
            assertEquals("first", tags.getJSONObject(0).getString("note"))
        }
    }

    @Test
    fun ridesWithoutPhotosDoNotNeedZip() {
        val fit = tmp.newFile("scout-plain.fit")
        fit.writeText("x")
        assertFalse(RideZipPackager.needsZip(fit))
    }

    @Test
    fun sampleSizeHalvesUntilMaxEdge() {
        assertEquals(1, RideImagePrep.sampleSize(1024, 768, 2048))
        assertEquals(2, RideImagePrep.sampleSize(4096, 3072, 2048))
        assertEquals(4, RideImagePrep.sampleSize(8192, 6144, 2048))
    }
}
