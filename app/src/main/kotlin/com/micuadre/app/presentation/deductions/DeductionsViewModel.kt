package com.micuadre.app.presentation.deductions

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micuadre.app.R
import com.micuadre.app.domain.model.Deduction
import com.micuadre.app.domain.repository.DeductionRepository
import com.micuadre.app.domain.repository.DeductionResult
import com.micuadre.app.presentation.UiMessage
import com.micuadre.app.presentation.finish.AmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/** Why a typed deduction name or usual amount was refused, shown inline in the dialog. */
enum class DeductionFormError { Blank, TooLong, Duplicate, InvalidAmount }

/** The dialog currently open over the deduction list, if any. */
sealed interface DeductionDialog {
    data class Add(val error: DeductionFormError? = null) : DeductionDialog

    /**
     * [currentAmount] is the usual amount as the field shows it ("15.000"), empty when none. The
     * field only holds whole pesos, so [usualAmountMinor] keeps the exact stored value: confirming
     * with the field untouched writes it back instead of the truncated text.
     */
    data class Edit(
        val id: Long,
        val currentName: String,
        val currentAmount: String,
        val usualAmountMinor: Long? = null,
        val error: DeductionFormError? = null
    ) : DeductionDialog

    data class Delete(val id: Long, val name: String) : DeductionDialog
}

data class DeductionsUiState(
    val deductions: List<Deduction> = emptyList(),
    val dialog: DeductionDialog? = null,
    /** A dialog write is in flight: further confirms are ignored and the button can be disabled. */
    val saving: Boolean = false,
    val isLoading: Boolean = true
)

/** What a row says under the deduction name. */
sealed interface DeductionSecondary {
    data class Usual(val amountMinor: Long) : DeductionSecondary
    data object NoUsual : DeductionSecondary
    data object Hidden : DeductionSecondary
}

/** Hidden wins over a usual amount: the row must say the deduction is not asked. */
fun Deduction.secondaryLine(): DeductionSecondary = when {
    !visible -> DeductionSecondary.Hidden
    usualAmountMinor != null -> DeductionSecondary.Usual(usualAmountMinor)
    else -> DeductionSecondary.NoUsual
}

