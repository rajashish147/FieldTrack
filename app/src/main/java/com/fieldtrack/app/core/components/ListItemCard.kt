package com.fieldtrack.app.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.fieldtrack.app.core.designsystem.*

// ─── ListItemCard ─────────────────────────────────────────────────────────────
// Standard list item with 48dp min height, 16dp horizontal padding.
// Used for employee lists, session lists, expense lists.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ListItemCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.MinTouchTarget)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = Spacing.MD, vertical = Spacing.SM + 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.MD),
    ) {
        // Leading slot (avatar, icon, etc.)
        leading?.invoke()

        // Text block
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = FtTypography.LabelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = FtTypography.BodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Trailing slot (badge, arrow, amount)
        trailing?.invoke()
    }
}

// ─── SectionContainer ─────────────────────────────────────────────────────────
// Wraps a group of list items inside a card with an optional section heading.

@Composable
fun SectionContainer(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.SM),
    ) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                style = FtTypography.LabelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.XS),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FtShape.Card)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            content = content,
        )
    }
}

// ─── EmptyState ───────────────────────────────────────────────────────────────

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.XXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.MD),
    ) {
        icon?.invoke()
        Text(
            text = message,
            style = FtTypography.BodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.invoke()
    }
}

// ─── ErrorState ───────────────────────────────────────────────────────────────

@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.XXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.MD),
    ) {
        Text(
            text = message,
            style = FtTypography.BodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        if (onRetry != null) {
            SecondaryButton(text = "Retry", onClick = onRetry)
        }
    }
}
