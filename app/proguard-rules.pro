# Project specific ProGuard rules for ClusterPlayer

# Preserve source file and line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# AndroidX Media3 & ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keepattributes *Annotation*
-keepclassmembers class * extends androidx.media3.session.MediaSessionService {
    public <init>();
}
-keep class * extends androidx.media3.session.MediaSession$Callback { *; }

# Android Architecture Components / Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.Dao { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter *;
}

# AndroidX DataStore
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ClusterPlayer Models & Receivers
-keep class com.joaohouto.clusterplayer.data.model.** { *; }
-keep class com.joaohouto.clusterplayer.data.local.entity.** { *; }
-keep public class com.joaohouto.clusterplayer.ClusterPlayerApplication
-keep public class com.joaohouto.clusterplayer.player.PlaybackService
-keep public class com.joaohouto.clusterplayer.player.AutoMediaButtonReceiver
-keep public class com.joaohouto.clusterplayer.receiver.UsbMountReceiver