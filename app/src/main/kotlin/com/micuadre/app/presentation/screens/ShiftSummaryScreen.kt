package com.micuadre.app.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micuadre.app.R
import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.presentation.components.MetricTile
import com.micuadre.app.presentation.components.SectionLabel
import com.micuadre.app.presentation.components.cardSurface
import com.micuadre.app.presentation.summary.PlatformBar
import com.micuadre.app.presentation.summary.ShiftSummaryModel
import com.micuadre.app.presentation.summary.SummaryRange
import com.micuadre.app.ui.theme.MiCuadreTheme
import com.micuadre.app.util.Formatters

/**
 * Full-screen shift summary rendered inside the activity content (not a Dialog), so it inherits
 * the edge-to-edge window, the host Scaffold insets and the theme like every other screen.
 * Back does the same as "Listo".
 */
@Composable
fun ShiftSummaryScreen(
    summary: DailySummary,
    onClose: () -> Unit
) {
    val model = remember(summary) { ShiftSummaryModel.from(summary) }
    BackHandler(onBack = onClose)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        // Short screens scroll; tall ones spread the content over the available height.
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxHeight)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryHeader(model)
                MetricsGrid(model)
                PlatformSection(model.bars)
            }
        }
        DoneButton(onClick = onClose, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun SummaryHeader(model: ShiftSummaryModel) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp)
        )
    }
    Text(
        text = stringResource(R.string.summary_closed),
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )
    Text(
        text = rangeText(model.range),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    SectionLabel(
        text = stringResource(R.string.summary_earned),
        modifier = Modifier.padding(top = 12.dp)
    )
    Text(
        text = Formatters.moneyNoDecimals(model.totalMinor),
        style = MiCuadreTheme.typography.moneyLarge.copy(fontSize = 50.sp, lineHeight = 58.sp),
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun rangeText(range: SummaryRange): String = when (range) {
    SummaryRange.Unknown -> stringResource(R.string.summary_range_unknown)
    is SummaryRange.Today -> stringResource(R.string.summary_range, range.start, range.end)
    is SummaryRange.SameDay -> stringResource(R.string.summary_range_day, range.date, range.start, range.end)
    is SummaryRange.Span -> stringResource(
        R.string.summary_range_span,
        range.startDate, range.startTime, range.endDate, range.endTime
    )
}

@Composable
private fun MetricsGrid(model: ShiftSummaryModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(
                label = stringResource(R.string.connected_label),
                value = model.connectedText,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = stringResource(R.string.trips_label),
                value = model.tripCount.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(
                label = stringResource(R.string.cash),
                value = Formatters.moneyNoDecimals(model.cashMinor),
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = stringResource(R.string.transfer),
                value = Formatters.moneyNoDecimals(model.transferMinor),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PlatformSection(bars: List<PlatformBar>) {
    if (bars.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .cardSurface(MaterialTheme.shapes.large)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionLabel(text = stringResource(R.string.summary_by_platform))
        bars.forEach { bar -> PlatformBarRow(bar) }
    }
}

@Composable
private fun PlatformBarRow(bar: PlatformBar) {
    val name = when (bar.platform) {
        Platform.INDRIVER -> stringResource(R.string.indriver)
        Platform.DIDI -> stringResource(R.string.didi)
        Platform.OTHER -> bar.otherLabel ?: stringResource(R.string.other)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = Formatters.moneyNoDecimals(bar.totalMinor),
                style = MiCuadreTheme.typography.moneySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(bar.fraction.coerceIn(0f, 1f))
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

@Composable
private fun DoneButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Text(text = stringResource(R.string.summary_done), style = MaterialTheme.typography.titleMedium)
    }
}
