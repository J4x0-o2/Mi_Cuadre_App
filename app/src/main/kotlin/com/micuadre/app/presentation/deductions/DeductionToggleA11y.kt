package com.micuadre.app.presentation.deductions

import androidx.annotation.StringRes
import com.micuadre.app.R

/** TalkBack texts of the show/hide switch of a deduction: a stable label plus the current state. */
object DeductionToggleA11y {

    /** Takes the deduction name as `%1$s`. */
    @StringRes
    val LABEL_RES: Int = R.string.deductions_toggle_cd

    @StringRes
    fun stateRes(visible: Boolean): Int =
        if (visible) R.string.deductions_toggle_state_visible else R.string.deductions_toggle_state_hidden
}
