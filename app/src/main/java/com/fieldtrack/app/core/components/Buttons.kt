package com.fieldtrack.app.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import com.fieldtrack.app.core.designsystem.*

// ─── PrimaryButton ────────────────────────────────────────────────────────────
// Pill-shaped gradient indigo button. 48dp min touch target enforced.
// Multi-tap protected: disabled while loading OR explicitly disabled.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    fullWidth: Boolean = false,
    icon: @Composable (() -> Unit)? = null,
) {
    // loading=true implicitly disables the button — multi-tap protection
    val effectiveEnabled = enabled && !loading
    val alpha = if (effectiveEnabled) 1f else 0.45f

    Button(
        onClick = onClick,
        enabled = effectiveEnabled,
        shape = FtShape.Pill,
        colors = ButtonDefaults.buttonColors(
            containerColor    = Color.Transparent,
            contentColor      = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor   = Color.White.copy(alpha = 0.5f),
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = Spacing.MinTouchTarget),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        Box(
            modifier = Modifier
                .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            GradientPrimaryStart.copy(alpha = alpha),
                            GradientPrimaryEnd.copy(alpha = alpha),
                        ),
                        start = Offset(0f, 0f),
                        end   = Offset(Float.POSITIVE_INFINITY, 0f),
                    ),
                    shape = FtShape.Pill,
                )
                .padding(horizontal = Spacing.LG, vertical = Spacing.SM + 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier  = Modifier.size(20.dp),
                    color     = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    icon?.invoke()
                    Text(text = text, style = FtTypography.Button, color = Color.White)
                }
            }
        }
    }
}

// ─── SecondaryButton ─────────────────────────────────────────────────────────
// Outlined pill button. Multi-tap safe — no double-action risk.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    fullWidth: Boolean = false,
    icon: @Composable (() -> Unit)? = null,
) {
    OutlinedButton(
        onClick  = onClick,
        enabled  = enabled && !loading,
        shape    = FtShape.Pill,
        colors   = ButtonDefaults.outlinedButtonColors(
            contentColor         = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = Spacing.MinTouchTarget),
        contentPadding = PaddingValues(horizontal = Spacing.LG, vertical = Spacing.SM + 2.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier    = Modifier.size(18.dp),
                color       = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                icon?.invoke()
                Text(text = text, style = FtTypography.Button)
            }
        }
    }
}
