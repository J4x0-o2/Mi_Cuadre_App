package com.micuadre.app.presentation.expenses

import com.micuadre.app.domain.model.Deduction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftExpensesTest {

    private val fuel = Deduction(id = 1L, name = "Gasolina", usualAmountMinor = 1_500_000L)
    private val food = Deduction(id = 2L, name = "Almuerzo")

    @Test
    fun `fields are prefilled with the usual amount and empty without one`() {
        val state = ShiftExpensesState.of(listOf(fuel, food))

        assertEquals(
            listOf(ExpenseField(1L, "Gasolina", "15.000"), ExpenseField(2L, "Almuerzo", "")),
            state.fields
        )
    }

    @Test
    fun `typing formats the digits and only touches its own field`() {
        val state = ShiftExpensesState.of(listOf(fuel, food)).withText(2L, "8a500")

        assertEquals("8.500", state.fields[1].text)
        assertEquals("15.000", state.fields[0].text)
    }

    @Test
    fun `editing an unknown deduction changes nothing`() {
        val state = ShiftExpensesState.of(listOf(fuel))

        assertEquals(state, state.withText(99L, "1"))
    }

    @Test
    fun `amounts are minor units and blank or zero fields are left out`() {
        val state = ShiftExpensesState.of(listOf(fuel, food))
            .withText(1L, "")
            .withText(2L, "0")

        assertTrue(state.amounts().isEmpty())
        assertEquals(mapOf(2L to 850_000L), state.withText(2L, "8500").amounts())
    }

    @Test
    fun `totals follow the fields live and blanks count as zero`() {
        val state = ShiftExpensesState.of(listOf(fuel, food))

        val prefilled = ShiftExpensesModel.of(state, grossMinor = 10_000_000L)
        assertEquals(1_500_000L, prefilled.totalMinor)
        assertEquals(8_500_000L, prefilled.netMinor)

        val typed = ShiftExpensesModel.of(state.withText(2L, "8500"), grossMinor = 10_000_000L)
        assertEquals(2_350_000L, typed.totalMinor)
        assertEquals(7_650_000L, typed.netMinor)
    }

    @Test
    fun `labels show the minus sign on the total and a negative net honestly`() {
        val state = ShiftExpensesState.of(listOf(fuel))

        val positive = ShiftExpensesModel.of(state, grossMinor = 2_000_000L)
        assertEquals("$ 20.000", positive.grossLabel)
        assertEquals("− $ 15.000", positive.totalLabel)
        assertEquals("$ 5.000", positive.netLabel)

        val negative = ShiftExpensesModel.of(state, grossMinor = 1_000_000L)
        assertEquals(-500_000L, negative.netMinor)
        assertEquals("− $ 5.000", negative.netLabel)
    }

    @Test
    fun `no expenses shows a zero total and the full gross as net`() {
        val model = ShiftExpensesModel.of(ShiftExpensesState.of(listOf(food)), grossMinor = 2_000_000L)

        assertEquals("$ 0", model.totalLabel)
        assertEquals("$ 20.000", model.netLabel)
    }
}
