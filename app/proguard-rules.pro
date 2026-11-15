# R8 and ProGuard configuration for Lika Lens
-dontwarn android.hardware.camera2.**
-dontwarn android.media.**
-dontwarn android.view.Window

# Preserve stack trace file/line numbers while allowing full R8 shrinking, inlining, and obfuscation
-keepattributes SourceFile,LineNumberTable

# Strip verbose and debug logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
