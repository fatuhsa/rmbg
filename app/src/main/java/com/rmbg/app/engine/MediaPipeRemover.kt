package com.rmbg.app.engine

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.rmbg.app.domain.BackgroundRemover
import com.rmbg.app.domain.SegmentationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

class MediaPipeRemover(private val context: Context) : BackgroundRemover {

    @Volatile private var _segmenter: ImageSegmenter? = null

    private val segmenter: ImageSegmenter
        get() = _segmenter ?: synchronized(this) {
            _segmenter ?: createSegmenter().also { _segmenter = it }
        }

    private fun createSegmenter(): ImageSegmenter {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("models/selfie_segmenter.tflite")
            .build()

        val options = ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setOutputCategoryMask(false)
            .setOutputConfidenceMasks(true)
            .build()

        return ImageSegmenter.createFromOptions(context, options)
    }

    fun close() {
        _segmenter?.close()
        _segmenter = null
    }

    override suspend fun removeBackground(bitmap: Bitmap, sensitivity: Float): Result<SegmentationResult> = withContext(Dispatchers.Default) {
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = segmenter.segment(mpImage)

            val confidenceMasksOpt = result.confidenceMasks()
            if (!confidenceMasksOpt.isPresent || confidenceMasksOpt.get().isEmpty()) {
                return@withContext Result.failure(Exception("No segmentation mask generated"))
            }

            val maskImage = confidenceMasksOpt.get()[0]
            val byteBuffer: ByteBuffer = ByteBufferExtractor.extract(maskImage)
            byteBuffer.rewind()

            val maskWidth = maskImage.width
            val maskHeight = maskImage.height
            val totalPixels = maskWidth * maskHeight
            val rawMask = FloatArray(totalPixels)

            for (i in 0 until totalPixels) {
                val confidence = if (byteBuffer.hasRemaining()) byteBuffer.float else 0f
                rawMask[i] = confidence.coerceIn(0f, 1f)
            }

            val output = try {
                BitmapUtils.applyMaskWithThreshold(bitmap, rawMask, maskWidth, maskHeight, sensitivity)
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("MediaPipe removal error: ${e.localizedMessage ?: "Mask application failed"}", e))
            }

            Result.success(SegmentationResult(output, rawMask, maskWidth, maskHeight))
        } catch (e: Exception) {
            Result.failure(Exception("MediaPipe removal error: ${e.localizedMessage ?: "Model asset missing"}", e))
        }
    }
}