@HiltViewModel
class DeductionsViewModel @Inject constructor(
    private val repository: DeductionRepository
) : ViewModel() {

    private val dialog = MutableStateFlow<DeductionDialog?>(null)

    private val saving = MutableStateFlow(false)

    /**
     * Identity of the dialog opening currently shown. Every opening gets a fresh id, so a write
     * result can tell its own dialog from an equal one opened later, whatever the user typed since.
     */
    private var openSeq = 0L
    private var openId = 0L

    private fun open(next: DeductionDialog) {
        openId = ++openSeq
        dialog.value = next
    }

    val state: StateFlow<DeductionsUiState> = combine(repository.observeActive(), dialog, saving) { deductions, dialog, saving ->
        DeductionsUiState(deductions = deductions, dialog = dialog, saving = saving, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DeductionsUiState())

    private val _message = MutableStateFlow<UiMessage?>(null)

    /** One-shot message (e.g. a failed write); clear it with [onMessageShown]. */
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private var messageSeq = 0L

    fun onMessageShown(id: Long) {
        _message.update { if (it?.id == id) null else it }
    }

    fun onToggleVisible(id: Long, visible: Boolean) {
        viewModelScope.launch { write { repository.setVisible(id, visible) } }
    }

    fun onAddClick() {
        open(DeductionDialog.Add())
    }

    fun onEditClick(id: Long) {
        val deduction = state.value.deductions.firstOrNull { it.id == id } ?: return
        open(DeductionDialog.Edit(
            id = deduction.id,
            currentName = deduction.name,
            currentAmount = deduction.usualAmountMinor?.let { AmountInput.format((it / 100L).toString()) }.orEmpty(),
            usualAmountMinor = deduction.usualAmountMinor
        ))
    }

    fun onDeleteClick(id: Long) {
        val deduction = state.value.deductions.firstOrNull { it.id == id } ?: return
        open(DeductionDialog.Delete(id = deduction.id, name = deduction.name))
    }

    fun onDismissDialog() {
        dialog.value = null
    }

    /** The user is typing again: the previous inline error no longer applies. */
    fun onFormEdited() {
        // Keeps the same instance when there is no error, so a write in flight still finds its dialog.
        dialog.update {
            when {
                it is DeductionDialog.Add && it.error != null -> it.copy(error = null)
                it is DeductionDialog.Edit && it.error != null -> it.copy(error = null)
                else -> it
            }
        }
    }

    /** [amount] is the field text; empty or zero means no usual amount. */
    fun onConfirmAdd(name: String, amount: String) {
        val current = dialog.value as? DeductionDialog.Add ?: return
        if (!saving.compareAndSet(expect = false, update = true)) return
        val id = openId
        viewModelScope.launch {
            try {
                val result = write { repository.create(name, AmountInput.toMinor(amount)) } ?: return@launch
                settle(id, result) { dialog, error -> (dialog as? DeductionDialog.Add)?.copy(error = error) ?: dialog }
            } finally {
                saving.value = false
            }
        }
    }

    fun onConfirmEdit(name: String, amount: String) {
        val current = dialog.value as? DeductionDialog.Edit ?: return
        if (!saving.compareAndSet(expect = false, update = true)) return
        val id = openId
        viewModelScope.launch {
            try {
                // The field holds whole pesos only: an empty field clears, untouched text keeps the exact stored amount.
                val usual = when {
                    amount.isEmpty() -> null
                    amount == current.currentAmount -> current.usualAmountMinor
                    else -> AmountInput.toMinor(amount)
                }
                val result = write { repository.update(current.id, name, usual) } ?: return@launch
                settle(id, result) { dialog, error -> (dialog as? DeductionDialog.Edit)?.copy(error = error) ?: dialog }
            } finally {
                saving.value = false
            }
        }
    }

    fun onConfirmDelete() {
        val current = dialog.value as? DeductionDialog.Delete ?: return
        if (!saving.compareAndSet(expect = false, update = true)) return
        val id = openId
        viewModelScope.launch {
            try {
                // Already deleted elsewhere is as good as deleted: either way the dialog is done.
                write { repository.delete(current.id) }
                closeIfStillOpen(id)
            } finally {
                saving.value = false
            }
        }
    }

    /**
     * Closes the dialog on success, or shows the inline error the result maps to on the CURRENT
     * dialog (keeping what the user typed since). Does nothing unless the dialog opening [id] is
     * still the one open: a dismissed or reopened dialog is never touched by a stale result.
     */
    private fun settle(
        id: Long,
        result: DeductionResult,
        withError: (DeductionDialog, DeductionFormError) -> DeductionDialog
    ) {
        val error = when (result) {
            DeductionResult.Blank -> DeductionFormError.Blank
            DeductionResult.TooLong -> DeductionFormError.TooLong
            DeductionResult.Duplicate -> DeductionFormError.Duplicate
            DeductionResult.InvalidAmount -> DeductionFormError.InvalidAmount
            // NotFound: the deduction vanished while the dialog was open, nothing left to edit.
            DeductionResult.Ok, DeductionResult.NotFound -> null
        }
        if (id != openId) return
        if (error == null) dialog.value = null else dialog.update { current -> current?.let { withError(it, error) } }
    }

    private fun closeIfStillOpen(id: Long) {
        if (id == openId) dialog.value = null
    }

    /** Runs a repository write; on a storage failure posts a message, closes any dialog, returns null. */
    private suspend fun write(block: suspend () -> DeductionResult): DeductionResult? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            failed()
        } catch (e: SQLiteException) {
            failed()
        }

    private fun failed(): DeductionResult? {
        dialog.value = null
        _message.value = UiMessage(id = ++messageSeq, textRes = R.string.deductions_save_error)
        return null
    }
}
