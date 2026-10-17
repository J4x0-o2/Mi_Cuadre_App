package com.micuadre.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
}

@Composable
fun MiCuadreTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Scheme switching is wired in the theme-wiring task; light only until then.
    @Suppress("UNUSED_VARIABLE")
    val dark = useDarkTheme
    CompositionLocalProvider(LocalMiCuadreColors provides LightMiCuadreColors) {
        MaterialTheme(
            colorScheme = LightColorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
