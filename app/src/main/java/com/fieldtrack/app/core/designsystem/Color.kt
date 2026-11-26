package com.fieldtrack.app.core.designsystem

import androidx.compose.ui.graphics.Color

// ─── FieldTrack Design System — Color Tokens (FINAL) ─────────────────────────
// PRIMARY: Indigo — matches Stitch design exactly.
// This is the FINAL, authoritative color decision.
// Source: Stitch project 1153454683470574174
// ─────────────────────────────────────────────────────────────────────────────

// ══════════════════════════════════════════════════════════════════════════════
// LIGHT MODE
// ══════════════════════════════════════════════════════════════════════════════

// Primary — Indigo  hsl(270 48% 42%)
val LightPrimary            = Color(0xFF4F378A)   // Stitch primary
val LightOnPrimary          = Color.White
val LightPrimaryContainer   = Color(0xFFEADDFF)   // hsl(270 100% 94%)
val LightOnPrimaryContainer = Color(0xFF21005D)   // hsl(270 100% 18%)

// Secondary — muted violet  hsl(269 22% 42%)
val LightSecondary            = Color(0xFF63597C)
val LightOnSecondary          = Color.White
val LightSecondaryContainer   = Color(0xFFE8DEF8)
val LightOnSecondaryContainer = Color(0xFF1D1135)

// Tertiary — warm amber  hsl(35 90% 46%)
val LightTertiary            = Color(0xFFD0A020)
val LightOnTertiary          = Color.White
val LightTertiaryContainer   = Color(0xFFF7E0A8)
val LightOnTertiaryContainer = Color(0xFF3B2B00)

// Error  hsl(4 85% 52%)
val LightError            = Color(0xFFBA1A1A)
val LightOnError          = Color.White
val LightErrorContainer   = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

// Background  hsl(250 100% 99%) — slight purple tint (Stitch)
val LightBackground   = Color(0xFFFDF7FF)
val LightOnBackground = Color(0xFF1C1B1F)

// Surface
val LightSurface                 = Color(0xFFFDF7FF)
val LightSurfaceDim              = Color(0xFFDED8E1)
val LightSurfaceContainerLowest  = Color(0xFFFFFFFF)
val LightSurfaceContainerLow     = Color(0xFFF7F2FA)
val LightSurfaceContainer        = Color(0xFFF3EDF7)
val LightSurfaceContainerHigh    = Color(0xFFECE6F0)
val LightSurfaceContainerHighest = Color(0xFFE6E0E9)

val LightOnSurface        = Color(0xFF1C1B1F)
val LightOnSurfaceVariant = Color(0xFF49454F)
val LightOutline          = Color(0xFF79747E)
val LightOutlineVariant   = Color(0xFFCAC4D0)
val LightShadow           = Color(0xFF000000)

// ══════════════════════════════════════════════════════════════════════════════
// DARK MODE
// ══════════════════════════════════════════════════════════════════════════════

// Primary — bright indigo optimised for dark bg  hsl(270 100% 84%)
val DarkPrimary            = Color(0xFFD0BCFF)
val DarkOnPrimary          = Color(0xFF381E72)
val DarkPrimaryContainer   = Color(0xFF4F378A)
val DarkOnPrimaryContainer = Color(0xFFEADDFF)

val DarkSecondary            = Color(0xFFCCC2DC)
val DarkOnSecondary          = Color(0xFF332D41)
val DarkSecondaryContainer   = Color(0xFF4A4458)
val DarkOnSecondaryContainer = Color(0xFFE8DEF8)

val DarkTertiary            = Color(0xFFEFB8C8)
val DarkOnTertiary          = Color(0xFF492532)
val DarkTertiaryContainer   = Color(0xFF633B48)
val DarkOnTertiaryContainer = Color(0xFFFFD8E4)

val DarkError            = Color(0xFFFFB4AB)
val DarkOnError          = Color(0xFF690005)
val DarkErrorContainer   = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkBackground   = Color(0xFF141218)   // hsl(270 14% 9%)
val DarkOnBackground = Color(0xFFE6E0E9)

val DarkSurface                 = Color(0xFF141218)
val DarkSurfaceDim              = Color(0xFF141218)
val DarkSurfaceContainerLowest  = Color(0xFF0F0D13)
val DarkSurfaceContainerLow     = Color(0xFF1D1B20)
val DarkSurfaceContainer        = Color(0xFF211F26)
val DarkSurfaceContainerHigh    = Color(0xFF2B2930)
val DarkSurfaceContainerHighest = Color(0xFF36343B)

val DarkOnSurface        = Color(0xFFE6E0E9)
val DarkOnSurfaceVariant = Color(0xFFCAC4D0)
val DarkOutline          = Color(0xFF938F99)
val DarkOutlineVariant   = Color(0xFF49454F)
val DarkShadow           = Color(0xFF000000)

// ─── Semantic Accent Colors (role-independent) ────────────────────────────────
// Used for status badges — NOT primary UI. Remain consistent across themes.
val AccentSuccess = Color(0xFF2E8B57)   // green — active / approved
val AccentWarning = Color(0xFFF59E0B)   // amber — pending
val AccentError   = Color(0xFFBA1A1A)   // red   — rejected / error
val AccentInfo    = Color(0xFF0EA5E9)   // cyan  — informational
val AccentYellow  = Color(0xFFF59E0B)   // leaderboard gold
val AccentGray    = Color(0xFF79747E)   // neutral

// ─── Gradient definitions (used in PrimaryButton + login bg) ─────────────────
val GradientPrimaryStart = Color(0xFF381E72)   // deep indigo
val GradientPrimaryEnd   = Color(0xFF6750A4)   // mid indigo
