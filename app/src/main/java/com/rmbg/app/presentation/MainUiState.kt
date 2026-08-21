package com.rmbg.app.presentation

import android.graphics.Bitmap
import com.rmbg.app.domain.RemoverEngine

data class MainUiState(
    val selectedBitmap: Bitmap? = null,
    val resultBitmap: Bitmap? = null,
    val selectedEngine: RemoverEngine = RemoverEngine.MEDIAPIPE,
    val isProcessing: Boolean = false,
    val statusMessage: String = "Select an image to get started",
    val sensitivity: Float = 0.5f,
    val rawMask: FloatArray? = null,
    val maskWidth: Int = 0,
    val maskHeight: Int = 0,
    val executionTimeMs: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MainUiState
        if (selectedBitmap != other.selectedBitmap) return false
        if (resultBitmap != other.resultBitmap) return false
        if (selectedEngine != other.selectedEngine) return false
        if (isProcessing != other.isProcessing) return false
        if (statusMessage != other.statusMessage) return false
        if (sensitivity != other.sensitivity) return false
        if (rawMask != null) {
            if (other.rawMask == null) return false
            if (!rawMask.contentEquals(other.rawMask)) return false
        } else if (other.rawMask != null) return false
        if (maskWidth != other.maskWidth) return false
        if (maskHeight != other.maskHeight) return false
        if (executionTimeMs != other.executionTimeMs) return false
        return true
    }

    override fun hashCode(): Int {
        var result = selectedBitmap?.hashCode() ?: 0
        result = 31 * result + (resultBitmap?.hashCode() ?: 0)
        result = 31 * result + selectedEngine.hashCode()
        result = 31 * result + isProcessing.hashCode()
        result = 31 * result + statusMessage.hashCode()
        result = 31 * result + sensitivity.hashCode()
        result = 31 * result + (rawMask?.contentHashCode() ?: 0)
        result = 31 * result + maskWidth
        result = 31 * result + maskHeight
        result = 31 * result + (executionTimeMs?.hashCode() ?: 0)
        return result
    }
}


