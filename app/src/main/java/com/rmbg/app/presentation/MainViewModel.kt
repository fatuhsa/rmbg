package com.rmbg.app.presentation

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rmbg.app.data.ImageSaver
import com.rmbg.app.domain.BackgroundRemover
import com.rmbg.app.domain.RemoverEngine
import com.rmbg.app.engine.MediaPipeRemover
import com.rmbg.app.engine.OnnxU2NetRemover
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
                statusMessage = "Image selected. Ready to process with ${it.selectedEngine.title}.",
                executionTimeMs = null
            )
        }
    }

    fun onEngineChanged(engine: RemoverEngine) {
        _uiState.update {
            it.copy(
                selectedEngine = engine,
                statusMessage = "Engine changed to ${engine.title}."
            )
        }
    }

    fun onRemoveBackground() {
        val bitmap = _uiState.value.selectedBitmap ?: return
        val engine = _uiState.value.selectedEngine

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

            var result: Result<Bitmap>
            val duration = measureTimeMillis {
                result = strategy.removeBackground(bitmap)
            }

            result.onSuccess { output ->
                _uiState.update {
                    it.copy(
                        resultBitmap = output,
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

