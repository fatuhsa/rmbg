package com.rmbg.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.rmbg.app.domain.BackgroundRemover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

class MediaPipeRemover(private val context: Context) : BackgroundRemover {

    override suspend fun removeBackground(bitmap: Bitmap): Result<Bitmap> = withContext(Dispatchers.Default) {
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
            val maskBitmap = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(maskWidth * maskHeight)

            for (i in 0 until (maskWidth * maskHeight)) {
                val confidence = if (byteBuffer.hasRemaining()) byteBuffer.float else 0f
                val alpha = (confidence.coerceIn(0f, 1f) * 255).toInt()
                pixels[i] = Color.argb(alpha, alpha, alpha, alpha)
            }
            maskBitmap.setPixels(pixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)

            val output = BitmapUtils.applyAlphaMask(bitmap, maskBitmap)
            segmenter.close()
            Result.success(output)
        } catch (e: Exception) {
            Result.failure(Exception("MediaPipe removal error: ${e.localizedMessage ?: "Model asset missing"}", e))
        }
    }
}
