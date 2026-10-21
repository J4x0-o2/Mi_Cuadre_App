package com.micuadre.app.presentation.period

import com.micuadre.app.domain.model.DeductionTotal
import com.micuadre.app.domain.model.Period
import com.micuadre.app.domain.model.PeriodBucket
import com.micuadre.app.domain.model.PeriodSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PeriodSheetModelTest {

    private fun d(day: Int, month: Int = 10) = LocalDate.of(2026, month, day)

    /** Pesos to minor units. */
    private fun pesos(value: Long) = value * 100

    private fun summary(
        buckets: List<PeriodBucket>,
        total: Long = buckets.sumOf { it.totalMinor },
        trips: Int = 0,
        workedDays: Int = 0,
        costs: List<DeductionTotal> = emptyList()
    ) = PeriodSummary(
        totalMinor = total, cashMinor = total / 2, transferMinor = total - total / 2,
        tripCount = trips, connectedSeconds = 0L, sessionCount = workedDays, workedDays = workedDays,
        firstSessionStartMs = null, buckets = buckets, costs = costs
    )

    /** Mon 12 .. Sun 18 Oct 2026; Fri 16 earned 92.000, Sat 17 earned 46.000, rest nothing. */
    private fun weekBuckets(monday: LocalDate = d(12), amounts: Map<Int, Long> = mapOf(4 to 92_000L, 5 to 46_000L)) =
        (0 until 7).map { i ->
            val day = monday.plusDays(i.toLong())
            PeriodBucket(day, day, pesos(amounts[i] ?: 0L))
        }

    // ---- range text ----

    @Test
    fun `week range spans Monday to Sunday within one month`() {
        val model = PeriodSheetModel.from(Period.WEEK, summary(weekBuckets()), today = d(18))

        assertEquals("Lun 12 – Dom 18 oct · semana actual", model.rangeText)
    }

    @Test
    fun `week range names both months when it crosses a month boundary`() {
        // Thu 1 Oct 2026: the week began Mon 28 Sep and ends Sun 4 Oct.
        val model = PeriodSheetModel.from(Period.WEEK, summary(weekBuckets(LocalDate.of(2026, 9, 28))), today = d(1))

        assertEquals("Lun 28 sep – Dom 4 oct · semana actual", model.rangeText)
    }

    @Test
    fun `month range runs from the first to today`() {
        val buckets = listOf(PeriodBucket(d(1), d(4), 0L), PeriodBucket(d(5), d(11), 0L), PeriodBucket(d(12), d(18), 0L))
        val model = PeriodSheetModel.from(Period.MONTH, summary(buckets), today = d(18))

        assertEquals("1 – 18 oct · mes actual", model.rangeText)
    }

    @Test
    fun `month range on the first day is a single date`() {
        val model = PeriodSheetModel.from(Period.MONTH, summary(listOf(PeriodBucket(d(1), d(1), 0L))), today = d(1))

        assertEquals("1 oct · mes actual", model.rangeText)
    }

    // ---- bars ----

    @Test
    fun `week bars are labelled L M X J V S D and described with the full day name`() {
        val model = PeriodSheetModel.from(Period.WEEK, summary(weekBuckets()), today = d(18))

        assertEquals(listOf("L", "M", "X", "J", "V", "S", "D"), model.bars.map { it.label })
        assertEquals("Viernes: $ 92.000", model.bars[4].description)
        assertEquals("Lunes: $ 0", model.bars[0].description)
    }

    @Test
    fun `bar heights are fractions of the tallest bar`() {
        val model = PeriodSheetModel.from(Period.WEEK, summary(weekBuckets()), today = d(18))

        assertEquals(1f, model.bars[4].fraction, 0.0001f)
        assertEquals(0.5f, model.bars[5].fraction, 0.0001f)
        assertEquals(0f, model.bars[0].fraction, 0.0001f)
    }

    @Test
    fun `zero buckets are stubs without a value label and non zero ones carry a compact value`() {
        val model = PeriodSheetModel.from(Period.WEEK, summary(weekBuckets()), today = d(18))

        assertTrue(model.bars[0].isEmpty)
        assertNull(model.bars[0].valueText)
        assertFalse(model.bars[4].isEmpty)
        assertEquals("$ 92k", model.bars[4].valueText)
    }

    @Test
    fun `a week without earnings has no divide by zero and only stubs`() {
        val model = PeriodSheetModel.from(Period.WEEK, summary(weekBuckets(amounts = emptyMap())), today = d(18))

        assertTrue(model.bars.all { it.isEmpty && it.fraction == 0f })
    }

    @Test
    fun `the current week bar is the one holding today`() {
        val model = PeriodSheetModel.from(Period.WEEK, summary(weekBuckets()), today = d(18))

        assertEquals(listOf(6), model.bars.indices.filter { model.bars[it].isCurrent })
    }

    @Test
    fun `month bars use day spans and the last one is current`() {
        val buckets = listOf(
            PeriodBucket(d(1), d(4), pesos(10_000)),
            PeriodBucket(d(5), d(11), pesos(40_000)),
            PeriodBucket(d(12), d(18), 0L)
        )
        val model = PeriodSheetModel.from(Period.MONTH, summary(buckets), today = d(18))

        assertEquals(listOf("1–4", "5–11", "12–18"), model.bars.map { it.label })
        assertEquals("Del 5 al 11: $ 40.000", model.bars[1].description)
        assertEquals(listOf(false, false, true), model.bars.map { it.isCurrent })
        assertEquals(0.25f, model.bars[0].fraction, 0.0001f)
    }

    @Test
    fun `a single day month segment is labelled with the bare day`() {
        val buckets = listOf(PeriodBucket(d(1), d(1), pesos(5_000)))
        val model = PeriodSheetModel.from(Period.MONTH, summary(buckets), today = d(1))

        assertEquals("1", model.bars.single().label)
        assertEquals("Día 1: $ 5.000", model.bars.single().description)
    }

    // ---- figures ----

    @Test
    fun `average divides the total by the worked days and rounds`() {
        val model = PeriodSheetModel.from(
            Period.WEEK, summary(weekBuckets(), total = pesos(138_000), trips = 9, workedDays = 4), today = d(18)
        )

        assertEquals(pesos(34_500), model.averageMinor)
        assertEquals(9, model.tripCount)
        assertEquals(4, model.workedDays)
    }

    @Test
    fun `average is unknown when no day was worked`() {
        val model = PeriodSheetModel.from(Period.WEEK, summary(weekBuckets(amounts = emptyMap())), today = d(18))

        assertNull(model.averageMinor)
        assertEquals(0, model.workedDays)
    }

    // ---- loading ----

    @Test
    fun `a missing summary yields placeholders`() {
        val model = PeriodSheetModel.from(Period.WEEK, summary = null, today = d(18))

        assertNull(model.totalMinor)
        assertNull(model.cashMinor)
        assertNull(model.tripCount)
        assertNull(model.averageMinor)
        assertTrue(model.bars.isEmpty())
        assertEquals("Lun 12 – Dom 18 oct · semana actual", model.rangeText)
    }

    @Test
    fun `an unknown date yields an empty range text`() {
        assertEquals("", PeriodSheetModel.from(Period.MONTH, summary = null, today = null).rangeText)
    }

    // ---- expenses and net ----

    private fun costed(day: LocalDate, gross: Long, costs: Long) =
        PeriodBucket(day, day, pesos(gross), pesos(costs))

    private val fuelAndFood = listOf(DeductionTotal(1, "Fuel", pesos(30_000)), DeductionTotal(2, "Food", pesos(10_000)))

    @Test
    fun `a period without costs keeps the gross view`() {
        val model = PeriodSheetModel.from(Period.WEEK, summary(weekBuckets(), workedDays = 2), today = d(18))

        assertFalse(model.hasCosts)
        assertEquals(emptyList<CostLine>(), model.costLines)
        assertEquals(0L, model.costsMinor)
        assertNull(model.netPercent)
        assertNull(model.costsPercent)
        assertNull(model.netPerDayMinor)
        assertEquals(1f, model.bars[4].fraction, 0.0001f)
        assertEquals(0f, model.bars[4].costFraction, 0.0001f)
        assertEquals("Viernes: $ 92.000", model.bars[4].description)
    }

    @Test
    fun `costs give lines total net and the ratio of the gross`() {
        val model = PeriodSheetModel.from(
            Period.WEEK, summary(weekBuckets(), total = pesos(160_000), workedDays = 4, costs = fuelAndFood), today = d(18)
        )

        assertTrue(model.hasCosts)
        assertEquals(listOf(CostLine("Fuel", pesos(30_000)), CostLine("Food", pesos(10_000))), model.costLines)
        assertEquals(pesos(40_000), model.costsMinor)
        assertEquals(pesos(120_000), model.netMinor)
        assertEquals(75, model.netPercent)
        assertEquals(25, model.costsPercent)
        assertEquals(pesos(160_000), model.totalMinor)
    }

    @Test
    fun `net per day divides by the worked days and rounds like the average`() {
        // net 120.000 / 7 days = 17.142,86 -> 17.143
        val model = PeriodSheetModel.from(
            Period.WEEK, summary(weekBuckets(), total = pesos(160_000), workedDays = 7, costs = fuelAndFood), today = d(18)
        )

        assertEquals(1_714_286L, model.netPerDayMinor)
    }

    @Test
    fun `percentages always add up to one hundred`() {
        val costs = listOf(DeductionTotal(1, "Fuel", pesos(1)))
        val model = PeriodSheetModel.from(
            Period.WEEK, summary(weekBuckets(), total = pesos(3), workedDays = 1, costs = costs), today = d(18)
        )

        assertEquals(33, model.costsPercent)
        assertEquals(67, model.netPercent)
    }

    @Test
    fun `costs above the gross show a negative net and a full cost bar`() {
        val costs = listOf(DeductionTotal(1, "Fuel", pesos(50_000)))
        val model = PeriodSheetModel.from(
            Period.WEEK, summary(weekBuckets(), total = pesos(40_000), workedDays = 2, costs = costs), today = d(18)
        )

        assertEquals(-pesos(10_000), model.netMinor)
        assertEquals(100, model.costsPercent)
        assertEquals(0, model.netPercent)
        assertEquals(-pesos(5_000), model.netPerDayMinor)
    }

    @Test
    fun `a period with costs but no gross has lines and no ratio`() {
        val costs = listOf(DeductionTotal(1, "Fuel", pesos(5_000)))
        val model = PeriodSheetModel.from(
            Period.WEEK, summary(weekBuckets(amounts = emptyMap()), workedDays = 1, costs = costs), today = d(18)
        )

        assertTrue(model.hasCosts)
        assertEquals(-pesos(5_000), model.netMinor)
        assertNull(model.netPercent)
        assertNull(model.costsPercent)
    }

    @Test
    fun `bars stack net and costs to the gross height`() {
        val buckets = listOf(costed(d(1), 100_000, 25_000), costed(d(2), 50_000, 25_000), costed(d(3), 0, 0))
        val model = PeriodSheetModel.from(
            Period.MONTH, summary(buckets, costs = fuelAndFood), today = d(3)
        )

        assertEquals(0.75f, model.bars[0].netFraction, 0.0001f)
        assertEquals(0.25f, model.bars[0].costFraction, 0.0001f)
        assertEquals(1f, model.bars[0].fraction, 0.0001f)
        assertEquals(0.25f, model.bars[1].netFraction, 0.0001f)
        assertEquals(0.25f, model.bars[1].costFraction, 0.0001f)
        assertEquals("$ 100k", model.bars[0].valueText)
        assertTrue(model.bars[2].isEmpty)
        assertEquals("Día 1: $ 100.000, gastos $ 25.000, neto $ 75.000", model.bars[0].description)
    }

    @Test
    fun `a bucket whose costs exceed its gross is clamped to the costs and flagged`() {
        val buckets = listOf(costed(d(1), 100_000, 0), costed(d(2), 20_000, 50_000), costed(d(3), 0, 10_000))
        val model = PeriodSheetModel.from(Period.MONTH, summary(buckets, costs = fuelAndFood), today = d(3))

        assertEquals(0f, model.bars[1].netFraction, 0.0001f)
        assertEquals(0.5f, model.bars[1].costFraction, 0.0001f)
        assertTrue(model.bars[1].costsExceedGross)
        assertFalse(model.bars[0].costsExceedGross)
        assertTrue(model.hasBarOverrun)
        assertEquals("Día 2: $ 20.000, gastos $ 50.000, neto − $ 30.000", model.bars[1].description)
        // Costs without any gross still draw a bar, with no value label.
        assertFalse(model.bars[2].isEmpty)
        assertNull(model.bars[2].valueText)
        assertEquals(0.1f, model.bars[2].costFraction, 0.0001f)
    }

    @Test
    fun `bar scale grows to the biggest cost when it passes every gross`() {
        val buckets = listOf(costed(d(1), 10_000, 40_000))
        val model = PeriodSheetModel.from(Period.MONTH, summary(buckets, costs = fuelAndFood), today = d(1))

        assertEquals(1f, model.bars.single().costFraction, 0.0001f)
        assertEquals(1f, model.bars.single().fraction, 0.0001f)
    }

    @Test
    fun `a missing summary has no costs`() {
        val model = PeriodSheetModel.from(Period.WEEK, summary = null, today = d(18))

        assertFalse(model.hasCosts)
        assertNull(model.netMinor)
        assertNull(model.netPerDayMinor)
    }
}
