package com.fieldtrack.app.core.navigation

// ─── NavRoutes ────────────────────────────────────────────────────────────────
// Single source of all navigation destinations.
// No magic strings anywhere in the codebase.
// ─────────────────────────────────────────────────────────────────────────────

object NavRoutes {
    // ── Auth ─────────────────────────────────────────────────────────────────
    const val LOGIN   = "auth/login"

    // ── Employee ─────────────────────────────────────────────────────────────
    const val EMP_HOME     = "employee/home"
    const val EMP_SESSIONS = "employee/sessions"
    const val EMP_EXPENSES = "employee/expenses"
    const val EMP_SUBMIT_EXPENSE = "employee/expenses/submit"
    const val EMP_PROFILE  = "employee/profile"

    // ── Admin ─────────────────────────────────────────────────────────────────
    const val ADMIN_DASHBOARD  = "admin/dashboard"
    const val ADMIN_EMPLOYEES  = "admin/employees"
    const val ADMIN_EMPLOYEE_DETAIL = "admin/employees/{employeeId}"
    const val ADMIN_TRACKING   = "admin/tracking"
    const val ADMIN_EXPENSES   = "admin/expenses"
    const val ADMIN_ANALYTICS  = "admin/analytics"

    // ── Helpers ──────────────────────────────────────────────────────────────
    fun adminEmployeeDetail(employeeId: String) =
        "admin/employees/$employeeId"
}
