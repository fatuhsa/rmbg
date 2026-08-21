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

    override suspend fun removeBackground(bitmap: Bitmap, sensitivity: Float): Result<SegmentationResult> = withContext(Dispatchers.Default) {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("models/selfie_segmenter.tflite")
                .build()

            val options = ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setOutputCategoryMask(false)
                .setOutputConfidenceMasks(true)
                .build()

            val segmenter = ImageSegmenter.createFromOptions(context, options)
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = segmenter.segment(mpImage)

            val confidenceMasksOpt = result.confidenceMasks()
            if (!confidenceMasksOpt.isPresent || confidenceMasksOpt.get().isEmpty()) {
                segmenter.close()
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

            val output = BitmapUtils.applyMaskWithThreshold(bitmap, rawMask, maskWidth, maskHeight, sensitivity)
            segmenter.close()
            Result.success(SegmentationResult(output, rawMask, maskWidth, maskHeight))
        } catch (e: Exception) {
            Result.failure(Exception("MediaPipe removal error: ${e.localizedMessage ?: "Model asset missing"}", e))
        }
    }
}

