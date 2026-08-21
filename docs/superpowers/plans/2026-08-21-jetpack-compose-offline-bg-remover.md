# Jetpack Compose Multi-Engine Offline BG Remover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the RMBG Android application into a modern Jetpack Compose application with on-device offline background removal (Google MediaPipe and ONNX Runtime U2NetP) and a Cloud FastAPI engine.

**Architecture:** Strategy Pattern (`BackgroundRemover`) powering a single `MainViewModel` observing UI events in Jetpack Compose (Material 3). Pre-processing and post-processing run off-thread via coroutines (`Dispatchers.Default`/`Dispatchers.IO`).

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, AndroidX Lifecycle / ViewModel Compose, Google MediaPipe Tasks Vision, ONNX Runtime Android, OkHttp 4, Coil Compose.

## Global Constraints
- Pure Kotlin with modern coroutines.
- Scoped Storage compliant (`MediaStore` on Android 10+ / Q+).
- Support full offline capability for MediaPipe and ONNX engines.
- Compose compiler and Gradle dependencies must build cleanly in CI.

---

### Task 1: Gradle & Compose Dependencies Configuration

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: Standard Android Gradle Plugin & Kotlin Android Plugin.
- Produces: Compose, MediaPipe, ONNX Runtime, Coil, and OkHttp dependencies available across the app.

- [ ] **Step 1: Update `app/build.gradle.kts` for Compose and AI Engines**

Configure `buildFeatures { compose = true }`, `composeOptions`, and add libraries:
```kotlin
buildFeatures {
    compose = true
}

composeOptions {
    kotlinCompilerExtensionVersion = "1.5.8"
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Offline AI Engines
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.1")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 2: Commit Gradle build configuration**

```bash
git add app/build.gradle.kts
git commit -m "build: add Jetpack Compose, MediaPipe, and ONNX Runtime dependencies"
```

---

### Task 2: Domain Strategy & Engine Models

**Files:**
- Create: `app/src/main/java/com/rmbg/app/domain/RemoverEngine.kt`
- Create: `app/src/main/java/com/rmbg/app/domain/BackgroundRemover.kt`
- Create: `app/src/test/java/com/rmbg/app/domain/RemoverEngineTest.kt`

**Interfaces:**
- Produces: `enum class RemoverEngine`, `interface BackgroundRemover` with `suspend fun removeBackground(bitmap: Bitmap): Result<Bitmap>`.

- [ ] **Step 1: Write unit tests for domain engine enum**

```kotlin
package com.rmbg.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoverEngineTest {
    @Test
    fun testEngineProperties() {
        assertTrue(RemoverEngine.MEDIAPIPE.isOffline)
        assertTrue(RemoverEngine.ONNX_U2NET.isOffline)
        assertEquals(false, RemoverEngine.CLOUD_API.isOffline)
    }
}
```

- [ ] **Step 2: Create domain models and interface**

`RemoverEngine.kt`:
```kotlin
package com.rmbg.app.domain

enum class RemoverEngine(
    val id: String,
    val title: String,
    val isOffline: Boolean,
    val description: String
) {
    MEDIAPIPE(
        id = "mediapipe",
        title = "Fast Portrait",
        isOffline = true,
        description = "MediaPipe AI (~50ms) - Best for people & selfies"
    ),
    ONNX_U2NET(
        id = "onnx_u2net",
        title = "General Objects",
        isOffline = true,
        description = "ONNX U2NetP (~500ms) - Best for products & objects"
    ),
    CLOUD_API(
        id = "cloud_api",
        title = "Cloud Server",
        isOffline = false,
        description = "FastAPI Backend - Remote processing"
    )
}
```

`BackgroundRemover.kt`:
```kotlin
package com.rmbg.app.domain

import android.graphics.Bitmap

interface BackgroundRemover {
    suspend fun removeBackground(bitmap: Bitmap): Result<Bitmap>
}
```

- [ ] **Step 3: Commit domain layer**

```bash
git add app/src/main/java/com/rmbg/app/domain/ app/src/test/java/com/rmbg/app/domain/
git commit -m "feat(domain): define RemoverEngine and BackgroundRemover strategy"
```

---

### Task 3: Engine Implementations (MediaPipe, ONNX Runtime, Cloud API)

**Files:**
- Create: `app/src/main/java/com/rmbg/app/engine/MediaPipeRemover.kt`
- Create: `app/src/main/java/com/rmbg/app/engine/OnnxU2NetRemover.kt`
- Create: `app/src/main/java/com/rmbg/app/engine/CloudApiRemover.kt`
- Create: `app/src/main/java/com/rmbg/app/engine/BitmapUtils.kt`
- Create: `app/src/main/assets/models/.gitkeep`

**Interfaces:**
- Consumes: `BackgroundRemover` interface, `android.content.Context`.
- Produces: `MediaPipeRemover`, `OnnxU2NetRemover`, `CloudApiRemover`.

- [ ] **Step 1: Create `BitmapUtils.kt` for tensor & alpha blending operations**

```kotlin
package com.rmbg.app.engine

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.nio.FloatBuffer

