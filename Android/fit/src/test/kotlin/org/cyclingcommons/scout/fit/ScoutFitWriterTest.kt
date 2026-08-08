package org.cyclingcommons.scout.fit

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Mirrors tools/make-test-fit.mjs SCENARIO so the Node parser can validate
 * Kotlin output the same way as the reference encoder.
 */
class ScoutFitWriterTest {

    @Test
    fun encodesScenarioMatchingReferenceStructure() {
        val samples = buildScenario()
        val bytes = ScoutFitWriter.encode(samples)

        assertTrue("header .FIT", bytes[8] == '.'.code.toByte())
        assertTrue(bytes[9] == 'F'.code.toByte())
        assertTrue(bytes[10] == 'I'.code.toByte())
        assertTrue(bytes[11] == 'T'.code.toByte())
        assertEquals(12, bytes[0].toInt() and 0xFF)

        val bodyLen =
            (bytes[4].toInt() and 0xFF) or
                ((bytes[5].toInt() and 0xFF) shl 8) or
                ((bytes[6].toInt() and 0xFF) shl 16) or
                ((bytes[7].toInt() and 0xFF) shl 24)
        assertEquals(bytes.size - 14, bodyLen)

        val expectedCrc = FitCrc.crc16(bytes, 0, bytes.size - 2)
        val fileCrc =
            (bytes[bytes.size - 2].toInt() and 0xFF) or
                ((bytes[bytes.size - 1].toInt() and 0xFF) shl 8)
        assertEquals(expectedCrc, fileCrc)

        // Spot-check: first data record local mesg 3 after defs — count developer field names.
        val asString = String(bytes, Charsets.ISO_8859_1)
        assertTrue(asString.contains("poi_type"))
        assertTrue(asString.contains("radar_speed"))
        assertEquals(60, samples.size)
        assertEquals(1, samples[5].poiType) // DANGER
        assertEquals(255, samples[0].radarCount)
        assertEquals(1, samples[6].radarCount)
    }

    @Test
    fun flushWritesReadableFile() {
        val tmp = File.createTempFile("scout-p2-", ".fit")
        try {
            val writer = ScoutFitWriter(tmp)
            writer.append(
                ScoutSample(
                    timestampFit = 1_000_000_000L,
                    latSemi = ScoutSample.degreesToSemi(52.0),
                    lonSemi = ScoutSample.degreesToSemi(4.0),
                    speedMmPerS = 6000,
                    poiType = 1,
                    poiDetail = 0,
                ),
            )
            writer.finish()
            assertTrue(tmp.length() > 40)
            val bytes = tmp.readBytes()
            val crc = FitCrc.crc16(bytes, 0, bytes.size - 2)
            val fileCrc =
                (bytes[bytes.size - 2].toInt() and 0xFF) or
                    ((bytes[bytes.size - 1].toInt() and 0xFF) shl 8)
            assertEquals(crc, fileCrc)
        } finally {
            tmp.delete()
        }
    }

    /** Streamed appends + mid-ride flushes must land the same bytes as a one-shot encode. */
    @Test
    fun streamedFlushesMatchOneShotEncode() {
        val samples = buildScenario()
        val tmp = File.createTempFile("scout-stream-", ".fit")
        try {
            val writer = ScoutFitWriter(tmp)
            samples.forEachIndexed { i, sample ->
                writer.append(sample)
                if (i % 7 == 0) writer.flush()
            }
            writer.finish()
            assertEquals(samples.size, writer.recordCount)
            assertArrayEquals(ScoutFitWriter.encode(samples), tmp.readBytes())
        } finally {
            tmp.delete()
        }
    }

