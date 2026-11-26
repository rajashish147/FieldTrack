package com.fieldtrack.app.core.crash

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

// ─── CrashReporter ────────────────────────────────────────────────────────────
// Lightweight uncaught-exception handler.
//
// On crash:
//   1. Writes a crash report to app-private storage (no permission required)
//   2. Keeps last MAX_REPORTS files (rotates oldest)
//   3. On next launch, FieldTrackApp reads pending reports and can forward them
//      to the backend via POST /crashes (or surface them in an admin view)
//
// This avoids any third-party dependency (no Firebase, no ACRA backend needed).
// Reports are stored in: filesDir/crash_reports/*.txt
// ─────────────────────────────────────────────────────────────────────────────

object CrashReporter {

    private const val TAG         = "CrashReporter"
    private const val MAX_REPORTS = 10
    private const val DIR_NAME    = "crash_reports"

    fun init(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashReport(appContext, thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write crash report", e)
            } finally {
                // Delegate to system handler (shows crash dialog / restarts)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        Log.i(TAG, "CrashReporter initialised")
    }

    private fun writeCrashReport(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }

        // Rotate — keep only last MAX_REPORTS files
        val existing = dir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
        if (existing.size >= MAX_REPORTS) {
            existing.take(existing.size - MAX_REPORTS + 1).forEach { it.delete() }
        }

        val timestamp = Instant.now().toString().replace(":", "-")
        val reportFile = File(dir, "crash_$timestamp.txt")

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        reportFile.writeText(
            buildString {
                appendLine("=== FieldTrack Crash Report ===")
                appendLine("Timestamp : $timestamp")
                appendLine("Thread    : ${thread.name}")
                appendLine("Version   : ${getVersionName(context)}")
                appendLine("Device    : ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("Android   : ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                appendLine()
                appendLine("=== Stack Trace ===")
                appendLine(sw.toString())
            }
        )

        Log.e(TAG, "Crash report written to ${reportFile.absolutePath}")
    }

    /**
     * Returns a list of pending crash report files.
     * Call from FieldTrackApp on startup to detect previous crashes.
     */
    fun getPendingReports(context: Context): List<File> {
        val dir = File(context.filesDir, DIR_NAME)
        return dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /** Mark all pending reports as handled (delete them) */
    fun clearReports(context: Context) {
        File(context.filesDir, DIR_NAME).listFiles()?.forEach { it.delete() }
    }

    private fun getVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }
}
