# ─── FieldTrack ProGuard Rules (PRODUCTION) ──────────────────────────────────
# Version: 2.0.0
# Keep rules are additive — remove only what is explicitly confirmed unused.
# ─────────────────────────────────────────────────────────────────────────────

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ── Hilt (DI) ─────────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.* { *; }
-keepclasseswithmembernames class * {
    @dagger.hilt.* <fields>;
    @dagger.hilt.* <methods>;
}
-dontwarn dagger.hilt.**

# ── Retrofit + OkHttp ────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepclassmembernames interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Gson (DTO serialization) ──────────────────────────────────────────────────
-keepattributes Signature
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── FieldTrack DTOs — keep ALL data class fields ─────────────────────────────
# Without this, Gson reflection fails on minified builds.
-keep class com.fieldtrack.app.core.network.dto.** { *; }

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract *;
}
-dontwarn androidx.room.**

# ── WorkManager ───────────────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-dontwarn androidx.work.**

# ── FieldTrack Workers ────────────────────────────────────────────────────────
-keep class com.fieldtrack.app.service.LocationSyncWorker { *; }
-keep class com.fieldtrack.app.service.SessionRecoveryWorker { *; }
-keep class com.fieldtrack.app.service.ExpenseRetryWorker { *; }

# ── DataStore ─────────────────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ── OSMDroid (maps) ───────────────────────────────────────────────────────────
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ── Crash Reporter ────────────────────────────────────────────────────────────
-keep class com.fieldtrack.app.core.crash.CrashReporter { *; }

# ── FieldTrack Services ───────────────────────────────────────────────────────
-keep class com.fieldtrack.app.service.LocationForegroundService { *; }
-keep class com.fieldtrack.app.service.BootReceiver { *; }

# ── Coroutines ────────────────────────────────────────────────────────────────
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ── Compose ───────────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Remove debug-only code in release ─────────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
