# Keep line numbers for crash stack traces
-keepattributes SourceFile,LineNumberTable

# WorkManager workers instantiated via reflection
-keep class com.example.backup.DriveBackupWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Room database & generated implementation
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# OkHttp optional JVM TLS providers (unused on Android)
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
