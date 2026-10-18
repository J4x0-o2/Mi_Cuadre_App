package com.micuadre.app.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyShapesTest {

    private val density = Density(1f)

    private fun radius(shape: androidx.compose.ui.graphics.Shape): Float {
        val corner: CornerSize = (shape as RoundedCornerShape).topStart
        return corner.toPx(androidx.compose.ui.geometry.Size(1000f, 1000f), density)
    }

    @Test
    fun shapesUseSpecifiedRadii() {
        assertEquals(12f, radius(AppShapes.extraSmall))
        assertEquals(16f, radius(AppShapes.small))
        assertEquals(20f, radius(AppShapes.medium))
        assertEquals(28f, radius(AppShapes.large))
        assertEquals(32f, radius(AppShapes.extraLarge))
    }

    @Test
    fun moneyAndTimerStylesUseTabularFigures() {
        val t = DefaultMiCuadreTypography
        listOf(t.moneyLarge, t.money, t.moneySmall, t.timer).forEach {
            assertTrue("tnum expected in $it", it.fontFeatureSettings?.contains("tnum") == true)
        }
    }

    @Test
    fun headingsUseSoraAndBodyUsesManrope() {
        assertNotEquals(AppTypography.displayLarge.fontFamily, AppTypography.bodyMedium.fontFamily)
        assertEquals(SoraFamily, AppTypography.displayLarge.fontFamily)
        assertEquals(SoraFamily, AppTypography.headlineMedium.fontFamily)
        assertEquals(ManropeFamily, AppTypography.titleMedium.fontFamily)
        assertEquals(ManropeFamily, AppTypography.bodyMedium.fontFamily)
        assertEquals(ManropeFamily, AppTypography.labelLarge.fontFamily)
        assertEquals(SoraFamily, DefaultMiCuadreTypography.moneyLarge.fontFamily)
    }
}
