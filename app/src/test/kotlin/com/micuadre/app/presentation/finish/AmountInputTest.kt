package com.micuadre.app.presentation.finish

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountInputTest {

    @Test
    fun `short amounts have no separator`() {
        assertEquals("850", AmountInput.format("850"))
    }

    @Test
    fun `thousands are grouped with a dot`() {
        assertEquals("18.500", AmountInput.format("18500"))
        assertEquals("1.250.000", AmountInput.format("1250000"))
    }

    @Test
    fun `reformatting an already formatted value is stable`() {
        assertEquals("18.500", AmountInput.format("18.500"))
    }

    @Test
    fun `grouping is recomputed when a digit is appended to a formatted value`() {
        assertEquals("185.000", AmountInput.format("18.5000"))
    }

    @Test
    fun `non digit characters are dropped`() {
        assertEquals("1.200", AmountInput.format("\$ 1,2a00"))
    }

    @Test
    fun `leading zeros are dropped`() {
        assertEquals("5", AmountInput.format("0005"))
        assertEquals("", AmountInput.format("000"))
    }

    @Test
    fun `input is capped so it can never overflow minor units`() {
        assertEquals("999.999.999", AmountInput.format("99999999999999"))
    }

    @Test
    fun `empty input stays empty`() {
        assertEquals("", AmountInput.format(""))
    }

    @Test
    fun `formatted pesos convert to minor units`() {
        assertEquals(1_850_000L, AmountInput.toMinor("18.500"))
        assertEquals(85_000L, AmountInput.toMinor("850"))
    }

    @Test
    fun `empty or zero amounts convert to null`() {
        assertNull(AmountInput.toMinor(""))
        assertNull(AmountInput.toMinor("0"))
    }
}
