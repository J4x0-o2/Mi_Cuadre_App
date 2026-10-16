package com.micuadre.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Primary,
    onSecondary = OnPrimary,
    tertiary = Success,
    onTertiary = OnPrimary,
    error = Danger,
    onError = OnPrimary,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline
)

@Composable
fun MiCuadreTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // MVP is light-only by design (the design tokens define a light palette).
    // The parameter is kept for future extension without changing call sites.
    val colors = LightColors
    @Suppress("UNUSED_VARIABLE")
    val dark = useDarkTheme
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
