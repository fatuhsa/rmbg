package com.rmbg.app.presentation

import android.graphics.Bitmap
import com.rmbg.app.domain.RemoverEngine

data class MainUiState(
    val selectedBitmap: Bitmap? = null,
    val resultBitmap: Bitmap? = null,
    val selectedEngine: RemoverEngine = RemoverEngine.MEDIAPIPE,
    val isProcessing: Boolean = false,
    val statusMessage: String = "Select an image to get started",
    val executionTimeMs: Long? = null
)