    /** Resume after process kill appends to an existing flushed partial file. */
    @Test
    fun resumeAppendContinuesPartialFile() {
        val samples = buildScenario()
        val tmp = File.createTempFile("scout-resume-", ".fit")
        try {
            val partial = samples.take(20)
            val writer = ScoutFitWriter(tmp)
            partial.forEach(writer::append)
            writer.flush()
            assertEquals(20, writer.recordCount)

            val resumed = ScoutFitWriter(tmp)
            resumed.resumeAppend(20)
            samples.drop(20).forEach(resumed::append)
            resumed.finish()
            assertEquals(samples.size, resumed.recordCount)
            assertArrayEquals(ScoutFitWriter.encode(samples), tmp.readBytes())
        } finally {
            tmp.delete()
        }
    }

    /** A flush mid-ride leaves a complete file, so a crash keeps everything logged so far. */
    @Test
    fun midRideFlushLeavesValidFile() {
        val samples = buildScenario()
        val tmp = File.createTempFile("scout-partial-", ".fit")
        try {
            val writer = ScoutFitWriter(tmp)
            samples.take(20).forEach(writer::append)
            writer.flush()

            val bytes = tmp.readBytes()
            assertArrayEquals(ScoutFitWriter.encode(samples.take(20)), bytes)
            val expectedCrc = FitCrc.crc16(bytes, 0, bytes.size - 2)
            val fileCrc =
                (bytes[bytes.size - 2].toInt() and 0xFF) or
                    ((bytes[bytes.size - 1].toInt() and 0xFF) shl 8)
            assertEquals(expectedCrc, fileCrc)

            samples.drop(20).forEach(writer::append)
            writer.finish()
            assertArrayEquals(ScoutFitWriter.encode(samples), tmp.readBytes())
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun writesScenarioForViewerValidation() {
        val out = File("build/scout-scenario.fit")
        out.parentFile?.mkdirs()
        out.writeBytes(ScoutFitWriter.encode(buildScenario()))
        assertTrue(out.length() > 100)
    }

    /** Same scenario through the writer the app actually uses, for the Node validator. */
    @Test
    fun writesStreamedScenarioForViewerValidation() {
        val out = File("build/scout-scenario-streamed.fit")
        out.parentFile?.mkdirs()
        val writer = ScoutFitWriter(out)
        buildScenario().forEachIndexed { i, sample ->
            writer.append(sample)
            if (i % 11 == 0) writer.flush()
        }
        writer.finish()
        assertTrue(out.length() > 100)
    }

    companion object {
        private val DEG = 2.0.pow(31) / 180.0
        private const val NA = 255

        fun buildScenario(): List<ScoutSample> {
            val rec = MutableList(60) { i ->
                ScoutSample(
                    timestampFit = 1_000_000_000L + i,
                    latSemi = ((52.0 + i * 0.0004) * DEG).roundToLong().toInt(),
                    lonSemi = ((4.0 + i * 0.0006) * DEG).roundToLong().toInt(),
                    speedMmPerS = 6000,
                    poiType = 0,
                    poiDetail = 0,
                    radarCount = NA,
                    radarNear = NA,
                    radarSpeed = NA,
                )
            }
            fun tag(i: Int, type: Int, detail: Int) {
                rec[i] = rec[i].copy(poiType = type, poiDetail = detail)
            }
            fun radar(i: Int, c: Int, near: Int, sp: Int) {
                rec[i] = rec[i].copy(radarCount = c, radarNear = near, radarSpeed = sp)
            }
            tag(5, 1, 0)
            tag(10, 2, 0)
            tag(13, 3, 0)
            tag(15, 7, 0)
            tag(17, 8, 0)
            tag(19, 4, 0)
            tag(22, 5, 1)
            tag(30, 5, 2)
            tag(38, 5, 3)
            tag(46, 5, 4)
            tag(54, 5, 5)
            tag(24, 6, 5)
            tag(27, 6, 6)
            tag(33, 6, 9)
            tag(42, 6, 7)
            tag(57, 1, 0)
            tag(58, 1, 0)
            radar(6, 1, 40, 28); radar(7, 1, 25, 30); radar(8, 0, NA, NA)
            radar(35, 1, 50, 45); radar(36, 1, 30, 48); radar(37, 0, NA, NA)
            return rec
        }
    }
}
