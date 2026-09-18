package org.cyclingcommons.scout.recording

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Decode any gallery/camera image into a stripped JPEG suitable for the ride sidecar. */
object RideImagePrep {
    const val MAX_EDGE_PX = 2048
    const val JPEG_QUALITY = 85

    fun compressToJpeg(context: Context, uri: Uri): ByteArray {
        if (uri.scheme == "file") {
            val path = uri.path ?: error("Could not open image")
            return compressFile(File(path))
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: error("Could not open image")
        return compressDecoded(context, uri, bounds.outWidth, bounds.outHeight)
    }

    fun compressFile(file: File): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) error("Could not decode image")
        val decode = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(width, height, MAX_EDGE_PX)
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, decode)
            ?: error("Could not decode image")
        return compressBitmap(bitmap)
    }

    private fun compressDecoded(context: Context, uri: Uri, width: Int, height: Int): ByteArray {
        if (width <= 0 || height <= 0) error("Could not decode image")
        val decode = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(width, height, MAX_EDGE_PX)
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decode)
        } ?: error("Could not decode image")
        return compressBitmap(bitmap)
    }

    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        try {
            val scaled = scaleToMax(bitmap, MAX_EDGE_PX)
            try {
                val out = ByteArrayOutputStream()
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    error("Could not compress image")
                }
                return out.toByteArray()
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    internal fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var longest = max(width, height)
        while (longest / 2 >= maxEdge) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleToMax(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / longest.toFloat()
        val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