object BitmapUtils {
    fun applyAlphaMask(source: Bitmap, mask: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val scaledMask = if (mask.width != width || mask.height != height) {
            Bitmap.createScaledBitmap(mask, width, height, true)
        } else {
            mask
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(width * height)
        val maskPixels = IntArray(width * height)
        val resultPixels = IntArray(width * height)

        source.getPixels(srcPixels, 0, width, 0, 0, width, height)
        scaledMask.getPixels(maskPixels, 0, width, 0, 0, width, height)

        for (i in srcPixels.indices) {
            val color = srcPixels[i]
            val maskVal = Color.red(maskPixels[i]) // 0-255
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            resultPixels[i] = Color.argb(maskVal, r, g, b)
        }

        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }

    fun toPngByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}
```

- [ ] **Step 2: Implement `MediaPipeRemover.kt`**

```kotlin
package com.rmbg.app.engine

import android.content.Context
import android.graphics.Bitmap
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

            val confidenceMasks = result.confidenceMasks()
            if (confidenceMasks.isNullOrEmpty()) {
                segmenter.close()
                return@withContext Result.failure(Exception("No segmentation mask generated"))
            }

            val maskImage = confidenceMasks[0]
            val byteBuffer: ByteBuffer = ByteBufferExtractor.extract(maskImage)
            byteBuffer.rewind()

            val maskWidth = maskImage.width
            val maskHeight = maskImage.height
            val maskBitmap = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(maskWidth * maskHeight)

