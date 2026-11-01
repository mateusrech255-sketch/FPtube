# Proguard rules for FPtube

# Hilt / Dagger
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.android.HandlerContext {
    java.lang.String name;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Media3 / ExoPlayer
-keep class androidx.media3.common.util.LibraryLoader {*;}
-dontwarn androidx.media3.**

# Youtube-dl Android
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.yausername.aria2c.** { *; }

# Python / JNI
-keep class com.yausername.youtubedl_android.YoutubeDL { *; }
-keepclassmembers class * {
    native <methods>;
}

# Material Icons Extended - Otimização agressiva
# R8 já remove o que não é usado, mas podemos desencorajar reflexão neles
-dontnote androidx.compose.material.icons.**

# Required for org.apache.commons.compress reflection used by youtubedl-android/Python initialization.
# Verified against Release crash: "org.apache.commons.compress.archivers.zip.AsiExtraField" -> "fl".
-keep class org.apache.commons.compress.archivers.zip.** { *; }
