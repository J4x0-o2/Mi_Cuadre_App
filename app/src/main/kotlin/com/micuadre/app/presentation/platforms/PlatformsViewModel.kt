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
        dialog.value = PlatformDialog.Add()
    }

    fun onRenameClick(id: Long) {
        val platform = state.value.platforms.firstOrNull { it.id == id } ?: return
        dialog.value = PlatformDialog.Rename(id = platform.id, currentName = platform.name)
    }

    fun onDeleteClick(id: Long) {
        val platform = state.value.platforms.firstOrNull { it.id == id } ?: return
        dialog.value = PlatformDialog.Delete(id = platform.id, name = platform.name)
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
        viewModelScope.launch {
            try {
                val result = write { repository.create(name) } ?: return@launch
                settle(current, result) { PlatformDialog.Add(error = it) }
            } finally {
                saving.value = false
            }
        }
    }

    fun onConfirmRename(name: String) {
        val current = dialog.value as? PlatformDialog.Rename ?: return
        if (!saving.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                val result = write { repository.rename(current.id, name) } ?: return@launch
                settle(current, result) { current.copy(error = it) }
            } finally {
                saving.value = false
            }
        }
    }

    fun onConfirmDelete() {
        val current = dialog.value as? PlatformDialog.Delete ?: return
        if (!saving.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                // Already deleted elsewhere is as good as deleted: either way the dialog is done.
                write { repository.delete(current.id) }
                dialog.compareAndSet(current, null)
            } finally {
                saving.value = false
            }
        }
    }

    /**
     * Closes the dialog on success, or reopens it with the inline error the result maps to. Does
     * nothing when the user already dismissed or replaced [opened] while the write was running.
     */
    private fun settle(opened: PlatformDialog, result: PlatformResult, withError: (PlatformNameError) -> PlatformDialog) {
        val next = when (result) {
            PlatformResult.Blank -> withError(PlatformNameError.Blank)
            PlatformResult.TooLong -> withError(PlatformNameError.TooLong)
            PlatformResult.Duplicate -> withError(PlatformNameError.Duplicate)
            // NotFound: the platform vanished while the dialog was open, nothing left to edit.
            PlatformResult.Ok, PlatformResult.NotFound -> null
        }
        dialog.compareAndSet(opened, next)
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
