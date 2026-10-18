package com.micuadre.app.domain.model

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Pure rules deciding the effective dark flag and when a scheduled mode flips. */
object ThemeSchedule {
    val DARK_START: LocalTime = LocalTime.of(18, 0)
    val LIGHT_START: LocalTime = LocalTime.of(6, 0)

    /** Dark in [18:00, 24:00) and [00:00, 06:00). */
    private fun isScheduledDark(time: LocalTime): Boolean =
        time >= DARK_START || time < LIGHT_START

    fun isDark(mode: ThemeMode, systemDark: Boolean, time: LocalTime): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SCHEDULED -> isScheduledDark(time)
    }

    /** First 06:00 or 18:00 strictly after [now]: exactly on a boundary it is the following one. */
    fun nextSwitch(now: ZonedDateTime): ZonedDateTime {
        val today = now.toLocalDate()
        return (0L..1L).asSequence()
            .flatMap { offset ->
                val date = today.plusDays(offset)
                sequenceOf(LIGHT_START, DARK_START).map { date.atTime(it).atZone(now.zone) }
            }
            .first { it.isAfter(now) }
    }

    /** [isDark] evaluated at an epoch-millis instant in [zone]. */
    fun isDarkAt(mode: ThemeMode, systemDark: Boolean, nowMs: Long, zone: ZoneId): Boolean =
        isDark(mode, systemDark, Instant.ofEpochMilli(nowMs).atZone(zone).toLocalTime())

    /**
     * Milliseconds to wait before the effective theme can change, or null when [mode] never
     * changes with the time of day (no timer needed).
     */
    fun delayUntilNextSwitchMs(mode: ThemeMode, nowMs: Long, zone: ZoneId): Long? {
        if (mode != ThemeMode.SCHEDULED) return null
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        return nextSwitch(now).toInstant().toEpochMilli() - nowMs
    }
}
