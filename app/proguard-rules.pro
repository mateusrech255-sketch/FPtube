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

# Material Icons Extended - Otimização agressiva
# R8 já remove o que não é usado, mas podemos desencorajar reflexão neles
-dontnote androidx.compose.material.icons.**
