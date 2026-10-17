package com.micuadre.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ColorContrastTest {

    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun check(name: String, fg: Color, bg: Color, min: Double, failures: MutableList<String>) {
        val ratio = contrast(fg, bg)
        if (ratio < min) failures += "$name: ${"%.2f".format(ratio)} < $min"
    }

    private fun verify(label: String, s: ColorScheme, x: MiCuadreColors) {
        val f = mutableListOf<String>()
        val text = 4.5
        val large = 3.0
        check("onBackground/background", s.onBackground, s.background, text, f)
        check("onSurface/surface", s.onSurface, s.surface, text, f)
        check("onSurfaceVariant/surface", s.onSurfaceVariant, s.surface, text, f)
        check("onSurfaceVariant/surfaceVariant", s.onSurfaceVariant, s.surfaceVariant, text, f)
        check("onSurfaceVariant/background", s.onSurfaceVariant, s.background, text, f)
        check("onPrimary/primary", s.onPrimary, s.primary, text, f)
        check("onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer, text, f)
        check("onSecondary/secondary", s.onSecondary, s.secondary, text, f)
        check("onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer, text, f)
        check("onTertiary/tertiary", s.onTertiary, s.tertiary, text, f)
        check("onTertiaryContainer/tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer, text, f)
        check("onError/error", s.onError, s.error, text, f)
        check("onErrorContainer/errorContainer", s.onErrorContainer, s.errorContainer, text, f)
        check("inverseOnSurface/inverseSurface", s.inverseOnSurface, s.inverseSurface, text, f)
        check("onSurface/surfaceContainerHighest", s.onSurface, s.surfaceContainerHighest, text, f)
        check("gold onContainer/container", x.onGoldContainer, x.goldContainer, text, f)
        check("money/background", x.money, s.background, text, f)
        check("money/surface", x.money, s.surface, text, f)
        // Large text / non-text (decorative outlines are exempt from WCAG 1.4.11)
        check("primary/background", s.primary, s.background, large, f)
        check("primary/surface", s.primary, s.surface, large, f)
        check("gold/background", x.gold, s.background, large, f)
        check("gold/surface", x.gold, s.surface, large, f)
        check("error/background", s.error, s.background, large, f)
        assertTrue("$label contrast failures: $f", f.isEmpty())
    }

    @Test
    fun darkSchemeMeetsWcagContrast() = verify("dark", DarkColorScheme, DarkMiCuadreColors)

    @Test
    fun lightSchemeMeetsWcagContrast() = verify("light", LightColorScheme, LightMiCuadreColors)
}
