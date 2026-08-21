package com.rmbg.app.domain

import android.graphics.Bitmap

data class SegmentationResult(
    val bitmap: Bitmap,
    val rawMask: FloatArray,
    val maskWidth: Int,
    val maskHeight: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SegmentationResult
        if (bitmap != other.bitmap) return false
        if (!rawMask.contentEquals(other.rawMask)) return false
        if (maskWidth != other.maskWidth) return false
        if (maskHeight != other.maskHeight) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bitmap.hashCode()
        result = 31 * result + rawMask.contentHashCode()
        result = 31 * result + maskWidth
        result = 31 * result + maskHeight
        return result
    }
}

interface BackgroundRemover {
    suspend fun removeBackground(bitmap: Bitmap, sensitivity: Float = 0.5f): Result<SegmentationResult>
}

