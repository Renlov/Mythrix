package com.pimenov.uikit.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MythrixColors = darkColorScheme(
    primary = Burgundy,
    onPrimary = Parchment,
    primaryContainer = BurgundyDark,
    onPrimaryContainer = Parchment,
    secondary = Gold,
    onSecondary = DeepNavyDark,
    secondaryContainer = GoldSoft,
    onSecondaryContainer = DeepNavyDark,
    tertiary = Ember,
    background = DeepNavy,
    onBackground = Parchment,
    surface = DeepNavyDark,
    onSurface = Parchment,
    surfaceVariant = DeepNavy,
    onSurfaceVariant = ParchmentDim,
    outline = Gold
)

private val MythrixTypography = Typography()

@Composable
fun MythrixTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MythrixColors,
        typography = MythrixTypography,
        content = content
    )
}
