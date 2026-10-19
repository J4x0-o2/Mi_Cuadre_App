package com.micuadre.app.presentation.home

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
