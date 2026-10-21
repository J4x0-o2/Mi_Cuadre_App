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

    /** [currentAmount] is the usual amount as the field shows it ("15.000"), empty when none. */
    data class Edit(
        val id: Long,
        val currentName: String,
        val currentAmount: String,
        val error: DeductionFormError? = null
    ) : DeductionDialog

    data class Delete(val id: Long, val name: String) : DeductionDialog
}

data class DeductionsUiState(
    val deductions: List<Deduction> = emptyList(),
    val dialog: DeductionDialog? = null,
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

    val state: StateFlow<DeductionsUiState> = combine(repository.observeActive(), dialog) { deductions, dialog ->
        DeductionsUiState(deductions = deductions, dialog = dialog, isLoading = false)
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
        dialog.value = DeductionDialog.Add()
    }

    fun onEditClick(id: Long) {
        val deduction = state.value.deductions.firstOrNull { it.id == id } ?: return
        dialog.value = DeductionDialog.Edit(
            id = deduction.id,
            currentName = deduction.name,
            currentAmount = deduction.usualAmountMinor?.let { AmountInput.format((it / 100L).toString()) }.orEmpty()
        )
    }

    fun onDeleteClick(id: Long) {
        val deduction = state.value.deductions.firstOrNull { it.id == id } ?: return
        dialog.value = DeductionDialog.Delete(id = deduction.id, name = deduction.name)
    }

    fun onDismissDialog() {
        dialog.value = null
    }

    /** The user is typing again: the previous inline error no longer applies. */
    fun onFormEdited() {
        dialog.update {
            when (it) {
                is DeductionDialog.Add -> it.copy(error = null)
                is DeductionDialog.Edit -> it.copy(error = null)
                else -> it
            }
        }
    }

    /** [amount] is the field text; empty or zero means no usual amount. */
    fun onConfirmAdd(name: String, amount: String) {
        if (dialog.value !is DeductionDialog.Add) return
        viewModelScope.launch {
            val result = write { repository.create(name, AmountInput.toMinor(amount)) } ?: return@launch
            settle(result) { DeductionDialog.Add(error = it) }
        }
    }

    fun onConfirmEdit(name: String, amount: String) {
        val current = dialog.value as? DeductionDialog.Edit ?: return
        viewModelScope.launch {
            // The amount is only written once the name was accepted, so a refused name changes nothing.
            val result = write {
                val renamed = repository.rename(current.id, name)
                if (renamed == DeductionResult.Ok) {
                    repository.setUsualAmount(current.id, AmountInput.toMinor(amount))
                } else {
                    renamed
                }
            } ?: return@launch
            settle(result) { current.copy(error = it) }
        }
    }

    fun onConfirmDelete() {
        val current = dialog.value as? DeductionDialog.Delete ?: return
        viewModelScope.launch {
            // Already deleted elsewhere is as good as deleted: either way the dialog is done.
            write { repository.delete(current.id) }
            dialog.value = null
        }
    }

    /** Closes the dialog on success, or reopens it with the inline error the result maps to. */
    private fun settle(result: DeductionResult, withError: (DeductionFormError) -> DeductionDialog) {
        dialog.value = when (result) {
            DeductionResult.Blank -> withError(DeductionFormError.Blank)
            DeductionResult.TooLong -> withError(DeductionFormError.TooLong)
            DeductionResult.Duplicate -> withError(DeductionFormError.Duplicate)
            DeductionResult.InvalidAmount -> withError(DeductionFormError.InvalidAmount)
            // NotFound: the deduction vanished while the dialog was open, nothing left to edit.
            DeductionResult.Ok, DeductionResult.NotFound -> null
        }
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
