package com.fieldtrack.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

// ─── BootReceiver ─────────────────────────────────────────────────────────────
// Receives BOOT_COMPLETED and MY_PACKAGE_REPLACED to re-schedule workers.
// Retries queued GPS. Android requires a visible activity to start location
// collection again after reboot with while-in-use location permission.
// ─────────────────────────────────────────────────────────────────────────────

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        Log.i("BootReceiver", "Boot/update received — scheduling recovery")

        SessionRecoveryWorker.schedule(context)
        SessionRecoveryWorker.enqueueImmediate(context)
    }
}
