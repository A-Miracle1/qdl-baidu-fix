# ProGuard rules for qdl app
# Keep WebView JavaScript Interface
-keepclassmembers class com.dsh.qdl.MainActivity {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep all methods with @JavascriptInterface annotation
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# Keep generic signatures for reflection
-keepattributes Signature

# Keep annotations
-keepattributes *Annotation*

# Don't warn about missing classes
-dontwarn android.webkit.**
-dontwarn java.lang.invoke.**

# Keep the main application class
-keep public class com.dsh.qdl.MainActivity
-keep public class com.dsh.qdl.KeepAliveService

# Keep all activities, services
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.webkit.WebView

# Optimization settings
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
