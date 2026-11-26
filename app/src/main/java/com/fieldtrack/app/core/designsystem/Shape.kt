package com.fieldtrack.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// ─── FieldTrack Shape System ──────────────────────────────────────────────────
// Source: web-app globals.css + Stitch design (layout structure kept)
//
// RULE: All interactive elements (buttons, inputs) → PillShape
//       All containers (cards, dialogs) → CardShape (20dp)
//       Status indicators (badges) → PillShape
// ─────────────────────────────────────────────────────────────────────────────

object FtShape {
    /** Fully rounded — buttons, inputs, badges, chips, search bars */
    val Pill = RoundedCornerShape(percent = 50)

    /** 20dp — standard cards, containers, dialogs */
    val Card = RoundedCornerShape(20.dp)

    /** 16dp — compact cards, smaller containers */
    val CardSmall = RoundedCornerShape(16.dp)

    /** 12dp — nav items, tab pills group container */
    val NavItem = RoundedCornerShape(12.dp)

    /** 8dp — tab pill individual items */
    val TabItem = RoundedCornerShape(8.dp)

    /** 4dp — smallest elements */
    val Xs = RoundedCornerShape(4.dp)
}
