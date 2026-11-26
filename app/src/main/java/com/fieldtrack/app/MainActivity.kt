package com.fieldtrack.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.fieldtrack.app.core.data.SessionManager
import com.fieldtrack.app.core.designsystem.*
import com.fieldtrack.app.core.navigation.AppNavGraph
import com.fieldtrack.app.core.navigation.NavRoutes
import com.fieldtrack.app.core.network.ApiService
import com.fieldtrack.app.core.network.EmployeeRequestOwner
import com.fieldtrack.app.service.LocationForegroundService
import com.fieldtrack.app.service.LocationSyncWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var api: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FieldTrackTheme {
                // ── Startup auth resolution ────────────────────────────────────
                // Suspend on IO thread to read DataStore.
                // Show splash until resolved — prevents flicker to wrong screen.
                var startDestination by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    val dest = withContext(Dispatchers.IO) {
                        if (sessionManager.isLoggedIn()) {
                            val role = sessionManager.getRole() ?: "EMPLOYEE"
                            if (role == "ADMIN") NavRoutes.ADMIN_DASHBOARD
                            else NavRoutes.EMP_HOME
                        } else {
                            NavRoutes.LOGIN
                        }
                    }
                    startDestination = dest
                }

                // Show splash until destination resolved
                AnimatedContent(
                    targetState = startDestination,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "SplashToApp",
                ) { dest ->
                    if (dest == null) {
                        // ── Splash screen (shown only during DataStore read ~50ms)
                        SplashScreen()
                    } else {
                        val navController = rememberNavController()
                        LaunchedEffect(navController) {
                            sessionManager.sessions.collect { session ->
                                if (session == null && navController.currentDestination?.route != NavRoutes.LOGIN) {
                                    navController.navigate(NavRoutes.LOGIN) { popUpTo(0) { inclusive = true } }
                                }
                            }
                        }
                        AppNavGraph(
                            navController    = navController,
                            startDestination = dest,
                        )
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                sessionManager.sessions
                    .map { auth -> auth?.owner?.takeIf { auth.role == "EMPLOYEE" } }
                    .distinctUntilChanged()
                    .collectLatest { owner ->
                        if (owner == null) return@collectLatest
                        LocationSyncWorker.flush(this@MainActivity)
                        val tracking = LocationForegroundService.activeShift.value
                        try {
                            val response = api.getMySessions(limit = 1, status = "active", owner = EmployeeRequestOwner(owner))
                            if (response.isSuccessful) {
                                val active = response.body()?.data ?: return@collectLatest
                                val session = active.firstOrNull()
                                if (session != null) {
                                    LocationForegroundService.start(this@MainActivity, owner, session)
                                } else if (tracking?.owner == owner) {
                                    LocationForegroundService.stop(this@MainActivity, tracking)
                                }
                            }
                        } catch (e: IOException) {
                            Log.d("MainActivity", "Could not restore shift tracking", e)
                        }
                    }
            }
        }
    }
}

// ─── SplashScreen ─────────────────────────────────────────────────────────────
// Shown only while DataStore read resolves auth state (~50ms).
// Matches app brand — indigo gradient background, centered logo.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientPrimaryStart, GradientPrimaryEnd)
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.MD),
        ) {
            Text(
                text  = "FieldTrack",
                style = FtTypography.H1,
                color = androidx.compose.ui.graphics.Color.White,
            )
            Text(
                text  = "Field Operations Platform",
                style = FtTypography.BodyMedium,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(Spacing.LG))
            CircularProgressIndicator(
                color     = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                modifier  = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}
