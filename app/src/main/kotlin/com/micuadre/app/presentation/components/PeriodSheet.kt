package com.micuadre.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micuadre.app.R
import com.micuadre.app.domain.model.Period
import com.micuadre.app.domain.model.PeriodSummary
import com.micuadre.app.presentation.period.BarModel
import com.micuadre.app.presentation.period.PeriodSheetModel
import com.micuadre.app.ui.theme.MiCuadreTheme
import com.micuadre.app.util.Formatters
import java.time.LocalDate

private val BarAreaHeight = 96.dp
private val ValueLabelHeight = 16.dp
private val BarGap = 6.dp
private val BarMaxWidth = 44.dp
private val StubHeight = 3.dp

/**
 * Week / month earnings detail: tabs, range, total with the cash / transfer split, a bar chart
 * (per day or per calendar week) and the trips / days / average stats. [summary] is null while it
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
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
            BarChartCard(model = model)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile(
                    label = stringResource(R.string.trips_label),
                    value = model.tripCount?.toString() ?: placeholder,
                    modifier = Modifier.weight(1f),
                    valueStyle = MiCuadreTheme.typography.moneySmall,
                    compact = true
                )
                MetricTile(
                    label = stringResource(R.string.period_days_label),
                    value = model.workedDays?.toString() ?: placeholder,
                    modifier = Modifier.weight(1f),
                    valueStyle = MiCuadreTheme.typography.moneySmall,
                    compact = true
                )
                MetricTile(
                    label = stringResource(R.string.period_average_label),
                    value = model.averageMinor?.let(Formatters::moneyCompact) ?: placeholder,
                    modifier = Modifier.weight(1f),
                    valueStyle = MiCuadreTheme.typography.moneySmall,
                    compact = true
                )
            }
        }
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

/** Bars anchored to a baseline: zero buckets are a thin stub, the current bucket is emphasized. */
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
            model.bars.forEach { bar -> Bar(bar = bar, modifier = Modifier.weight(1f)) }
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
    }
}

@Composable
private fun Bar(bar: BarModel, modifier: Modifier = Modifier) {
    val money = MiCuadreTheme.colors.money
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
        val height = if (bar.isEmpty) StubHeight else (BarAreaHeight * bar.fraction).coerceAtLeast(StubHeight)
        val color = when {
            bar.isEmpty -> MaterialTheme.colorScheme.outlineVariant
            bar.isCurrent -> money
            else -> money.copy(alpha = 0.55f)
        }
        Box(
            modifier = Modifier
                .widthIn(max = BarMaxWidth)
                .fillMaxWidth()
                .height(height)
                .background(color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
        )
    }
}
