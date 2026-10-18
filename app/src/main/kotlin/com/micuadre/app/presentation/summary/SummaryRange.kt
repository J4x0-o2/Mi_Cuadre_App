package com.micuadre.app.presentation.summary

import com.micuadre.app.util.TimeUtils
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How the shift's time range is shown on the summary. Labels are formatted; copy lives in strings.xml. */
sealed interface SummaryRange {
    /** The session row is gone (or its timestamps are unusable): nothing meaningful to show. */
    data object Unknown : SummaryRange

    /** Started and ended today: "Hoy · 08:00 – 10:05". */
    data class Today(val start: String, val end: String) : SummaryRange

    /** Started and ended the same past day: "17 oct · 08:00 – 10:05". */
    data class SameDay(val date: String, val start: String, val end: String) : SummaryRange

    /** Crossed midnight: "17 oct 22:00 – 18 oct 02:00". */
    data class Span(
        val startDate: String,
        val startTime: String,
        val endDate: String,
        val endTime: String
    ) : SummaryRange

    companion object {
        private val spanish = Locale.forLanguageTag("es")
        private val dateFormat = DateTimeFormatter.ofPattern("d MMM", spanish)
        private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

        /**
         * Range of a shift from [startMs] to [endMs] (null = still open, ends at [nowMs]).
         * A missing or implausible start (legacy data, deleted session) is [Unknown].
         */
        fun of(startMs: Long?, endMs: Long?, nowMs: Long, zone: ZoneId): SummaryRange {
            if (startMs == null || !TimeUtils.isPlausibleEpoch(startMs)) return Unknown
            val start = Instant.ofEpochMilli(startMs).atZone(zone)
            val end = Instant.ofEpochMilli(maxOf(endMs ?: nowMs, startMs)).atZone(zone)
            val startTime = clockFormat.format(start)
            val endTime = clockFormat.format(end)
            return when {
                start.toLocalDate() != end.toLocalDate() ->
                    Span(dateLabel(start), startTime, dateLabel(end), endTime)
                start.toLocalDate() == Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate() ->
                    Today(startTime, endTime)
                else -> SameDay(dateLabel(start), startTime, endTime)
            }
        }

        // Some JDK/ICU versions append a dot to abbreviated months ("oct."); the copy drops it.
        private fun dateLabel(date: ZonedDateTime): String = dateFormat.format(date).trimEnd('.')
    }
}
