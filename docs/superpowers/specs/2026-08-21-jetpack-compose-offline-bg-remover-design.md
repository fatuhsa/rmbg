# Spec: Jetpack Compose Multi-Engine Background Remover (Offline & Cloud Hybrid)

## 1. Overview
Transform the RMBG Android application into a modern Jetpack Compose application featuring on-device offline background removal (Google MediaPipe and ONNX Runtime U2NetP) as well as an optional Cloud FastAPI backend engine.

## 2. Requirements & Goals
1. **100% Offline Capability**: Ability to run background removal fully on-device without internet or server connection.
2. **Multi-Engine Support**:
   - **Google MediaPipe Image Segmenter**: Optimized for selfie/portrait/person segmentation, ultra-fast (~30-100ms) with bundled `selfie_segmenter.tflite`.
   - **ONNX Runtime (U2NetP)**: General-purpose object segmentation (e-commerce, products, objects, humans, animals) with bundled `u2netp.onnx`.
   - **Cloud API**: Existing FastAPI backend via OkHttp for remote processing.
3. **Jetpack Compose UI**: Modern, responsive, Material 3 Jetpack Compose interface replacing legacy XML layouts.
4. **Gallery Export**: Seamless saving of transparent PNG results to device gallery via `MediaStore` (Scoped Storage).
5. **CI Compatibility**: Ensure Gradle dependencies, compose compiler configuration, and CI workflows build and pass cleanly.

## 3. Architecture & Engine Strategy

### 3.1 Domain Model & Strategy Interface
```kotlin
enum class RemoverEngine(val displayName: String, val isOffline: Boolean, val description: String) {
    MEDIAPIPE("Fast Portrait", true, "MediaPipe AI (~50ms) - Best for people & portraits"),
    ONNX_U2NET("General Object", true, "ONNX U2NetP AI (~500ms) - Best for products & objects"),
    CLOUD_API("Cloud Server", false, "FastAPI Server - Remote processing")
}

interface BackgroundRemover {
    suspend fun removeBackground(bitmap: Bitmap): Result<Bitmap>
}
```

### 3.2 Strategy Implementations
- `MediaPipeRemover`:
  - Uses `com.google.mediapipe:tasks-vision:0.10.14`
  - Loads `models/selfie_segmenter.tflite` from `assets/`
  - Performs on-device image segmentation using `ImageSegmenter`
  - Extracts confidence mask and blends alpha channel with input bitmap
- `OnnxU2NetRemover`:
  - Uses `com.microsoft.onnxruntime:onnxruntime-android:1.17.1`
  - Loads `models/u2netp.onnx` from `assets/`
  - Preprocesses Bitmap: resize to 320x320, normalize RGB channels to FloatBuffer `(input - 0.485)/0.229`
  - Runs ONNX session inference on CPU/NNAPI
  - Postprocesses tensor output: sigmoid thresholding, scales mask back to original Bitmap dimensions, applies alpha mask
- `CloudApiRemover`:
  - Compresses bitmap to PNG/JPEG ByteArray
  - Sends multipart POST to `/remove-bg` with OkHttp (timeouts configured to 60s)
  - Decodes response bytes to Bitmap

### 3.3 ViewModel & State Management
```kotlin
data class MainUiState(
    val selectedBitmap: Bitmap? = null,
    val resultBitmap: Bitmap? = null,
    val resultBytes: ByteArray? = null,
    val selectedEngine: RemoverEngine = RemoverEngine.MEDIAPIPE,
    val isProcessing: Boolean = false,
    val statusMessage: String = "Select an image to get started",
    val serverUrl: String = "http://10.0.2.2:8000/remove-bg",
    val showSettingsDialog: Boolean = false
)
```

## 4. Jetpack Compose UI Design
- **TopAppBar**: Material 3 TopAppBar with app title and settings icon (for server URL configuration).
- **Engine Selector**: FilterChip / Segmented Button allowing user to switch between:
  - ⚡ `Fast Portrait (Offline)`
  - 🎒 `General Objects (Offline)`
  - ☁️ `Cloud Server`
- **Image Comparison Cards**:
  - Original Image card with dashed placeholder or selected image preview.
  - Result Image card with checkerboard transparent background for transparent PNG visualization.
- **Action Control Bar**:
  - `Select Image` button (launches `rememberLauncherForActivityResult(GetContent())`).
  - `Remove Background` primary button (with loading spinner during processing).
  - `Save Result` button (exports transparent PNG to Pictures/RMBG via MediaStore).

## 5. Asset & Model Assets
- Store lightweight models in `app/src/main/assets/models/`:
  - `selfie_segmenter.tflite` (~2.5 MB)
  - `u2netp.onnx` (~4.5 MB)
- Add build configuration to prevent compression of `.tflite` and `.onnx` models in APK.

## 6. Build & CI Configuration
- Enable Compose in `app/build.gradle.kts`:
  - `buildFeatures { compose = true }`
  - Compose compiler extension configuration
  - Add Jetpack Compose BOM, Material 3, ViewModel Compose, MediaPipe Vision, ONNX Runtime Android dependencies.
- Update `.github/workflows/ci.yml` to ensure Android debug build succeeds in CI.

## 7. Error Handling & Edge Cases
- **Out of Memory Protection**: Large images downsampled to maximum 2048px before processing.
- **Model Load Failures**: Graceful fallback and user-friendly error messages if asset fails to load.
- **Storage Permissions**: Scoped Storage compliant on Android 10+ (Q+) without requiring `WRITE_EXTERNAL_STORAGE` permission.
