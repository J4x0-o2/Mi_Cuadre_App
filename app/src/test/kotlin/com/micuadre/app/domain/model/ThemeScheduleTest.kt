package com.micuadre.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ThemeScheduleTest {

    private val zone = ZoneId.of("America/Bogota")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0, s: Int = 0) =
        ZonedDateTime.of(LocalDateTime.of(y, m, d, h, min, s), zone)

    private fun ms(z: ZonedDateTime) = z.toInstant().toEpochMilli()

    @Test
    fun systemFollowsPhone() {
        assertTrue(ThemeSchedule.isDark(ThemeMode.SYSTEM, true, LocalTime.NOON))
        assertFalse(ThemeSchedule.isDark(ThemeMode.SYSTEM, false, LocalTime.MIDNIGHT))
    }

    @Test
    fun forcedModesIgnoreSystemAndTime() {
        assertFalse(ThemeSchedule.isDark(ThemeMode.LIGHT, true, LocalTime.MIDNIGHT))
        assertTrue(ThemeSchedule.isDark(ThemeMode.DARK, false, LocalTime.NOON))
    }

    @Test
    fun scheduledBoundaries() {
        fun dark(h: Int, m: Int, s: Int = 0) =
            ThemeSchedule.isDark(ThemeMode.SCHEDULED, false, LocalTime.of(h, m, s))
        assertTrue(dark(18, 0))
        assertFalse(dark(17, 59, 59))
        assertFalse(dark(6, 0))
        assertTrue(dark(5, 59, 59))
        assertTrue(dark(0, 0))
        assertTrue(dark(23, 59, 59))
        assertFalse(dark(12, 0))
    }

    @Test
    fun scheduledIgnoresSystemDark() {
        assertFalse(ThemeSchedule.isDark(ThemeMode.SCHEDULED, true, LocalTime.NOON))
        assertTrue(ThemeSchedule.isDark(ThemeMode.SCHEDULED, false, LocalTime.of(22, 0)))
    }

    @Test
    fun nextSwitchDuringTheDayIsEvening() {
        assertEquals(at(2026, 10, 17, 18), ThemeSchedule.nextSwitch(at(2026, 10, 17, 9, 30)))
    }

    @Test
    fun nextSwitchAtNightIsMorningAcrossMidnight() {
        assertEquals(at(2026, 10, 18, 6), ThemeSchedule.nextSwitch(at(2026, 10, 17, 23, 59, 59)))
        assertEquals(at(2026, 10, 17, 6), ThemeSchedule.nextSwitch(at(2026, 10, 17, 0, 0)))
        assertEquals(at(2026, 10, 17, 6), ThemeSchedule.nextSwitch(at(2026, 10, 17, 3, 0)))
    }

    @Test
    fun nextSwitchOnExactBoundaryIsTheFollowingOne() {
        assertEquals(at(2026, 10, 18, 6), ThemeSchedule.nextSwitch(at(2026, 10, 17, 18, 0)))
        assertEquals(at(2026, 10, 17, 18), ThemeSchedule.nextSwitch(at(2026, 10, 17, 6, 0)))
    }

    @Test
    fun nextSwitchJustBeforeBoundary() {
        assertEquals(at(2026, 10, 17, 18), ThemeSchedule.nextSwitch(at(2026, 10, 17, 17, 59, 59)))
        assertEquals(at(2026, 10, 17, 6), ThemeSchedule.nextSwitch(at(2026, 10, 17, 5, 59, 59)))
    }

    @Test
    fun nextSwitchAcrossMonthEnd() {
        assertEquals(at(2026, 11, 1, 6), ThemeSchedule.nextSwitch(at(2026, 10, 31, 20, 0)))
    }

    @Test
    fun delayIsNullUnlessScheduled() {
        val now = ms(at(2026, 10, 17, 10))
        assertNull(ThemeSchedule.delayUntilNextSwitchMs(ThemeMode.SYSTEM, now, zone))
        assertNull(ThemeSchedule.delayUntilNextSwitchMs(ThemeMode.LIGHT, now, zone))
        assertNull(ThemeSchedule.delayUntilNextSwitchMs(ThemeMode.DARK, now, zone))
    }

    @Test
    fun delayUntilEveningAndAcrossNight() {
        val hour = 3_600_000L
        assertEquals(8 * hour, ThemeSchedule.delayUntilNextSwitchMs(ThemeMode.SCHEDULED, ms(at(2026, 10, 17, 10)), zone))
        assertEquals(7 * hour, ThemeSchedule.delayUntilNextSwitchMs(ThemeMode.SCHEDULED, ms(at(2026, 10, 17, 23)), zone))
    }

    @Test
    fun delayOnExactBoundaryIsTheFullNextInterval() {
        val hour = 3_600_000L
        assertEquals(12 * hour, ThemeSchedule.delayUntilNextSwitchMs(ThemeMode.SCHEDULED, ms(at(2026, 10, 17, 18)), zone))
    }

    @Test
    fun isDarkAtUsesLocalTimeOfTheZone() {
        val utcInstant = ZonedDateTime.of(LocalDateTime.of(2026, 10, 17, 23, 0), ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        // 23:00 UTC is 18:00 in Bogota (dark) and 08:00 next day in Tokyo (light).
        assertTrue(ThemeSchedule.isDarkAt(ThemeMode.SCHEDULED, false, utcInstant, zone))
        assertFalse(ThemeSchedule.isDarkAt(ThemeMode.SCHEDULED, false, utcInstant, ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun storedValueMapping() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromStored("DARK"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("PURPLE"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("dark"))
    }
}
