package com.rmbg.app.engine

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream

object BitmapUtils {
    /**
     * Applies a raw float probability mask (values 0.0f..1.0f) onto the source bitmap
     * using a threshold cutoff (sensitivity).
     * sensitivity: 0.1f (keep more edges/soft) to 0.9f (sharp cutoff). Default 0.5f.
     */
    fun applyMaskWithThreshold(
        source: Bitmap,
        rawMask: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        threshold: Float
    ): Bitmap {
        val maskBitmap = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
        val maskPixels = IntArray(maskWidth * maskHeight)

        // Threshold tuning with soft feathering window
        val feather = 0.08f
        val lowBound = (threshold - feather).coerceAtLeast(0.0f)
        val highBound = (threshold + feather).coerceAtMost(1.0f)
        val range = (highBound - lowBound).coerceAtLeast(0.001f)

        for (i in rawMask.indices) {
            val prob = rawMask[i]
            val alphaFloat = when {
                prob <= lowBound -> 0.0f
                prob >= highBound -> 1.0f
                else -> (prob - lowBound) / range
            }
            val alpha = (alphaFloat * 255).toInt().coerceIn(0, 255)
            maskPixels[i] = Color.argb(alpha, alpha, alpha, alpha)
        }
        maskBitmap.setPixels(maskPixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)

        return applyAlphaMask(source, maskBitmap)
    }

    fun applyAlphaMask(source: Bitmap, mask: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val scaledMask = if (mask.width != width || mask.height != height) {
            Bitmap.createScaledBitmap(mask, width, height, true)
        } else {
            mask
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(width * height)
        val maskPixels = IntArray(width * height)
        val resultPixels = IntArray(width * height)

        source.getPixels(srcPixels, 0, width, 0, 0, width, height)
        scaledMask.getPixels(maskPixels, 0, width, 0, 0, width, height)

        for (i in srcPixels.indices) {
            val color = srcPixels[i]
            val maskVal = Color.red(maskPixels[i])
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            resultPixels[i] = Color.argb(maskVal, r, g, b)
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

