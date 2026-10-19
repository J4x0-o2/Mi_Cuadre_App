package com.micuadre.app.service

import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ShiftNotificationModelTest {

    private val zone = ZoneId.of("America/Bogota")
    private fun ms(h: Int, m: Int) =
        ZonedDateTime.of(2026, 10, 17, h, m, 0, 0, zone).toInstant().toEpochMilli()

    private val texts = object : NotificationTexts {
        override fun idleTitle() = "MiCuadre"
        override fun tripTitle() = "Carrera en curso"
        override fun tripText(startedAtHHmm: String, total: String, tripCount: Int) =
            "Iniciada $startedAtHHmm - Hoy $total en $tripCount"
        override fun connectedTitle(total: String, tripCount: Int) = "Hoy $total - $tripCount"
        override fun connectedText() = "Toca Iniciar"
    }
    private val money: (Long) -> String = { "S${it / 100}" }

    private fun model(state: ShiftState, totals: DayTotals = DayTotals(1_850_000L, 3)) =
        ShiftNotificationModel.create(state, totals, texts, money, zone)

    @Test
    fun `on trip shows title, start time, day totals and finish and open actions`() {
        val m = model(ShiftState.OnTrip(connectedSinceMs = ms(8, 0), tripStartedAtMs = ms(14, 5)))

        assertEquals("Carrera en curso", m.title)
        assertEquals("Iniciada 14:05 - Hoy S18500 en 3", m.text)
        assertEquals(listOf(NotificationAction.FINISH_TRIP, NotificationAction.OPEN_APP), m.actions)
    }

    @Test
    fun `on trip counts the chronometer from the trip start`() {
        val m = model(ShiftState.OnTrip(connectedSinceMs = ms(8, 0), tripStartedAtMs = ms(14, 5)))

        assertEquals(ms(14, 5), m.chronometerBaseMs)
    }

    @Test
    fun `connected shows day totals, hint and start and open actions`() {
        val m = model(ShiftState.Connected(connectedSinceMs = ms(8, 0)))

        assertEquals("Hoy S18500 - 3", m.title)
        assertEquals("Toca Iniciar", m.text)
        assertEquals(listOf(NotificationAction.START_TRIP, NotificationAction.OPEN_APP), m.actions)
        assertNull(m.chronometerBaseMs)
    }

    @Test
    fun `connected with no trips shows zero totals`() {
        val m = model(ShiftState.Connected(ms(8, 0)), DayTotals.EMPTY)

        assertEquals("Hoy S0 - 0", m.title)
    }

    @Test
    fun `disconnected only offers opening the app`() {
        val m = model(ShiftState.Disconnected)

        assertEquals("MiCuadre", m.title)
        assertEquals(listOf(NotificationAction.OPEN_APP), m.actions)
        assertNull(m.chronometerBaseMs)
    }

    @Test
    fun `day totals sum amounts and count finished trips`() {
        fun trip(id: Long, amount: Long?, ended: Long?) = Trip(
            id = id, sessionId = 1L, platformId = 2L, platformName = "Didi",
            startedAt = 1L, endedAt = ended, amountMinor = amount, paymentMethod = PaymentMethod.CASH
        )

        val totals = DayTotals.from(listOf(trip(1, 100L, 5L), trip(2, 250L, 9L), trip(3, null, null)))

        assertEquals(DayTotals(350L, 2), totals)
    }
}
