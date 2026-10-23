package com.micuadre.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micuadre.app.R
import com.micuadre.app.domain.model.Period
import com.micuadre.app.domain.model.PeriodSummary
import com.micuadre.app.presentation.period.BarModel
import com.micuadre.app.presentation.period.PeriodSheetModel
import com.micuadre.app.presentation.period.StackedSegments
import com.micuadre.app.ui.theme.MiCuadreTheme
import com.micuadre.app.util.Formatters
import java.time.LocalDate

private val BarAreaHeight = 96.dp
private val ValueLabelHeight = 16.dp
private val BarGap = 6.dp
private val BarMaxWidth = 44.dp
private val StubHeight = 3.dp

/**
 * Week / month earnings detail. The header (title, close, tabs) is fixed; below it scroll the
 * range, the gross total with the cash / transfer split, the "Gastos y neto" card (only when the
 * period has expenses), a bar chart (per day or per calendar week, stacked net + expenses when
 * there are expenses) and the trips / average (/ net per day) stats. [summary] is null while it
 * loads, which shows "—" placeholders. Switching tabs asks for the other period through
 * [onPeriodSelected]; the sheet itself stays open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSheet(
    period: Period,
    summary: PeriodSummary?,
    today: LocalDate?,
    onPeriodSelected: (Period) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val model = remember(period, summary, today) { PeriodSheetModel.from(period, summary, today) }
    val placeholder = stringResource(R.string.per_hour_unknown)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.earnings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close_sheet),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            PeriodTabs(selected = period, onSelected = onPeriodSelected)
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = model.rangeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column {
                    SectionLabel(text = stringResource(R.string.period_total_label))
                    Text(
                        text = model.totalMinor?.let(Formatters::moneyNoDecimals) ?: placeholder,
                        style = MiCuadreTheme.typography.moneyLarge.copy(fontSize = 46.sp, lineHeight = 54.sp),
                        color = MiCuadreTheme.colors.money,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile(
                        label = stringResource(R.string.cash),
                        value = model.cashMinor?.let(Formatters::moneyNoDecimals) ?: placeholder,
                        modifier = Modifier.weight(1f),
                        valueStyle = MiCuadreTheme.typography.moneySmall
                    )
                    MetricTile(
                        label = stringResource(R.string.transfer),
                        value = model.transferMinor?.let(Formatters::moneyNoDecimals) ?: placeholder,
                        modifier = Modifier.weight(1f),
                        valueStyle = MiCuadreTheme.typography.moneySmall
                    )
                }
                CostsCard(model = model)
                BarChartCard(model = model)
                StatsRow(model = model, placeholder = placeholder)
            }
        }
    }
}

/**
 * Three tiles in one row. Without expenses: trips, worked days, average. With expenses the worked
 * days tile gives its place to NETO/DÍA and the worked days move to a caption under the row.
 */
