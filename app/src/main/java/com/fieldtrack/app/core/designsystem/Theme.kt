package com.fieldtrack.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// ─── FieldTrack Material3 Theme (FINAL — Indigo) ──────────────────────────────
// All color roles map to the indigo palette.
// Typography and Shape unchanged.
// ─────────────────────────────────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary                  = LightPrimary,
    onPrimary                = LightOnPrimary,
    primaryContainer         = LightPrimaryContainer,
    onPrimaryContainer       = LightOnPrimaryContainer,
    secondary                = LightSecondary,
    onSecondary              = LightOnSecondary,
    secondaryContainer       = LightSecondaryContainer,
    onSecondaryContainer     = LightOnSecondaryContainer,
    tertiary                 = LightTertiary,
    onTertiary               = LightOnTertiary,
    tertiaryContainer        = LightTertiaryContainer,
    onTertiaryContainer      = LightOnTertiaryContainer,
    error                    = LightError,
    onError                  = LightOnError,
    errorContainer           = LightErrorContainer,
    onErrorContainer         = LightOnErrorContainer,
    background               = LightBackground,
    onBackground             = LightOnBackground,
    surface                  = LightSurface,
    onSurface                = LightOnSurface,
    surfaceVariant           = LightSurfaceContainerHighest,
    onSurfaceVariant         = LightOnSurfaceVariant,
    outline                  = LightOutline,
    outlineVariant           = LightOutlineVariant,
    surfaceContainerLowest   = LightSurfaceContainerLowest,
    surfaceContainerLow      = LightSurfaceContainerLow,
    surfaceContainer         = LightSurfaceContainer,
    surfaceContainerHigh     = LightSurfaceContainerHigh,
    surfaceContainerHighest  = LightSurfaceContainerHighest,
)

private val DarkColorScheme = darkColorScheme(
    primary                  = DarkPrimary,
    onPrimary                = DarkOnPrimary,
    primaryContainer         = DarkPrimaryContainer,
    onPrimaryContainer       = DarkOnPrimaryContainer,
    secondary                = DarkSecondary,
    onSecondary              = DarkOnSecondary,
    secondaryContainer       = DarkSecondaryContainer,
    onSecondaryContainer     = DarkOnSecondaryContainer,
    tertiary                 = DarkTertiary,
    onTertiary               = DarkOnTertiary,
    tertiaryContainer        = DarkTertiaryContainer,
    onTertiaryContainer      = DarkOnTertiaryContainer,
    error                    = DarkError,
    onError                  = DarkOnError,
    errorContainer           = DarkErrorContainer,
    onErrorContainer         = DarkOnErrorContainer,
    background               = DarkBackground,
    onBackground             = DarkOnBackground,
    surface                  = DarkSurface,
    onSurface                = DarkOnSurface,
    surfaceVariant           = DarkSurfaceContainerHighest,
    onSurfaceVariant         = DarkOnSurfaceVariant,
    outline                  = DarkOutline,
    outlineVariant           = DarkOutlineVariant,
    surfaceContainerLowest   = DarkSurfaceContainerLowest,
    surfaceContainerLow      = DarkSurfaceContainerLow,
    surfaceContainer         = DarkSurfaceContainer,
    surfaceContainerHigh     = DarkSurfaceContainerHigh,
    surfaceContainerHighest  = DarkSurfaceContainerHighest,
)

@Composable
fun FieldTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
