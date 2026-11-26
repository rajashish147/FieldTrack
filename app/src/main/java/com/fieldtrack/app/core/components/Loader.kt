package com.fieldtrack.app.core.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fieldtrack.app.core.designsystem.*

// ─── SpinnerSize ──────────────────────────────────────────────────────────────

enum class SpinnerSize(val dp: Dp) {
    Small(20.dp),
    Medium(36.dp),
    Large(56.dp),
}

// ─── FtSpinner ────────────────────────────────────────────────────────────────
// Indigo circular progress indicator.

@Composable
fun FtSpinner(
    size: SpinnerSize = SpinnerSize.Medium,
    modifier: Modifier = Modifier,
) {
    CircularProgressIndicator(
        modifier    = modifier.size(size.dp),
        color       = MaterialTheme.colorScheme.primary,
        strokeWidth = (size.dp.value * 0.08f).dp,
    )
}

// ─── FullScreenLoader ─────────────────────────────────────────────────────────

@Composable
fun FullScreenLoader(modifier: Modifier = Modifier) {
    Box(
        modifier         = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        FtSpinner(SpinnerSize.Large)
    }
}

// ─── Shimmer primitives ───────────────────────────────────────────────────────
// Uses indigo-tinted shimmer (primaryContainer shade) for design consistency.

@Composable
private fun shimmerBrush(): Brush {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue   = 0f,
        targetValue    = 1400f,
        animationSpec  = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
        ),
        label = "shimmerTranslate",
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start  = Offset(translateAnim - 700f, 0f),
        end    = Offset(translateAnim, 0f),
    )
}

// ─── SkeletonCard ─────────────────────────────────────────────────────────────

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(FtShape.Card)
            .background(brush),
    )
}

// ─── SkeletonListItem ─────────────────────────────────────────────────────────

@Composable
fun SkeletonListItem(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.SM),
        horizontalArrangement = Arrangement.spacedBy(Spacing.MD),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(FtShape.Pill)
                .background(brush),
        )
        Column(
            modifier              = Modifier.weight(1f),
            verticalArrangement   = Arrangement.spacedBy(Spacing.XS),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(14.dp)
                    .clip(FtShape.CardSmall)
                    .background(brush),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(10.dp)
                    .clip(FtShape.CardSmall)
                    .background(brush),
            )
        }
    }
}
