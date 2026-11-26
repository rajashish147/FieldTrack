package com.fieldtrack.app.features.employee.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldtrack.app.core.components.*
import com.fieldtrack.app.core.designsystem.*
import com.fieldtrack.app.core.navigation.NavRoutes
import com.fieldtrack.app.core.permissions.*
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.time.ZoneId
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

// ─── HomeScreen ───────────────────────────────────────────────────────────────
// Matches Stitch "Employee Home" layout.
// Structure: TopBar / KPI Cards / Check-in Button / Bottom Nav
//
// Permission flow:
//   Check-in tapped → LocationPermissionGate checks permissions →
//   If granted: viewModel.checkIn() → starts service
//   If denied:  PermissionDeniedCard shown instead of check-in button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    onNavigateToSessions: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToProfile: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state   by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var cameraDenied by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraDenied = !granted
        if (granted) viewModel.prepareCheckIn()
    }
    val startCamera: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraDenied = false; viewModel.prepareCheckIn()
        } else cameraPermission.launch(Manifest.permission.CAMERA)
    }
    if (state.cameraChallenge != null) CameraAttendanceDialog(
        busy = state.isCheckingIn, onDismiss = viewModel::cancelCamera, onPhoto = viewModel::checkIn,
    )

    var permissionDenied by remember { mutableStateOf(false) }
    var showBatteryCard by remember {
        mutableStateOf(!BatteryOptimizationHelper.isIgnoringOptimizations(context))
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        topBar = {
            FtTopBar(
                title    = "FieldTrack",
                subtitle = "Employee Dashboard",
                actions  = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile",
                             tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        },
        bottomBar = {
            FtBottomNavBar(
                items        = employeeNavItems(),
                currentRoute = NavRoutes.EMP_HOME,
                onNavigate   = { route ->
                    when (route) {
                        NavRoutes.EMP_SESSIONS -> onNavigateToSessions()
                        NavRoutes.EMP_EXPENSES -> onNavigateToExpenses()
                        NavRoutes.EMP_PROFILE  -> onNavigateToProfile()
                    }
                }
            )
        },
        snackbarHost    = { SnackbarHost(snackbarHostState) },
        containerColor  = MaterialTheme.colorScheme.background,
    ) { innerPadding ->

        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = Spacing.MD),
                verticalArrangement = Arrangement.spacedBy(Spacing.MD),
            ) {
                Spacer(Modifier.height(Spacing.MD))
                repeat(3) { SkeletonCard() }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.MD, vertical = Spacing.MD),
            verticalArrangement = Arrangement.spacedBy(Spacing.MD),
        ) {
            val summary  = state.summary
            val active = state.activeSession
            val isActive = active != null

            // ── KPI Row ───────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.SM),
            ) {
                KpiCard(
                    title       = "This Week",
                    value       = summary?.sessionsThisWeek?.toString() ?: "—",
                    subtitle    = "Sessions",
                    highlighted = true,
                    modifier    = Modifier.weight(1f),
                    icon        = {
                        Icon(Icons.Default.CalendarToday, null,
                             tint     = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                             modifier = Modifier.size(20.dp))
                    }
                )
                KpiCard(
                    title    = "Distance",
                    value    = summary?.let { "${"%.1f".format(it.distanceThisWeek)} km" } ?: "—",
                    subtitle = "Total this week",
                    modifier = Modifier.weight(1f),
                    icon     = {
                        Icon(Icons.Default.Route, null,
                             tint     = MaterialTheme.colorScheme.primary,
                             modifier = Modifier.size(20.dp))
                    }
                )
            }

            // ── Duration Card ─────────────────────────────────────────────────
            val durationMinutes = summary?.let { kotlin.math.round(it.hoursThisWeek * 60).toInt() }
            FtCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Hours This Week", style = FtTypography.LabelSmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(durationMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "—", style = FtTypography.H3,
                             color = MaterialTheme.colorScheme.onSurface)
                    }
                    Icon(Icons.Default.Schedule, null,
                         tint     = MaterialTheme.colorScheme.primary,
                         modifier = Modifier.size(32.dp))
                }
            }

            // ── Active Session Status ─────────────────────────────────────────
            if (active != null) {
                val timezone = state.fieldSetup?.settings?.timezone ?: "Asia/Kolkata"
                FtCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                            StatusBadge("Active", BadgeVariant.SUCCESS)
                            Text("Session in progress", style = FtTypography.BodyMedium,
                                 color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = "Started: ${formatFieldTime(active.checkinAt, timezone)}",
                                style = FtTypography.BodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.trackingActive) {
                                Text(
                                    if (state.pendingLocations > 0) "${state.pendingLocations} GPS updates pending"
                                    else "Location tracking active",
                                    style = FtTypography.BodySmall,
                                )
                            } else {
                                Text("Location tracking is paused.", style = FtTypography.BodySmall)
                                TextButton(onClick = viewModel::loadSummary) { Text("Resume tracking") }
                            }
                        }
                        Icon(Icons.Default.FiberManualRecord, null,
                             tint     = AccentSuccess,
                             modifier = Modifier.size(12.dp))
                    }
                }
            }

            if (isActive && showBatteryCard) {
                FtCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.MD),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.BatterySaver,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Spacing.XS),
                        ) {
                            Text(
                                "Keep tracking stable",
                                style = FtTypography.LabelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "Allow FieldTrack to keep GPS active during long sessions.",
                                style = FtTypography.BodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = {
                                BatteryOptimizationHelper.openOptimizationSettings(context)
                                showBatteryCard = false
                            },
                        ) { Text("Allow") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.SM))

            // ── Check-in / Check-out ──────────────────────────────────────────
            // Permission gate wraps the check-in action.
            // Check-out does not need permission — session is already active.
            if (!state.shiftVerified) {
                SecondaryButton(text = "Retry loading your shift", onClick = viewModel::loadSummary, fullWidth = true)
            } else if (isActive) {
                PrimaryButton(
                    text     = "Check Out",
                    onClick  = { viewModel.checkOut() },
                    loading  = state.isCheckingOut,
                    fullWidth = true,
                    icon     = {
                        Icon(Icons.AutoMirrored.Filled.Logout, null,
                             tint     = androidx.compose.ui.graphics.Color.White,
                             modifier = Modifier.size(18.dp))
                    }
                )
            } else {
                if (permissionDenied) {
                    PermissionDeniedCard(
                        onRetry = { permissionDenied = false },
                    )
                } else {
                    LocationPermissionGate(
                        onPermissionsGranted = startCamera,
                        onPermissionsDenied  = { permissionDenied = true },
                    ) { requestPermissions ->
                        PrimaryButton(
                            text     = "Start work with selfie & GPS",
                            onClick  = {
                                if (PermissionHelper.hasLocationPermission(context) &&
                                    PermissionHelper.hasNotificationPermission(context)) {
                                    startCamera()
                                } else {
                                    requestPermissions()
                                }
                            },
                            loading  = state.isCheckingIn,
                            fullWidth = true,
                            icon     = {
                                Icon(Icons.AutoMirrored.Filled.Login, null,
                                     tint     = androidx.compose.ui.graphics.Color.White,
                                     modifier = Modifier.size(18.dp))
                            }
                        )
                    }
                }
            }

            SecondaryButton(
                text     = "View Sessions",
                onClick  = onNavigateToSessions,
                fullWidth = true,
            )
            WorkLocationsCard(state = state, onReach = viewModel::confirmArrival)
            FieldWorkCard(state = state, onPeriod = viewModel::loadFieldOverview)
            if (cameraDenied) Text("Camera permission is required for live selfie attendance. Enable it in app settings or retry.", color = MaterialTheme.colorScheme.error)
        }
    }
}

