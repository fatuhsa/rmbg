# ==========================================
# Proguard / R8 rules for RMBG App (Release)
# ==========================================

# 1. Native JNI & Reflection attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-keepclasseswithmembernames class * {
    native <methods>;
}

# 2. Keep ONNX Runtime JNI & classes
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# 3. Keep MediaPipe Tasks Vision & Framework
-keep class com.google.mediapipe.** { *; }
-keepclassmembers class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# 4. Keep TensorFlow Lite (Used internally by MediaPipe)
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.**

# 5. Keep Google Protobuf (Used by MediaPipe graph runtime across JNI)
-keep class com.google.protobuf.** { *; }
-keepclassmembers class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# 6. Keep App Domain, Engine & Presentation classes
-keep class com.rmbg.app.domain.** { *; }
-keepclassmembers class com.rmbg.app.domain.** { *; }
-keep class com.rmbg.app.engine.** { *; }
-keepclassmembers class com.rmbg.app.engine.** { *; }
-keep class com.rmbg.app.presentation.** { *; }
-keepclassmembers class com.rmbg.app.presentation.** { *; }

# 7. Keep Coroutines internals
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# 8. Suppress build-time compiler warnings
-dontwarn com.google.auto.value.**
-dontwarn autovalue.shaded.**
-dontwarn javax.annotation.processing.**
-dontwarn javax.lang.model.**
-dontwarn com.google.common.**


