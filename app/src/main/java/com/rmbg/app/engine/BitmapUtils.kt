package com.rmbg.app.engine

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream

object BitmapUtils {
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
