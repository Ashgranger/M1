package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ZoneZeroColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = VoidObsidian,
    primaryContainer = NeonCyanDark,
    onPrimaryContainer = TextPrimary,
    secondary = PlasmaMagenta,
    onSecondary = TextPrimary,
    secondaryContainer = StormViolet,
    onSecondaryContainer = TextPrimary,
    tertiary = LegendaryAmber,
    onTertiary = VoidObsidian,
    background = VoidObsidian,
    onBackground = TextPrimary,
    surface = SlateArmor,
    onSurface = TextPrimary,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder,
    error = CrimsonDanger,
    onError = TextPrimary
)

@Composable
fun ZoneZeroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZoneZeroColorScheme,
        typography = ZoneZeroTypography,
        content = content
    )
}
