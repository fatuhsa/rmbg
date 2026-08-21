package com.rmbg.app.presentation

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rmbg.app.data.ImageSaver
import com.rmbg.app.domain.BackgroundRemover
import com.rmbg.app.domain.RemoverEngine
import com.rmbg.app.domain.SegmentationResult
import com.rmbg.app.engine.BitmapUtils
import com.rmbg.app.engine.MediaPipeRemover
import com.rmbg.app.engine.OnnxU2NetRemover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val mediaPipeRemover by lazy { MediaPipeRemover(getApplication()) }
    private val onnxRemover by lazy { OnnxU2NetRemover(getApplication()) }

    fun onImageSelected(bitmap: Bitmap) {
        _uiState.update {
            it.copy(
                selectedBitmap = bitmap,
                resultBitmap = null,
                rawMask = null,
                statusMessage = "Image selected. Tap 'Remove BG' to process.",
                executionTimeMs = null
            )
        }
    }

    fun onEngineChanged(engine: RemoverEngine) {
        _uiState.update {
            it.copy(
                selectedEngine = engine
            )
        }
    }

    private var sensitivityJob: kotlinx.coroutines.Job? = null

    fun onSensitivityChanged(newSensitivity: Float) {
        _uiState.update { it.copy(sensitivity = newSensitivity) }

        val currentState = _uiState.value
        val source = currentState.selectedBitmap
        val rawMask = currentState.rawMask

        if (source != null && rawMask != null) {
            sensitivityJob?.cancel()
            sensitivityJob = viewModelScope.launch {
                try {
                    val updatedBitmap = withContext(Dispatchers.Default) {
                        BitmapUtils.applyMaskWithThreshold(
                            source = source,
                            rawMask = rawMask,
                            maskWidth = currentState.maskWidth,
                            maskHeight = currentState.maskHeight,
                            threshold = newSensitivity
                        )
                    }
                    _uiState.update { it.copy(resultBitmap = updatedBitmap) }
                } catch (e: Exception) {
                    // Ignore cancellation or memory warnings gracefully
                }
            }
        }
    }

    fun onRemoveBackground() {
        val bitmap = _uiState.value.selectedBitmap ?: return
        val engine = _uiState.value.selectedEngine
        val sensitivity = _uiState.value.sensitivity

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Removing background using ${engine.title}..."
                )
            }

            val strategy: BackgroundRemover = when (engine) {
                RemoverEngine.MEDIAPIPE -> mediaPipeRemover
                RemoverEngine.ONNX_U2NET -> onnxRemover
            }

            var result: Result<SegmentationResult>
            val duration = measureTimeMillis {
                result = strategy.removeBackground(bitmap, sensitivity)
            }

            result.onSuccess { segResult ->
                _uiState.update {
                    it.copy(
                        resultBitmap = segResult.bitmap,
                        rawMask = segResult.rawMask,
                        maskWidth = segResult.maskWidth,
                        maskHeight = segResult.maskHeight,
                        isProcessing = false,
                        statusMessage = "Done in ${duration}ms using ${engine.title}!",
                        executionTimeMs = duration
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = "Error: ${error.localizedMessage ?: "Processing failed"}"
                    )
                }
            }
        }
    }

    fun onSaveResult(onComplete: (Boolean, String) -> Unit) {
        val resultBitmap = _uiState.value.resultBitmap ?: return
        viewModelScope.launch {
            ImageSaver.saveToGallery(getApplication(), resultBitmap)
                .onSuccess { msg ->
                    _uiState.update { it.copy(statusMessage = msg) }
                    onComplete(true, msg)
                }
                .onFailure { err ->
                    val msg = "Failed to save: ${err.localizedMessage}"
                    _uiState.update { it.copy(statusMessage = msg) }
                    onComplete(false, msg)
                }
        }
    }
}