internal fun formatFieldTime(timestamp: String, timezone: String): String =
    runCatching {
        OffsetDateTime.parse(timestamp).atZoneSameInstant(ZoneId.of(timezone))
            .format(DateTimeFormatter.ofPattern("dd MMM, HH:mm z"))
    }.getOrDefault(timestamp)

// ── Employee bottom nav items ─────────────────────────────────────────────────────────
@Composable
private fun employeeNavItems(): List<NavItem> = listOf(
    NavItem(NavRoutes.EMP_HOME,     "Home",
        icon = { Icon(Icons.Default.Home,    "Home") },
        selectedIcon = { Icon(Icons.Default.Home, "Home") }),
    NavItem(NavRoutes.EMP_SESSIONS, "Sessions",
        icon = { Icon(Icons.Default.Timer,   "Sessions") },
        selectedIcon = { Icon(Icons.Default.Timer, "Sessions") }),
    NavItem(NavRoutes.EMP_EXPENSES, "Expenses",
        icon = { Icon(Icons.Default.Receipt, "Expenses") },
        selectedIcon = { Icon(Icons.Default.Receipt, "Expenses") }),
    NavItem(NavRoutes.EMP_PROFILE,  "Profile",
        icon = { Icon(Icons.Default.Person,  "Profile") },
        selectedIcon = { Icon(Icons.Default.Person, "Profile") }),
)
