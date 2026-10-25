package com.micuadre.app.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micuadre.app.R
import com.micuadre.app.presentation.components.SectionLabel
import com.micuadre.app.presentation.components.cardSurface
import com.micuadre.app.presentation.history.HistoryViewModel
import com.micuadre.app.presentation.history.MonthHistoryModel
import com.micuadre.app.presentation.history.MonthRowModel
import com.micuadre.app.presentation.history.WeekRowModel
import com.micuadre.app.presentation.history.YearHistoryModel
import com.micuadre.app.ui.theme.MiCuadreTheme

/** Year screen: a year stepper, the year total and one card per month with shifts. */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenMonth: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val model by viewModel.year.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    HistoryScaffold(
        title = stringResource(R.string.history_title),
        subtitle = null,
        backDescription = stringResource(R.string.history_back_cd),
        onBack = onBack
    ) {
        model?.let { year ->
            item {
                YearStepper(
                    year = year,
                    onPrevious = viewModel::onPreviousYear,
                    onNext = viewModel::onNextYear
                )
            }
            item {
                HeroCard(
                    label = stringResource(R.string.history_year_total),
                    grossText = year.grossText,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(
                        text = metaText(year.netText, year.shiftCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (year.isEmpty) {
                item { EmptyYear(year = year.year) }
            } else {
                item {
                    SectionTitle(stringResource(R.string.history_by_month), Modifier.padding(top = 20.dp, bottom = 4.dp))
                }
                items(year.months, key = { it.month.toString() }) { row ->
                    MonthCard(row = row, onClick = {
                        viewModel.onMonthSelected(row.month)
                        onOpenMonth()
                    })
                }
            }
        }
    }
}

/** Month screen: the month total with expenses / net / shifts tiles and one card per week. */
@Composable
fun HistoryMonthScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val model by viewModel.month.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    HistoryScaffold(
        title = model?.title.orEmpty(),
        subtitle = model?.year?.toString(),
        backDescription = stringResource(R.string.history_month_back_cd),
        onBack = onBack
    ) {
        model?.let { month ->
            item { MonthHero(month) }
            item {
                SectionTitle(stringResource(R.string.history_by_week), Modifier.padding(top = 20.dp, bottom = 4.dp))
            }
            items(month.weeks, key = { it.rangeText }) { week -> WeekCard(week) }
        }
    }
}

@Composable
private fun HistoryScaffold(
    title: String,
    subtitle: String?,
    backDescription: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backDescription,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                content = content
            )
        }
    }
}

@Composable
private fun YearStepper(year: YearHistoryModel, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(MaterialTheme.shapes.large)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepButton(
            enabled = year.canGoBack,
            description = stringResource(R.string.history_previous_year_cd),
            onClick = onPrevious,
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft
        )
        Text(
            text = year.year.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        StepButton(
            enabled = year.canGoForward,
            description = stringResource(R.string.history_next_year_cd),
            onClick = onNext,
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight
        )
    }
}

@Composable
private fun StepButton(
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    icon: ImageVector
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
        )
    }
}

/** Big card with a muted [label], the gross total and extra [content] under it. */
@Composable
private fun HeroCard(
    label: String,
    grossText: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface(MaterialTheme.shapes.extraLarge)
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SectionLabel(text = label)
        Text(
            text = grossText,
            style = MiCuadreTheme.typography.moneyLarge.copy(fontSize = 40.sp, lineHeight = 46.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        content()
    }
}

@Composable
private fun MonthHero(month: MonthHistoryModel) {
    HeroCard(label = stringResource(R.string.history_month_total), grossText = month.grossText) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Tile(
                label = stringResource(R.string.history_tile_expenses),
                value = month.expensesText,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            Tile(
                label = stringResource(R.string.history_tile_net),
                value = month.netText,
                color = netColor(month.netNegative),
                modifier = Modifier.weight(1f)
            )
            Tile(
                label = stringResource(R.string.history_tile_shifts),
                value = month.shiftCount.toString(),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun Tile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Large amounts shrink to fit instead of being cut off.
        var fontSize by remember(value) { mutableStateOf(TILE_VALUE_SIZE) }
        Text(
            text = value,
            style = MiCuadreTheme.typography.moneySmall.copy(fontSize = fontSize),
            color = color,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { layout ->
                if (layout.didOverflowWidth && fontSize > TILE_VALUE_MIN_SIZE) fontSize *= 0.9f
            },
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private val TILE_VALUE_SIZE = 15.sp
private val TILE_VALUE_MIN_SIZE = 10.sp

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

@Composable
private fun MonthCard(row: MonthRowModel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PeriodBody(
            title = if (row.isCurrent) stringResource(R.string.history_month_current, row.name) else row.name,
            grossText = row.grossText,
            barFraction = row.barFraction,
            netText = row.netText,
            shiftCount = row.shiftCount,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun WeekCard(week: WeekRowModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(MaterialTheme.shapes.large)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        PeriodBody(
            title = week.rangeText,
            grossText = week.grossText,
            barFraction = week.barFraction,
            netText = week.netText,
            shiftCount = week.shiftCount
        )
    }
}

/** Title and gross on one line, a bar relative to the best sibling and the "Neto … · N turnos" line. */
@Composable
private fun PeriodBody(
    title: String,
    grossText: String,
    barFraction: Float,
    netText: String,
    shiftCount: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            )
            Text(
                text = grossText,
                style = MiCuadreTheme.typography.moneySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        ) {
            if (barFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barFraction)
                        .height(6.dp)
                        .background(MiCuadreTheme.colors.money, CircleShape)
                )
            }
        }
        Text(
            text = metaText(netText, shiftCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyYear(year: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.history_empty_title, year),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.history_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** "Neto $ 1.051.000 · 14 turnos". */
@Composable
private fun metaText(netText: String, shiftCount: Int): String =
    stringResource(R.string.history_meta, netText, pluralStringResource(R.plurals.history_shifts, shiftCount, shiftCount))

@Composable
private fun netColor(negative: Boolean): Color =
    if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
