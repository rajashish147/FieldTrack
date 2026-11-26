package com.fieldtrack.app.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fieldtrack.app.core.designsystem.*

// ─── StatusBadge ─────────────────────────────────────────────────────────────
// Pill-shaped status indicator using semantic accent colors.
// These are INDEPENDENT of the primary indigo system.
//
// API status → Badge variant:
//   APPROVED / ACTIVE   → Success (green)
//   PENDING             → Pending (amber)
//   REJECTED            → Error   (red)
//   INACTIVE / other    → Neutral (gray)
//   info / info         → Info    (cyan)
// ─────────────────────────────────────────────────────────────────────────────

enum class BadgeVariant { SUCCESS, PENDING, ERROR, INFO, NEUTRAL }

@Composable
fun StatusBadge(
    text: String,
    variant: BadgeVariant,
    modifier: Modifier = Modifier,
) {
    val (bgColor, textColor) = when (variant) {
        BadgeVariant.SUCCESS -> AccentSuccess.copy(alpha = 0.14f) to AccentSuccess
        BadgeVariant.PENDING -> AccentWarning.copy(alpha = 0.16f) to AccentWarning
        BadgeVariant.ERROR   -> AccentError.copy(alpha = 0.14f)   to AccentError
        BadgeVariant.INFO    -> AccentInfo.copy(alpha = 0.14f)     to AccentInfo
        BadgeVariant.NEUTRAL -> MaterialTheme.colorScheme.outline.copy(alpha = 0.16f) to
                                MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .clip(FtShape.Pill)
            .background(bgColor)
            .padding(horizontal = Spacing.SM + 2.dp, vertical = Spacing.XS),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(FtShape.Pill)
                .background(textColor)
        )
        Text(
            text  = text,
            style = FtTypography.LabelMedium,
            color = textColor,
        )
    }
}

// ─── String → BadgeVariant factory ───────────────────────────────────────────

fun String.toBadgeVariant(): BadgeVariant = when (this.uppercase()) {
    "APPROVED", "ACTIVE", "COMPLETED" -> BadgeVariant.SUCCESS
    "PENDING", "RECENT", "OPEN"       -> BadgeVariant.PENDING
    "REJECTED", "FAILED", "ERROR"     -> BadgeVariant.ERROR
    "INACTIVE", "CLOSED", "NONE", ""  -> BadgeVariant.NEUTRAL
    "INFO"                            -> BadgeVariant.INFO
    else                              -> BadgeVariant.NEUTRAL
}
