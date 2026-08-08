package org.cyclingcommons.scout.fit

/** FIT CRC-16 — same table as tools/make-test-fit.mjs / fit-viewer. */
object FitCrc {
    private val TABLE = intArrayOf(
        0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
    )

    /** [seed] continues a CRC across chunks; 0 starts a fresh one. */
    fun crc16(bytes: ByteArray, start: Int = 0, end: Int = bytes.size, seed: Int = 0): Int {
        var crc = seed and 0xFFFF
        for (i in start until end) {
            val b = bytes[i].toInt() and 0xFF
            var t = TABLE[crc and 0xF]
            crc = ((crc ushr 4) and 0x0FFF) xor t xor TABLE[b and 0xF]
            t = TABLE[crc and 0xF]
            crc = ((crc ushr 4) and 0x0FFF) xor t xor TABLE[(b ushr 4) and 0xF]
        }
        return crc and 0xFFFF
    }
}
