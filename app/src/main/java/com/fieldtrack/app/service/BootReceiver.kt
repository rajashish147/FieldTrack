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

        if (!sessionManager.isCheckedIn) {
            Log.d(TAG, "User not checked in, skipping service restart")
            return
        }

        if (!LocationUtils.hasLocationPermission(context)) {
            Log.w(TAG, "Missing location permission, cannot restart service")
            return
        }

        Log.d(TAG, "Device rebooted while user checked in, restarting location service")

        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
