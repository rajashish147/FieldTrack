package com.fieldtrack.app.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fieldtrack.app.core.components.*
import com.fieldtrack.app.core.designsystem.*

// ─── PermissionHelper ─────────────────────────────────────────────────────────
// Centralised permission state checks used across the app.
// ─────────────────────────────────────────────────────────────────────────────

object PermissionHelper {

    /** True if the app already holds fine location permission */
    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * True if notification permission is required AND already granted.
     * On API < 33, notifications don't need a runtime permission — returns true.
     */
    fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true  // Not required below API 33
        }

    /** Returns the set of permissions to request for full tracking capability */
    fun trackingPermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()
}

// ─── LocationPermissionGate ───────────────────────────────────────────────────
// Wraps content that requires location + notification permissions.
// Shows an explanatory dialog if permission is not granted.
// Blocks tracking start until permission is confirmed.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LocationPermissionGate(
    onPermissionsGranted: () -> Unit,
    onPermissionsDenied: () -> Unit = {},
    content: @Composable (requestPermissions: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (locationGranted) {
            onPermissionsGranted()
        } else {
            onPermissionsDenied()
        }
    }

    val requestPermissions: () -> Unit = {
        val allGranted = PermissionHelper.hasLocationPermission(context) &&
                         PermissionHelper.hasNotificationPermission(context)
        if (allGranted) {
            onPermissionsGranted()
        } else {
            showRationale = true
        }
    }

    if (showRationale) {
        PermissionRationaleDialog(
            onConfirm = {
                showRationale = false
                permissionsLauncher.launch(PermissionHelper.trackingPermissions())
            },
            onDismiss = {
                showRationale = false
                onPermissionsDenied()
            }
        )
    }

    content(requestPermissions)
}

// ─── PermissionRationaleDialog ────────────────────────────────────────────────

@Composable
private fun PermissionRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = FtShape.Card,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        icon = {
            Icon(Icons.Default.LocationOn, null,
                 tint = MaterialTheme.colorScheme.primary,
                 modifier = Modifier.size(Spacing.XXL))
        },
        title = {
            Text("Location Access Required",
                 style = FtTypography.H4,
                 color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                Text(
                    "FieldTrack needs location permission to track your field activity during a session.",
                    style = FtTypography.BodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Text(
                        "Notification permission is needed to show a persistent notification while tracking is active.",
                        style = FtTypography.BodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton(text = "Grant Permission", onClick = onConfirm)
        },
        dismissButton = {
            SecondaryButton(text = "Not Now", onClick = onDismiss)
        }
    )
}

// ─── PermissionDeniedCard ─────────────────────────────────────────────────────
// Shown in-place when user denies permission — blocks check-in with explanation.

@Composable
fun PermissionDeniedCard(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FtCard(modifier = modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.MD),
        ) {
            Icon(Icons.Default.LocationOn, null,
                 tint = MaterialTheme.colorScheme.error,
                 modifier = Modifier.size(40.dp))
            Text("Location Permission Required",
                 style = FtTypography.LabelLarge,
                 color = MaterialTheme.colorScheme.onSurface)
            Text("Please grant location permission in Settings to enable field tracking.",
                 style = FtTypography.BodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            SecondaryButton(text = "Grant Permission", onClick = onRetry, fullWidth = true)
        }
    }
}
