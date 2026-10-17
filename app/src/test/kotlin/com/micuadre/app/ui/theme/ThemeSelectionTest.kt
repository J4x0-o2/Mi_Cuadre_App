package com.micuadre.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemeSelectionTest {

    @Test
    fun darkFlagSelectsDarkPalette() {
        assertEquals(DarkColorScheme, colorSchemeFor(darkTheme = true))
        assertEquals(DarkMiCuadreColors, extendedColorsFor(darkTheme = true))
    }

    @Test
    fun lightFlagSelectsLightPalette() {
        assertEquals(LightColorScheme, colorSchemeFor(darkTheme = false))
        assertEquals(LightMiCuadreColors, extendedColorsFor(darkTheme = false))
    }

    @Test
    fun palettesDiffer() {
        assertNotEquals(colorSchemeFor(true).background, colorSchemeFor(false).background)
    }
}
