package com.micuadre.app.presentation.expenses

import com.micuadre.app.domain.model.Deduction
import com.micuadre.app.presentation.finish.AmountInput
import com.micuadre.app.util.Formatters

/** One amount field of the "Gastos del turno" sheet; [text] is the formatted whole-peso input. */
data class ExpenseField(val deductionId: Long, val name: String, val text: String)

/**
 * Fields of the open "Gastos del turno" sheet, in the order of the visible deductions. Blank fields
 * count as zero and are never sent.
 */
data class ShiftExpensesState(val fields: List<ExpenseField>) {

    /** Replaces the text of one field with [raw] filtered to the whole-peso format. */
    fun withText(deductionId: Long, raw: String): ShiftExpensesState {
        if (fields.none { it.deductionId == deductionId }) return this
        val formatted = AmountInput.format(raw)
        return copy(fields = fields.map { if (it.deductionId == deductionId) it.copy(text = formatted) else it })
    }

    /** Positive amounts in minor units by deduction id; blank and zero fields are left out. */
    fun amounts(): Map<Long, Long> = fields.mapNotNull { field ->
        AmountInput.toMinor(field.text)?.let { field.deductionId to it }
    }.toMap()

    companion object {
        /** Prefills every field with the deduction's usual amount, or leaves it blank. */
        fun of(deductions: List<Deduction>) = ShiftExpensesState(
            deductions.map { deduction ->
                val usual = deduction.usualAmountMinor?.let { AmountInput.format((it / 100L).toString()) }.orEmpty()
                ExpenseField(deduction.id, deduction.name, usual)
            }
        )
    }
}

/**
 * Live figures under the fields: what the shift earned, what was spent and what is left. A net below
 * zero is shown as it is, with a minus sign, instead of being clamped to zero.
 */
data class ShiftExpensesModel(val grossMinor: Long, val totalMinor: Long) {

    val netMinor: Long get() = grossMinor - totalMinor

    val grossLabel: String get() = Formatters.moneyNoDecimals(grossMinor)

    /** "− $ X" when something was spent, "$ 0" otherwise. */
    val totalLabel: String get() = Formatters.expenseMoney(totalMinor)

    val netLabel: String
        get() = Formatters.signedMoney(netMinor)

    companion object {
        fun of(state: ShiftExpensesState, grossMinor: Long) =
            ShiftExpensesModel(grossMinor, state.amounts().values.sum())
    }
}
