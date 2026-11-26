package com.fieldtrack.app.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import com.fieldtrack.app.core.designsystem.*

// ─── FtCard ───────────────────────────────────────────────────────────────────
// Standard card: 20dp radius, surfaceContainer background, subtle border.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FtCard(
    modifier: Modifier = Modifier,
    elevation: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .then(
                if (elevation) Modifier.shadow(
                    elevation   = 2.dp,
                    shape       = FtShape.Card,
                    ambientColor = LightShadow.copy(alpha = 0.06f),
                    spotColor    = LightShadow.copy(alpha = 0.04f),
                ) else Modifier
            )
            .clip(FtShape.Card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = FtShape.Card,
            )
            .padding(Spacing.LG),
        content = content,
    )
}

@Composable
fun FtCardSmall(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(FtShape.CardSmall)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = FtShape.CardSmall,
            )
            .padding(Spacing.MD),
        content = content,
    )
}

// ─── KpiCard ─────────────────────────────────────────────────────────────────
// Highlighted KPI variant: indigo gradient background.
// Standard variant: surfaceContainer background.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun KpiCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: @Composable (() -> Unit)? = null,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val indigoGradient = Brush.linearGradient(
        colors = listOf(GradientPrimaryStart, GradientPrimaryEnd),
        start  = Offset(0f, 0f),
        end    = Offset(Float.POSITIVE_INFINITY, 0f),
    )

    Column(
        modifier = modifier
            .shadow(elevation = 3.dp, shape = FtShape.Card)
            .clip(FtShape.Card)
            .then(
                if (highlighted)
                    Modifier.background(brush = indigoGradient)
                else
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), FtShape.Card)
            )
            .padding(Spacing.LG),
        verticalArrangement = Arrangement.spacedBy(Spacing.SM),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text  = title.uppercase(),
                style = FtTypography.LabelSmall,
                color = if (highlighted) Color.White.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            icon?.invoke()
        }
        Text(
            text  = value,
            style = FtTypography.H2,
            color = if (highlighted) Color.White else MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(
                text  = subtitle,
                style = FtTypography.BodySmall,
                color = if (highlighted) Color.White.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
