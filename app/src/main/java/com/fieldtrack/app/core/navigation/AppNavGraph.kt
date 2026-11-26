package com.fieldtrack.app.core.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.fieldtrack.app.features.auth.LoginScreen
import com.fieldtrack.app.features.employee.home.HomeScreen
import com.fieldtrack.app.features.employee.sessions.SessionsScreen
import com.fieldtrack.app.features.employee.expenses.ExpensesScreen
import com.fieldtrack.app.features.employee.expenses.SubmitExpenseScreen
import com.fieldtrack.app.features.employee.profile.ProfileScreen
import com.fieldtrack.app.features.admin.dashboard.AdminDashboardScreen
import com.fieldtrack.app.features.admin.employees.EmployeesScreen
import com.fieldtrack.app.features.admin.employees.EmployeeDetailScreen
import com.fieldtrack.app.features.admin.tracking.TrackingScreen
import com.fieldtrack.app.features.admin.expenses.AdminExpensesScreen
import com.fieldtrack.app.features.admin.analytics.AnalyticsScreen

// ─── AppNavGraph ──────────────────────────────────────────────────────────────
// Single NavHost for entire app.
// Role-based navigation: EMPLOYEE → emp graph, ADMIN → admin graph.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {

        // ── Auth ──────────────────────────────────────────────────────────────
        composable(NavRoutes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { role ->
                    val dest = if (role == "ADMIN") NavRoutes.ADMIN_DASHBOARD
                               else NavRoutes.EMP_HOME
                    navController.navigate(dest) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        // ── Employee Screens ──────────────────────────────────────────────────
        composable(NavRoutes.EMP_HOME) {
            HomeScreen(
                onNavigateToSessions = { navController.navigate(NavRoutes.EMP_SESSIONS) },
                onNavigateToExpenses = { navController.navigate(NavRoutes.EMP_EXPENSES) },
                onNavigateToProfile  = { navController.navigate(NavRoutes.EMP_PROFILE) },
            )
        }

        composable(NavRoutes.EMP_SESSIONS) {
            SessionsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(NavRoutes.EMP_EXPENSES) {
            ExpensesScreen(
                onSubmit = { navController.navigate(NavRoutes.EMP_SUBMIT_EXPENSE) },
                onBack   = { navController.popBackStack() },
            )
        }

        composable(NavRoutes.EMP_SUBMIT_EXPENSE) {
            SubmitExpenseScreen(
                onSuccess = { navController.popBackStack() },
                onBack    = { navController.popBackStack() },
            )
        }

        composable(NavRoutes.EMP_PROFILE) {
            ProfileScreen(
                onLogout = {
                    navController.navigate(NavRoutes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Admin Screens ─────────────────────────────────────────────────────
        composable(NavRoutes.ADMIN_DASHBOARD) {
            AdminDashboardScreen(
                onNavigateToEmployees = { navController.navigate(NavRoutes.ADMIN_EMPLOYEES) },
                onNavigateToTracking  = { navController.navigate(NavRoutes.ADMIN_TRACKING) },
                onNavigateToExpenses  = { navController.navigate(NavRoutes.ADMIN_EXPENSES) },
                onNavigateToAnalytics = { navController.navigate(NavRoutes.ADMIN_ANALYTICS) },
            )
        }

        composable(NavRoutes.ADMIN_EMPLOYEES) {
            EmployeesScreen(
                onEmployeeClick = { employeeId ->
                    navController.navigate(NavRoutes.adminEmployeeDetail(employeeId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(NavRoutes.ADMIN_EMPLOYEE_DETAIL) { backStack ->
            val employeeId = backStack.arguments?.getString("employeeId") ?: return@composable
            EmployeeDetailScreen(
                employeeId = employeeId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(NavRoutes.ADMIN_TRACKING) {
            TrackingScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(NavRoutes.ADMIN_EXPENSES) {
            AdminExpensesScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(NavRoutes.ADMIN_ANALYTICS) {
            AnalyticsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