@Composable
private fun StatsRow(model: PeriodSheetModel, placeholder: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(
                label = stringResource(R.string.trips_label),
                value = model.tripCount?.toString() ?: placeholder,
                modifier = Modifier.weight(1f),
                valueStyle = MiCuadreTheme.typography.moneySmall,
                compact = true
            )
            if (!model.hasCosts) {
                MetricTile(
                    label = stringResource(R.string.period_days_label),
                    value = model.workedDays?.toString() ?: placeholder,
                    modifier = Modifier.weight(1f),
                    valueStyle = MiCuadreTheme.typography.moneySmall,
                    compact = true
                )
            }
            MetricTile(
                label = stringResource(R.string.period_average_label),
                value = model.averageMinor?.let(Formatters::moneyCompact) ?: placeholder,
                modifier = Modifier.weight(1f),
                valueStyle = MiCuadreTheme.typography.moneySmall,
                compact = true
            )
            if (model.hasCosts) {
                NetPerDayTile(netPerDayMinor = model.netPerDayMinor, placeholder = placeholder, modifier = Modifier.weight(1f))
            }
        }
        if (model.hasCosts && model.workedDays != null) {
            Text(
                text = stringResource(R.string.period_days_worked, model.workedDays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NetPerDayTile(netPerDayMinor: Long?, placeholder: String, modifier: Modifier = Modifier) {
    val negative = netPerDayMinor != null && netPerDayMinor < 0L
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        SectionLabel(text = stringResource(R.string.period_net_per_day_label), fontSize = 11.sp)
        Text(
            text = netPerDayMinor?.let(Formatters::signedMoney) ?: placeholder,
            style = MiCuadreTheme.typography.moneySmall,
            color = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** Ratio bar, gross, each deduction, total and net; not shown when the period has no expenses. */
@Composable
private fun CostsCard(model: PeriodSheetModel) {
    if (!model.hasCosts) return
    val net = model.netMinor ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(MaterialTheme.shapes.large)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionLabel(text = stringResource(R.string.period_costs_title))
        val netPercent = model.netPercent
        val costsPercent = model.costsPercent
        if (netPercent != null && costsPercent != null) {
            RatioBar(netPercent = netPercent, costsPercent = costsPercent)
            Text(
                text = stringResource(R.string.period_costs_legend, netPercent, costsPercent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        CostRow(
            label = stringResource(R.string.period_costs_gross),
            value = Formatters.moneyNoDecimals(model.totalMinor ?: 0L)
        )
        model.costLines.forEach { line ->
            CostRow(
                label = line.name,
                value = Formatters.signedMoney(-line.amountMinor),
                indented = true
            )
        }
        CostRow(
            label = stringResource(R.string.period_costs_total),
            value = Formatters.signedMoney(-model.costsMinor),
            strong = true
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.period_net_left),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = Formatters.signedMoney(net),
                style = MiCuadreTheme.typography.moneyLarge.copy(fontSize = 28.sp, lineHeight = 34.sp),
                color = if (net < 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Net (green) and costs (gold) as shares of the gross; decorative, the legend says the numbers. */
@Composable
private fun RatioBar(netPercent: Int, costsPercent: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .clearAndSetSemantics {}
    ) {
        if (netPercent > 0) {
            Box(Modifier.weight(netPercent.toFloat()).fillMaxHeight().background(MiCuadreTheme.colors.money))
        }
        if (costsPercent > 0) {
            Box(Modifier.weight(costsPercent.toFloat()).fillMaxHeight().background(MiCuadreTheme.colors.gold))
        }
    }
}

@Composable
private fun CostRow(label: String, value: String, indented: Boolean = false, strong: Boolean = false) {
    val tone = when {
        strong -> MaterialTheme.colorScheme.error
        indented -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indented) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
            color = tone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Text(text = value, style = MiCuadreTheme.typography.moneySmall, color = tone)
    }
}

/** Segmented "Semana" / "Mes" control; each segment is a selectable tab. */
@Composable
private fun PeriodTabs(selected: Period, onSelected: (Period) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(Period.WEEK to R.string.period_tab_week, Period.MONTH to R.string.period_tab_month)
            .forEach { (tab, label) ->
                val isSelected = tab == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                        )
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelected(tab) }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isSelected) MiCuadreTheme.colors.money else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
    }
}

/**
 * Bars anchored to a baseline: zero buckets are a thin stub, the current bucket is emphasized.
 * With expenses each bar stacks costs (gold) over net (green) and a legend names both.
 */
@Composable
private fun BarChartCard(model: PeriodSheetModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(MaterialTheme.shapes.large)
            .padding(16.dp)
    ) {
        SectionLabel(
            text = stringResource(
                if (model.period == Period.WEEK) R.string.period_chart_by_day else R.string.period_chart_by_week
            )
        )
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(BarGap)
        ) {
            model.bars.forEach { bar ->
                Bar(bar = bar, stacked = model.hasCosts, modifier = Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // The bars already speak their label in their description; keep the axis out of the tree.
        Row(
            modifier = Modifier
                .padding(top = 6.dp)
                .clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(BarGap)
        ) {
            model.bars.forEach { bar ->
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (bar.isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (bar.isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
        if (model.hasCosts) ChartLegend(showOverrunNote = model.hasBarOverrun)
    }
}

/** Neto / Gastos swatches under the stacked chart; the note explains bars clamped to their costs. */
@Composable
private fun ChartLegend(showOverrunNote: Boolean) {
    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendSwatch(color = MiCuadreTheme.colors.money, label = stringResource(R.string.period_chart_legend_net))
            LegendSwatch(color = MiCuadreTheme.colors.gold, label = stringResource(R.string.period_chart_legend_costs))
        }
        if (showOverrunNote) {
            Text(
                text = stringResource(R.string.period_chart_overrun_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

/** One bar; [stacked] draws costs (gold) over net (green), otherwise a single green bar. */
@Composable
private fun Bar(bar: BarModel, stacked: Boolean, modifier: Modifier = Modifier) {
    val money = MiCuadreTheme.colors.money
    val gold = MiCuadreTheme.colors.gold
    // The current bucket is emphasized; the others are dimmed.
    fun tone(color: Color) = if (bar.isCurrent) color else color.copy(alpha = 0.55f)
    val corners = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
    Column(
        modifier = modifier
            .height(BarAreaHeight + ValueLabelHeight)
            .semantics(mergeDescendants = true) { contentDescription = bar.description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        if (bar.valueText != null) {
            Text(
                text = bar.valueText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.height(ValueLabelHeight)
            )
        }
        val barModifier = Modifier
            .widthIn(max = BarMaxWidth)
            .fillMaxWidth()
        when {
            bar.isEmpty ->
                Box(barModifier.height(StubHeight).background(MaterialTheme.colorScheme.outlineVariant, corners))
            stacked ->
                Column(barModifier.clip(corners)) {
                    // A small segment keeps the stub height without the bar outgrowing the area.
                    val segments = StackedSegments.of(bar.costFraction, bar.netFraction, StubHeight / BarAreaHeight)
                    if (segments.cost > 0f) {
                        Box(Modifier.fillMaxWidth().height(BarAreaHeight * segments.cost).background(tone(gold)))
                    }
                    if (segments.net > 0f) {
                        Box(Modifier.fillMaxWidth().height(BarAreaHeight * segments.net).background(tone(money)))
                    }
                }
            else ->
                Box(
                    barModifier
                        .height((BarAreaHeight * bar.fraction).coerceAtLeast(StubHeight))
                        .background(tone(money), corners)
                )
        }
    }
}
