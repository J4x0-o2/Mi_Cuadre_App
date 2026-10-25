package com.micuadre.app.presentation.history

import com.micuadre.app.domain.model.HistoryTotals
import com.micuadre.app.domain.model.HistoryWeek
import com.micuadre.app.domain.model.MonthDetail
import com.micuadre.app.domain.model.MonthHistory
import com.micuadre.app.domain.model.YearHistory
import com.micuadre.app.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HistoryModelsTest {

    private fun totals(gross: Long, expenses: Long = 0L, shifts: Int = 1) = HistoryTotals(gross, expenses, shifts)

    private fun year(
        months: List<MonthHistory>,
        hasPrevious: Boolean = true,
        isCurrentYear: Boolean = true
    ) = YearHistory(
        year = 2026,
        totals = totals(
            months.sumOf { it.totals.grossMinor },
            months.sumOf { it.totals.expensesMinor },
            months.sumOf { it.totals.shiftCount }
        ),
        months = months,
        hasPreviousYear = hasPrevious,
        isCurrentYear = isCurrentYear
    )

    private fun month(m: Int, gross: Long, expenses: Long = 0L, shifts: Int = 3, current: Boolean = false) =
        MonthHistory(YearMonth.of(2026, m), totals(gross, expenses, shifts), current)

    @Test
    fun `year hero shows gross net and shift count`() {
        val model = YearHistoryModel.from(year(listOf(month(10, 300_000, 50_000, 4), month(9, 600_000, 100_000, 6))))

        assertEquals(2026, model.year)
        assertEquals(Formatters.moneyNoDecimals(900_000), model.grossText)
        assertEquals(Formatters.signedMoney(750_000), model.netText)
        assertEquals(10, model.shiftCount)
    }

    @Test
    fun `month rows keep the newest first order with titlecase Spanish names`() {
        val model = YearHistoryModel.from(year(listOf(month(10, 300_000, current = true), month(9, 600_000))))

        assertEquals(listOf("Octubre", "Septiembre"), model.months.map { it.name })
        assertEquals(listOf(true, false), model.months.map { it.isCurrent })
        assertEquals(YearMonth.of(2026, 10), model.months.first().month)
    }

    @Test
    fun `month bars are relative to the best month`() {
        val model = YearHistoryModel.from(year(listOf(month(10, 300_000), month(9, 600_000), month(8, 0))))

        assertEquals(listOf(0.5f, 1f, 0f), model.months.map { it.barFraction })
    }

    @Test
    fun `a month row carries its net and shift count`() {
        val row = YearHistoryModel.from(year(listOf(month(9, 600_000, 100_000, 6)))).months.single()

        assertEquals(Formatters.moneyNoDecimals(600_000), row.grossText)
        assertEquals(Formatters.signedMoney(500_000), row.netText)
        assertEquals(6, row.shiftCount)
    }

    @Test
    fun `a negative net keeps the app minus and is flagged`() {
        val row = YearHistoryModel.from(year(listOf(month(9, 10_000, 25_000)))).months.single()

        assertEquals(Formatters.signedMoney(-15_000), row.netText)
        assertTrue(row.netNegative)
    }

    @Test
    fun `year stepping follows the history flags`() {
        val first = YearHistoryModel.from(year(emptyList(), hasPrevious = false, isCurrentYear = true))
        val past = YearHistoryModel.from(year(emptyList(), hasPrevious = true, isCurrentYear = false))

        assertFalse(first.canGoBack)
        assertFalse(first.canGoForward)
        assertTrue(past.canGoBack)
        assertTrue(past.canGoForward)
    }

    @Test
    fun `a year without months is empty`() {
        assertTrue(YearHistoryModel.from(year(emptyList())).isEmpty)
        assertFalse(YearHistoryModel.from(year(listOf(month(9, 1)))).isEmpty)
    }

    // ---- month ----

    private fun week(start: String, end: String, gross: Long, expenses: Long = 0L, shifts: Int = 2) =
        HistoryWeek(LocalDate.parse(start), LocalDate.parse(end), totals(gross, expenses, shifts))

    private fun detail(weeks: List<HistoryWeek>, month: YearMonth = YearMonth.of(2026, 9)) = MonthDetail(
        month = month,
        totals = totals(
            weeks.sumOf { it.totals.grossMinor },
            weeks.sumOf { it.totals.expensesMinor },
            weeks.sumOf { it.totals.shiftCount }
        ),
        weeks = weeks
    )

    @Test
    fun `month hero shows title year and the three tiles`() {
        val model = MonthHistoryModel.from(
            detail(
                listOf(
                    week("2026-09-01", "2026-09-06", 500_000, 80_000, 5),
                    week("2026-09-07", "2026-09-13", 200_000, 20_000, 3)
                )
            )
        )

        assertEquals("Septiembre", model.title)
        assertEquals(2026, model.year)
        assertEquals(Formatters.moneyNoDecimals(700_000), model.grossText)
        assertEquals(Formatters.expenseMoney(100_000), model.expensesText)
        assertEquals(Formatters.signedMoney(600_000), model.netText)
        assertFalse(model.netNegative)
        assertEquals(8, model.shiftCount)
    }

    @Test
    fun `a month without expenses shows a zero expense tile`() {
        val model = MonthHistoryModel.from(detail(listOf(week("2026-09-01", "2026-09-06", 500_000))))

        assertEquals(Formatters.expenseMoney(0), model.expensesText)
    }

    @Test
    fun `week bars are relative to the best week`() {
        val model = MonthHistoryModel.from(
            detail(
                listOf(
                    week("2026-09-01", "2026-09-06", 100_000),
                    week("2026-09-07", "2026-09-13", 400_000),
                    week("2026-09-14", "2026-09-20", 0)
                )
            )
        )

        assertEquals(listOf(0.25f, 1f, 0f), model.weeks.map { it.barFraction })
    }

    @Test
    fun `week ranges use the Spanish short month`() {
        val model = MonthHistoryModel.from(
            detail(listOf(week("2026-09-01", "2026-09-06", 1), week("2026-09-28", "2026-09-30", 1)))
        )

        assertEquals(listOf("1 – 6 sep", "28 – 30 sep"), model.weeks.map { it.rangeText })
    }

    @Test
    fun `a one day week shows a single date and a week across months shows both months`() {
        val model = MonthHistoryModel.from(
            detail(listOf(week("2026-09-30", "2026-09-30", 1), week("2026-09-29", "2026-10-05", 1)))
        )

        assertEquals(listOf("30 sep", "29 sep – 5 oct"), model.weeks.map { it.rangeText })
    }

    @Test
    fun `a week row carries its net and shift count`() {
        val row = MonthHistoryModel
            .from(detail(listOf(week("2026-09-01", "2026-09-06", 90_000, 30_000, 4))))
            .weeks.single()

        assertEquals(Formatters.moneyNoDecimals(90_000), row.grossText)
        assertEquals(Formatters.signedMoney(60_000), row.netText)
        assertEquals(4, row.shiftCount)
    }

    @Test
    fun `a month with no weeks has an empty list`() {
        val model = MonthHistoryModel.from(detail(emptyList(), YearMonth.of(2026, 10)))

        assertEquals("Octubre", model.title)
        assertTrue(model.weeks.isEmpty())
    }
}
