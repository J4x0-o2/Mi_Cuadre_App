package com.micuadre.app.presentation

import androidx.annotation.StringRes

/**
 * One-shot message for the UI. The [id] is unique per emission so the same
 * text published twice in a row is still treated as a new event.
 */
data class UiMessage(val id: Long, @StringRes val textRes: Int)
