# R8 and ProGuard configuration for Lika Lens
-dontwarn android.hardware.camera2.**
-dontwarn android.media.**

# Preserve app components and camera models
-keepattributes SourceFile,LineNumberTable
-keep class com.scope.camera.** { *; }
