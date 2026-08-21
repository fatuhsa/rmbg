package com.rmbg.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.rmbg.app.domain.BackgroundRemover
import com.rmbg.app.domain.SegmentationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

class OnnxU2NetRemover(private val context: Context) : BackgroundRemover {

    override suspend fun removeBackground(bitmap: Bitmap, sensitivity: Float): Result<SegmentationResult> = withContext(Dispatchers.Default) {
        try {
            val env = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open("models/u2netp.onnx").use { it.readBytes() }
            val session = env.createSession(modelBytes, OrtSession.SessionOptions())

            val targetSize = 320
            val resized = Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
            val floatBuffer = FloatBuffer.allocate(1 * 3 * targetSize * targetSize)

            val pixels = IntArray(targetSize * targetSize)
            resized.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)

            // ImageNet normalization
            val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
            val std = floatArrayOf(0.229f, 0.224f, 0.225f)

            for (c in 0..2) {
                for (i in pixels.indices) {
                    val p = pixels[i]
                    val channelVal = when (c) {
                        0 -> Color.red(p) / 255.0f
                        1 -> Color.green(p) / 255.0f
                        else -> Color.blue(p) / 255.0f
                    }
                    floatBuffer.put((channelVal - mean[c]) / std[c])
                }
            }
            floatBuffer.rewind()

            val shape = longArrayOf(1, 3, targetSize.toLong(), targetSize.toLong())
            val tensor = OnnxTensor.createTensor(env, floatBuffer, shape)
            val inputName = session.inputNames.iterator().next()

            val results = session.run(mapOf(inputName to tensor))
            @Suppress("UNCHECKED_CAST")
            val outputTensor = results[0].value as Array<Array<Array<FloatArray>>>
            val out = outputTensor[0][0]

            val rawMask = FloatArray(targetSize * targetSize)
            for (y in 0 until targetSize) {
                for (x in 0 until targetSize) {
                    val raw = out[y][x]
                    val prob = 1.0f / (1.0f + Math.exp(-raw.toDouble())).toFloat()
                    rawMask[y * targetSize + x] = prob.coerceIn(0f, 1f)
                }
            }

            val output = BitmapUtils.applyMaskWithThreshold(bitmap, rawMask, targetSize, targetSize, sensitivity)
            results.close()
            tensor.close()
            session.close()

            Result.success(SegmentationResult(output, rawMask, targetSize, targetSize))
        } catch (e: Exception) {
            Result.failure(Exception("ONNX removal error: ${e.localizedMessage ?: "Model asset missing"}", e))
        }
    }
}

