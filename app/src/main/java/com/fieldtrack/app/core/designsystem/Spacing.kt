package com.fieldtrack.app.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─── FieldTrack Spacing System (8dp grid) ────────────────────────────────────
// ALL padding, margin, gap values MUST use these constants.
// Custom per-screen spacing is PROHIBITED.
// ─────────────────────────────────────────────────────────────────────────────

object Spacing {
    /** 2dp — micro gaps, divider insets */
    val Micro: Dp = 2.dp

    /** 4dp — xs: icon padding, tight gaps */
    val XS: Dp = 4.dp

    /** 8dp — sm: compact component spacing */
    val SM: Dp = 8.dp

    /** 16dp — md: standard horizontal screen padding, card padding */
    val MD: Dp = 16.dp

    /** 24dp — lg: section gap, large card padding */
    val LG: Dp = 24.dp

    /** 32dp — xl: large sections */
    val XL: Dp = 32.dp

    /** 48dp — xxl: bottom nav height buffer, hero sections */
    val XXL: Dp = 48.dp

    /** 64dp — 3xl: extra large spacing */
    val XXXL: Dp = 64.dp

    /** Minimum touch target (WCAG + Android guidelines) */
    val MinTouchTarget: Dp = 48.dp
}
