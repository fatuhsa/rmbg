package com.rmbg.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max

object BitmapUtils {

    /**
     * Safely decodes a bitmap from Uri with automatic downsampling to maxDimension.
     */
    fun decodeSampledBitmap(context: Context, uri: Uri, maxDimension: Int = 1280): Bitmap? {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            var inSampleSize = 1
            val maxOriginal = max(origWidth, origHeight)
            while (maxOriginal / inSampleSize > maxDimension) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            return context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Applies a raw float probability mask (values 0.0f..1.0f) onto the source bitmap
     * using a threshold cutoff (sensitivity).
     * Direct pixel manipulation without intermediate bitmap thrashing.
     */
    fun applyMaskWithThreshold(
        source: Bitmap,
        rawMask: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        threshold: Float
    ): Bitmap {
        val width = source.width
        val height = source.height

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(width * height)
        val resultPixels = IntArray(width * height)
        source.getPixels(srcPixels, 0, width, 0, 0, width, height)

        val feather = 0.06f
        val lowBound = (threshold - feather).coerceAtLeast(0.0f)
        val highBound = (threshold + feather).coerceAtMost(1.0f)
        val invRange = 1.0f / (highBound - lowBound).coerceAtLeast(0.001f)

        val xRatio = maskWidth.toFloat() / width
        val yRatio = maskHeight.toFloat() / height

        for (y in 0 until height) {
            val maskY = (y * yRatio).toInt().coerceIn(0, maskHeight - 1)
            val maskRowOffset = maskY * maskWidth
            val rowOffset = y * width

            for (x in 0 until width) {
                val maskX = (x * xRatio).toInt().coerceIn(0, maskWidth - 1)
                val prob = rawMask[maskRowOffset + maskX]

                val alphaFloat = when {
                    prob <= lowBound -> 0.0f
                    prob >= highBound -> 1.0f
                    else -> (prob - lowBound) * invRange
                }
                val alpha = (alphaFloat * 255).toInt().coerceIn(0, 255)

                val color = srcPixels[rowOffset + x]
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                resultPixels[rowOffset + x] = Color.argb(alpha, r, g, b)
            }
        }

        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }

    fun toPngByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}


