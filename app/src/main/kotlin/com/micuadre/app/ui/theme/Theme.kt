package com.micuadre.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** Accessor for the tokens that Material 3 has no role for. */
object MiCuadreTheme {
    val colors: MiCuadreColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMiCuadreColors.current

    val typography: MiCuadreTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalMiCuadreTypography.current
}

internal fun colorSchemeFor(darkTheme: Boolean): ColorScheme =
    if (darkTheme) DarkColorScheme else LightColorScheme

internal fun extendedColorsFor(darkTheme: Boolean): MiCuadreColors =
    if (darkTheme) DarkMiCuadreColors else LightMiCuadreColors

/**
 * Follows the system dark/light setting by default. Dynamic color is intentionally off:
 * the brand palette is fixed.
 */
@Composable
fun MiCuadreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalMiCuadreColors provides extendedColorsFor(darkTheme),
        LocalMiCuadreTypography provides DefaultMiCuadreTypography
    ) {
        MaterialTheme(
            colorScheme = colorSchemeFor(darkTheme),
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
