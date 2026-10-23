package com.micuadre.app.presentation.deductions

import com.micuadre.app.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeductionToggleA11yTest {

    @Test
    fun `visible and hidden deductions announce different states`() {
        assertEquals(R.string.deductions_toggle_state_visible, DeductionToggleA11y.stateRes(visible = true))
        assertEquals(R.string.deductions_toggle_state_hidden, DeductionToggleA11y.stateRes(visible = false))
        assertNotEquals(DeductionToggleA11y.stateRes(true), DeductionToggleA11y.stateRes(false))
    }

    @Test
    fun `label is a single resource that takes the deduction name`() {
        assertEquals(R.string.deductions_toggle_cd, DeductionToggleA11y.LABEL_RES)
        val strings = listOf("src/main/res/values/strings.xml", "app/src/main/res/values/strings.xml")
            .map(::File).first { it.isFile }.readText()
        val label = Regex("""<string name="deductions_toggle_cd">([^<]*)</string>""").find(strings)?.groupValues?.get(1)
        assertTrue("label must contain the deduction name placeholder", label?.contains("%1\$s") == true)
    }
}
