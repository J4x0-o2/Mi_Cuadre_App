package com.micuadre.app.presentation.history

import com.micuadre.app.presentation.DayKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Re-subscribes to [observe] each time the local day (date or zone) changes. The history use cases
 * read "today" only when their repository flows emit, so without this a screen kept open across
 * midnight would keep an old "en curso" month, current year and week cutoff.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<DayKey>.switchOnDayChange(observe: (DayKey) -> Flow<T>): Flow<T> = flatMapLatest(observe)
