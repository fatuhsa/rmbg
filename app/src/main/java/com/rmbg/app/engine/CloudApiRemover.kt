package com.rmbg.app.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.rmbg.app.domain.BackgroundRemover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class CloudApiRemover(
    private val getApiUrl: () -> String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
) : BackgroundRemover {

    override suspend fun removeBackground(bitmap: Bitmap): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val pngBytes = BitmapUtils.toPngByteArray(bitmap)
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "image.png",
                    pngBytes.toRequestBody("image/png".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url(getApiUrl())
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: response.message
                    return@withContext Result.failure(Exception("Server error (${response.code}): $err"))
                }
                val bytes = response.body?.bytes() ?: return@withContext Result.failure(Exception("Empty server response"))
                val resultBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@withContext Result.failure(Exception("Failed to decode response image"))
                Result.success(resultBitmap)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cloud request failed: ${e.localizedMessage ?: "Network error"}", e))
        }
    }
}
