package com.fieldtrack.app.ui.employee

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.fieldtrack.app.R
import com.fieldtrack.app.databinding.ActivityEmployeeHomeBinding
import com.fieldtrack.app.service.LocationTrackingService
import com.fieldtrack.app.util.showToast
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Employee home screen with bottom navigation.
 * Handles the permission chain for location tracking.
 *
 * Navigation destinations:
 *   - AttendanceFragment (check-in/out + timer)
 *   - MyLocationFragment (GPS map view)
 *   - EmployeeExpenseFragment (expense submission)
 */
class EmployeeHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmployeeHomeBinding
    private val viewModel: EmployeeHomeViewModel by viewModel()
    
    // Broadcast receiver for notification stop action
    private val checkoutReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationTrackingService.ACTION_REQUEST_CHECKOUT) {
                // Trigger check-out via ViewModel
                context?.let { viewModel.checkOut(it) }
            }
        }
    }

    // Permission chain: foreground location → background location → notifications
    private val foregroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestBackgroundLocationIfNeeded()
        } else {
            showToast("Location permission is required for tracking")
        }
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestNotificationPermissionIfNeeded()
        }
        // Background location is optional — don't block the app
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Notification permission is optional
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmployeeHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        requestPermissionsChain()
        registerCheckoutReceiver()
    }
    
    private fun registerCheckoutReceiver() {
        val filter = IntentFilter(LocationTrackingService.ACTION_REQUEST_CHECKOUT)
        ContextCompat.registerReceiver(this, checkoutReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(checkoutReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered, ignore
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)
    }

    private fun requestPermissionsChain() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            foregroundLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            requestBackgroundLocationIfNeeded()
        }
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            requestNotificationPermissionIfNeeded()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
