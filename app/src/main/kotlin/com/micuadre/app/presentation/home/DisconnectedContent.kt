package com.micuadre.app.presentation.home

import com.micuadre.app.presentation.HomeUiState

/** Which block the disconnected Home shows. */
enum class DisconnectedContent {
    /** Nothing recorded yet today and no open shift. */
    WELCOME,

    /** "Resumen de hoy": every shift started today. */
    TODAY,

    /**
     * A paused shift that started on a previous day (e.g. a night shift paused after midnight).
     * Today's summary would read zero, so the shift's own totals are shown instead.
     */
    OPEN_SHIFT;

    companion object {
        /** [todaySessions] is null until today's summary has loaded. */
        fun choose(hasOpenShift: Boolean, todaySessions: Int?): DisconnectedContent = when {
            hasOpenShift && todaySessions == 0 -> OPEN_SHIFT
            hasOpenShift || (todaySessions ?: 0) > 0 -> TODAY
            else -> WELCOME
        }
    }
}

/** What the disconnected Home renders: the block, its figures and, for [OPEN_SHIFT], the start. */
data class DisconnectedModel(
    val content: DisconnectedContent,
    val figures: TodaySummaryModel,
    val openShiftStartMs: Long?
) {
    companion object {
        fun of(ui: HomeUiState): DisconnectedModel {
            val content = DisconnectedContent.choose(ui.hasOpenShift, ui.today?.sessionCount)
            return if (content == DisconnectedContent.OPEN_SHIFT) {
                DisconnectedModel(
                    content = content,
                    figures = TodaySummaryModel.ofOpenShift(
                        DayBreakdown.from(ui.trips),
                        ui.connectedIntervals,
                        ui.nowMs
                    ),
                    openShiftStartMs = shiftStartMs(ui.shiftState, ui.connectedIntervals)
                )
            } else {
                DisconnectedModel(
                    content = content,
                    figures = ui.today?.let(TodaySummaryModel::from) ?: TodaySummaryModel.EMPTY,
                    openShiftStartMs = null
                )
            }
        }
    }
}
