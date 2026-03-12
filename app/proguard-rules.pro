# ==============================
# GECKOVIEW: native and bridge rules
# ==============================
-keep class org.mozilla.geckoview.** { *; }
-keep interface org.mozilla.geckoview.** { *; }
-keepclassmembers class org.mozilla.geckoview.** {
    @org.mozilla.geckoview.Annotations$K_SNC *;
}

# ==============================
# MEDIA3 / EXOPLAYER: native/reflection rules
# ==============================
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ==============================
# AGGRESSIVE SHRINKING: assumenosideeffects
# ==============================
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# General Android Rules
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}