            for (i in 0 until (maskWidth * maskHeight)) {
                val confidence = if (byteBuffer.hasRemaining()) byteBuffer.float else 0f
                val alpha = (confidence.coerceIn(0f, 1f) * 255).toInt()
                pixels[i] = android.graphics.Color.argb(alpha, alpha, alpha, alpha)
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
```

- [ ] **Step 3: Implement `OnnxU2NetRemover.kt`**

```kotlin
package com.rmbg.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.rmbg.app.domain.BackgroundRemover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

class OnnxU2NetRemover(private val context: Context) : BackgroundRemover {

    override suspend fun removeBackground(bitmap: Bitmap): Result<Bitmap> = withContext(Dispatchers.Default) {
        try {
            val env = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open("models/u2netp.onnx").use { it.readBytes() }
            val session = env.createSession(modelBytes, OrtSession.SessionOptions())

            val targetSize = 320
            val resized = Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
            val floatBuffer = FloatBuffer.allocate(1 * 3 * targetSize * targetSize)

            val pixels = IntArray(targetSize * targetSize)
            resized.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)

            // Normalize RGB using ImageNet mean & std
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

            val maskBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val maskPixels = IntArray(targetSize * targetSize)

            for (y in 0 until targetSize) {
                for (x in 0 until targetSize) {
                    val raw = out[y][x]
                    val prob = 1.0f / (1.0f + Math.exp(-raw.toDouble())).toFloat()
                    val alpha = (prob.coerceIn(0f, 1f) * 255).toInt()
                    maskPixels[y * targetSize + x] = Color.argb(alpha, alpha, alpha, alpha)
                }
            }
            maskBitmap.setPixels(maskPixels, 0, targetSize, 0, 0, targetSize, targetSize)

            val output = BitmapUtils.applyAlphaMask(bitmap, maskBitmap)
            results.close()
            tensor.close()
            session.close()

            Result.success(output)
        } catch (e: Exception) {
            Result.failure(Exception("ONNX removal error: ${e.localizedMessage ?: "Model asset missing"}", e))
        }
    }
}
```

- [ ] **Step 4: Implement `CloudApiRemover.kt`**

```kotlin
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
```

- [ ] **Step 5: Commit engine implementations**

```bash
git add app/src/main/java/com/rmbg/app/engine/
git commit -m "feat(engine): implement MediaPipe, ONNX U2Net, and Cloud API removers"
```

---

### Task 4: MainViewModel & Storage Exporter

**Files:**
- Create: `app/src/main/java/com/rmbg/app/presentation/MainUiState.kt`
- Create: `app/src/main/java/com/rmbg/app/presentation/MainViewModel.kt`
- Create: `app/src/main/java/com/rmbg/app/data/ImageSaver.kt`

**Interfaces:**
- Consumes: `BackgroundRemover` strategies, `Application` context.
- Produces: `StateFlow<MainUiState>`, UI actions (`onImageSelected`, `onEngineChanged`, `onRemoveBackground`, `onSaveResult`, `onServerUrlChanged`).

- [ ] **Step 1: Create `ImageSaver.kt` for Scoped Storage saving**

```kotlin
package com.rmbg.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

object ImageSaver {
    suspend fun saveToGallery(context: Context, bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        try {
            val filename = "rmbg_${System.currentTimeMillis()}.png"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RMBG")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext Result.failure(Exception("Could not create MediaStore entry"))

            context.contentResolver.openOutputStream(uri)?.use { output: OutputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }

            Result.success("Saved to Pictures/RMBG")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

- [ ] **Step 2: Create `MainUiState.kt` and `MainViewModel.kt`**

`MainUiState.kt`:
```kotlin
package com.rmbg.app.presentation

import android.graphics.Bitmap
import com.rmbg.app.domain.RemoverEngine

data class MainUiState(
    val selectedBitmap: Bitmap? = null,
    val resultBitmap: Bitmap? = null,
    val selectedEngine: RemoverEngine = RemoverEngine.MEDIAPIPE,
    val isProcessing: Boolean = false,
    val statusMessage: String = "Select an image to get started",
    val serverUrl: String = "http://10.0.2.2:8000/remove-bg",
    val showSettingsDialog: Boolean = false,
    val executionTimeMs: Long? = null
)
```

`MainViewModel.kt`:
```kotlin
package com.rmbg.app.presentation

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rmbg.app.data.ImageSaver
import com.rmbg.app.domain.BackgroundRemover
import com.rmbg.app.domain.RemoverEngine
import com.rmbg.app.engine.CloudApiRemover
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

    private val prefs by lazy {
        application.getSharedPreferences("rmbg_prefs", Application.MODE_PRIVATE)
    }

    init {
        val savedUrl = prefs.getString("server_url", "http://10.0.2.2:8000/remove-bg") ?: "http://10.0.2.2:8000/remove-bg"
        _uiState.update { it.copy(serverUrl = savedUrl) }
    }

    private val mediaPipeRemover by lazy { MediaPipeRemover(getApplication()) }
    private val onnxRemover by lazy { OnnxU2NetRemover(getApplication()) }
    private val cloudRemover by lazy { CloudApiRemover({ _uiState.value.serverUrl }) }

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

    fun onServerUrlChanged(newUrl: String) {
        prefs.edit().putString("server_url", newUrl).apply()
        _uiState.update { it.copy(serverUrl = newUrl, showSettingsDialog = false) }
    }

    fun setSettingsDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = visible) }
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
                RemoverEngine.CLOUD_API -> cloudRemover
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
```

- [ ] **Step 3: Commit presentation layer**

```bash
git add app/src/main/java/com/rmbg/app/presentation/ app/src/main/java/com/rmbg/app/data/
git commit -m "feat(presentation): implement MainViewModel, MainUiState, and ImageSaver"
```

---

### Task 5: Jetpack Compose UI Screens & Components

**Files:**
- Create: `app/src/main/java/com/rmbg/app/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/rmbg/app/ui/theme/Color.kt`
- Create: `app/src/main/java/com/rmbg/app/ui/components/EngineSelector.kt`
- Create: `app/src/main/java/com/rmbg/app/ui/components/ImageComparisonCard.kt`
- Create: `app/src/main/java/com/rmbg/app/ui/components/ServerSettingsDialog.kt`
- Create: `app/src/main/java/com/rmbg/app/ui/MainScreen.kt`

**Interfaces:**
- Consumes: `MainViewModel`, `MainUiState`.
- Produces: `MainScreen()` Composable root.

- [ ] **Step 1: Create Theme and Color definitions**

`Color.kt`:
```kotlin
package com.rmbg.app.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
val CheckerboardLight = Color(0xFFF0F0F0)
val CheckerboardDark = Color(0xFFE0E0E0)
```

`Theme.kt`:
```kotlin
package com.rmbg.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun RMBGTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
```

- [ ] **Step 2: Create Compose UI Components**

`EngineSelector.kt`:
```kotlin
package com.rmbg.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rmbg.app.domain.RemoverEngine

@Composable
fun EngineSelector(
    selectedEngine: RemoverEngine,
    onEngineSelected: (RemoverEngine) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "AI Engine Mode",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RemoverEngine.values().forEach { engine ->
                val icon = if (engine.isOffline) "⚡" else "☁️"
                FilterChip(
                    selected = engine == selectedEngine,
                    onClick = { onEngineSelected(engine) },
                    label = { Text("$icon ${engine.title}") }
                )
            }
        }
        Text(
            text = selectedEngine.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
```

`ImageComparisonCard.kt`:
```kotlin
package com.rmbg.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun ImagePreviewBox(
    bitmap: Bitmap?,
    placeholderText: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = placeholderText,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = placeholderText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

`ServerSettingsDialog.kt`:
```kotlin
package com.rmbg.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun ServerSettingsDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var urlText by remember { mutableStateOf(initialUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cloud Server URL") },
        text = {
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text("API URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(urlText.trim()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

`MainScreen.kt`:
```kotlin
package com.rmbg.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rmbg.app.presentation.MainViewModel
import com.rmbg.app.ui.components.EngineSelector
import com.rmbg.app.ui.components.ImagePreviewBox
import com.rmbg.app.ui.components.ServerSettingsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { imageUri ->
            try {
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        viewModel.onImageSelected(bitmap)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RMBG - AI Remover") },
                actions = {
                    IconButton(onClick = { viewModel.setSettingsDialogVisible(true) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            EngineSelector(
                selectedEngine = state.selectedEngine,
                onEngineSelected = { viewModel.onEngineChanged(it) }
            )

            Text(
                text = "Original Image",
                style = MaterialTheme.typography.titleSmall
            )
            ImagePreviewBox(
                bitmap = state.selectedBitmap,
                placeholderText = "No image selected (Tap 'Select Image')"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { imagePicker.launch("image/*") },
                    enabled = !state.isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Select Image")
                }

                Button(
                    onClick = { viewModel.onRemoveBackground() },
                    enabled = state.selectedBitmap != null && !state.isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Remove BG")
                    }
                }
            }

            Text(
                text = state.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Text(
                text = "Result Preview",
                style = MaterialTheme.typography.titleSmall
            )
            ImagePreviewBox(
                bitmap = state.resultBitmap,
                placeholderText = "Processed result will appear here"
            )

            Button(
                onClick = {
                    viewModel.onSaveResult { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = state.resultBitmap != null && !state.isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save to Gallery")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (state.showSettingsDialog) {
            ServerSettingsDialog(
                initialUrl = state.serverUrl,
                onDismiss = { viewModel.setSettingsDialogVisible(false) },
                onSave = { viewModel.onServerUrlChanged(it) }
            )
        }
    }
}
```

- [ ] **Step 3: Commit Compose UI components**

```bash
git add app/src/main/java/com/rmbg/app/ui/
git commit -m "feat(ui): create Jetpack Compose screens, components, and Material 3 theme"
```

---

### Task 6: Connect MainActivity & Clean Up Legacy Layouts

**Files:**
- Modify: `app/src/main/java/com/rmbg/app/MainActivity.kt`
- Delete: `app/src/main/res/layout/activity_main.xml`
- Delete: `app/src/main/res/layout/content_main.xml`

**Interfaces:**
- Consumes: `MainScreen(viewModel)`, `RMBGTheme`.
- Produces: Working Jetpack Compose Activity.

- [ ] **Step 1: Update `MainActivity.kt` to use `setContent`**

```kotlin
package com.rmbg.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.rmbg.app.presentation.MainViewModel
import com.rmbg.app.ui.MainScreen
import com.rmbg.app.ui.theme.RMBGTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RMBGTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
```

- [ ] **Step 2: Remove legacy XML layout files and unused ViewBinding**

```bash
git rm app/src/main/res/layout/activity_main.xml app/src/main/res/layout/content_main.xml
```

- [ ] **Step 3: Commit MainActivity and layout cleanup**

```bash
git add app/src/main/java/com/rmbg/app/MainActivity.kt
git commit -m "refactor: migrate MainActivity to Jetpack Compose and clean legacy XML layouts"
```

---

### Task 7: Offline Model Bundling & CI Verification

**Files:**
- Create: `app/src/main/assets/models/selfie_segmenter.tflite`
- Create: `app/src/main/assets/models/u2netp.onnx`
- Test: Push to GitHub & verify CI build via `gh run watch`

- [ ] **Step 1: Place or generate model asset binaries into `app/src/main/assets/models/`**
- [ ] **Step 2: Commit and push via `git push origin master`**
- [ ] **Step 3: Monitor CI build with `gh run watch`**
