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
