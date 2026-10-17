package com.micuadre.app.presentation.finish

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FinishSheetSubtitleTest {

    private val zone = ZoneId.of("America/Bogota")

    private fun ms(hour: Int, minute: Int, second: Int = 0): Long =
        ZonedDateTime.of(2026, 10, 17, hour, minute, second, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `shows start, end and whole minutes`() {
        assertEquals(
            "14:05 – 14:23 · 18 min",
            FinishSheetSubtitle.format(ms(14, 5), ms(14, 23), zone)
        )
    }

    @Test
    fun `seconds are floored to whole minutes`() {
        assertEquals(
            "09:00 – 09:12 · 12 min",
            FinishSheetSubtitle.format(ms(9, 0), ms(9, 12, 59), zone)
        )
    }

    @Test
    fun `sub minute trips show at least one minute`() {
        assertEquals(
            "09:00 – 09:00 · 1 min",
            FinishSheetSubtitle.format(ms(9, 0), ms(9, 0, 30), zone)
        )
    }

    @Test
    fun `clock moving backwards never yields negative minutes`() {
        assertEquals(
            "09:10 – 09:00 · 1 min",
            FinishSheetSubtitle.format(ms(9, 10), ms(9, 0), zone)
        )
    }

    @Test
    fun `trips past an hour keep counting minutes`() {
        assertEquals(
            "08:00 – 09:15 · 75 min",
            FinishSheetSubtitle.format(ms(8, 0), ms(9, 15), zone)
        )
    }
}
