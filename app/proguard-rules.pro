# TextSwap ProGuard Rules

# Keep ML Kit
-keep class com.google.mlkit.** { *; }

# Keep ONNX Runtime
-keep class ai.onnxruntime.** { *; }

# Keep TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }

# Keep Coil
-keep class coil.** { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep data classes (reflection used by serialization)
-keep class com.textswap.app.model.** { *; }