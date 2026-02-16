package com.fieldtrack.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.util.LocationUtils
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BootReceiver : BroadcastReceiver(), KoinComponent {

    companion object {
        const val TAG = "BootReceiver"
    }

    private val sessionManager: SessionManager by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // v1.4 Phase 0: Do NOT start service on boot
        // Service will start when AttendanceFragment observes recovery completion
        // This prevents race condition where service starts before session ID is restored
        
        if (!sessionManager.isCheckedIn) {
            Log.d(TAG, "User not checked in, skipping service restart")
            return
        }

        if (!LocationUtils.hasLocationPermission(context)) {
            Log.w(TAG, "Missing location permission, cannot restart service")
            return
        }

        // Phase 0: Service start is deferred to AttendanceFragment after recovery
        Log.d(TAG, "Device rebooted while checked in - service will start after recovery")
    }
}
