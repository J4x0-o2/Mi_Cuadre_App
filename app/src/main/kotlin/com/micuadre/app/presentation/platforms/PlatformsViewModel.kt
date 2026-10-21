package com.micuadre.app.presentation.platforms

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micuadre.app.R
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.repository.PlatformRepository
import com.micuadre.app.domain.repository.PlatformResult
import com.micuadre.app.presentation.UiMessage
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

/** Why a typed platform name was refused, shown inline in the dialog. */
enum class PlatformNameError { Blank, TooLong, Duplicate }

/** The dialog currently open over the platform list, if any. */
sealed interface PlatformDialog {
    data class Add(val error: PlatformNameError? = null) : PlatformDialog

    data class Rename(
        val id: Long,
        val currentName: String,
        val error: PlatformNameError? = null
    ) : PlatformDialog

    data class Delete(val id: Long, val name: String) : PlatformDialog
}

data class PlatformsUiState(
    val platforms: List<Platform> = emptyList(),
    val dialog: PlatformDialog? = null,
    /** A dialog write is in flight: further confirms are ignored and the button can be disabled. */
    val saving: Boolean = false,
    val isLoading: Boolean = true
) {
    /** True when no platform would be offered when finishing a trip (including an empty list). */
    val showNoneVisibleHint: Boolean get() = !isLoading && platforms.none { it.visible }
}

@HiltViewModel
class PlatformsViewModel @Inject constructor(
    private val repository: PlatformRepository
) : ViewModel() {

    private val dialog = MutableStateFlow<PlatformDialog?>(null)

    private val saving = MutableStateFlow(false)

    /**
     * Identity of the dialog opening currently shown. Every opening gets a fresh id, so a write
     * result can tell its own dialog from an equal one opened later, whatever the user typed since.
     */
    private var openSeq = 0L
    private var openId = 0L

    private fun open(next: PlatformDialog) {
        openId = ++openSeq
        dialog.value = next
    }

    val state: StateFlow<PlatformsUiState> = combine(repository.observeActive(), dialog, saving) { platforms, dialog, saving ->
        PlatformsUiState(platforms = platforms, dialog = dialog, saving = saving, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlatformsUiState())

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
        open(PlatformDialog.Add())
    }

    fun onRenameClick(id: Long) {
        val platform = state.value.platforms.firstOrNull { it.id == id } ?: return
        open(PlatformDialog.Rename(id = platform.id, currentName = platform.name))
    }

    fun onDeleteClick(id: Long) {
        val platform = state.value.platforms.firstOrNull { it.id == id } ?: return
        open(PlatformDialog.Delete(id = platform.id, name = platform.name))
    }

    fun onDismissDialog() {
        dialog.value = null
    }

    /** The user is typing again: the previous inline error no longer applies. */
    fun onNameEdited() {
        // Keeps the same instance when there is no error, so a write in flight still finds its dialog.
        dialog.update {
            when {
                it is PlatformDialog.Add && it.error != null -> it.copy(error = null)
                it is PlatformDialog.Rename && it.error != null -> it.copy(error = null)
                else -> it
            }
        }
    }

    fun onConfirmAdd(name: String) {
        val current = dialog.value as? PlatformDialog.Add ?: return
        if (!saving.compareAndSet(expect = false, update = true)) return
        val id = openId
        viewModelScope.launch {
            try {
                val result = write { repository.create(name) } ?: return@launch
                settle(id, result) { dialog, error -> (dialog as? PlatformDialog.Add)?.copy(error = error) ?: dialog }
            } finally {
                saving.value = false
            }
        }
    }

    fun onConfirmRename(name: String) {
        val current = dialog.value as? PlatformDialog.Rename ?: return
        if (!saving.compareAndSet(expect = false, update = true)) return
        val id = openId
        viewModelScope.launch {
            try {
                val result = write { repository.rename(current.id, name) } ?: return@launch
                settle(id, result) { dialog, error -> (dialog as? PlatformDialog.Rename)?.copy(error = error) ?: dialog }
            } finally {
                saving.value = false
            }
        }
    }

    fun onConfirmDelete() {
        val current = dialog.value as? PlatformDialog.Delete ?: return
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
        result: PlatformResult,
        withError: (PlatformDialog, PlatformNameError) -> PlatformDialog
    ) {
        val error = when (result) {
            PlatformResult.Blank -> PlatformNameError.Blank
            PlatformResult.TooLong -> PlatformNameError.TooLong
            PlatformResult.Duplicate -> PlatformNameError.Duplicate
            // NotFound: the platform vanished while the dialog was open, nothing left to edit.
            PlatformResult.Ok, PlatformResult.NotFound -> null
        }
        if (id != openId) return
        if (error == null) dialog.value = null else dialog.update { current -> current?.let { withError(it, error) } }
    }

    private fun closeIfStillOpen(id: Long) {
        if (id == openId) dialog.value = null
    }

    /** Runs a repository write; on a storage failure posts a message, closes any dialog, returns null. */
    private suspend fun write(block: suspend () -> PlatformResult): PlatformResult? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            failed()
        } catch (e: SQLiteException) {
            failed()
        }

    private fun failed(): PlatformResult? {
        dialog.value = null
        _message.value = UiMessage(id = ++messageSeq, textRes = R.string.platforms_save_error)
        return null
    }
}
