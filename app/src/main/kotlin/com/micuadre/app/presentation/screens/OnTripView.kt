package com.micuadre.app.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micuadre.app.R
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.presentation.HomeUiState
import com.micuadre.app.presentation.components.HeaderStatus
import com.micuadre.app.presentation.components.MetricTile
import com.micuadre.app.presentation.components.PrimaryActionButton
import com.micuadre.app.presentation.components.ScreenHeader
import com.micuadre.app.presentation.components.SectionLabel
import com.micuadre.app.presentation.home.DayBreakdown
import com.micuadre.app.presentation.home.HomeActions
import com.micuadre.app.presentation.home.RingProgress
import com.micuadre.app.presentation.home.clockTime
import com.micuadre.app.ui.theme.MiCuadreTheme
import com.micuadre.app.util.Formatters

private val RingSize = 264.dp
private val RingStroke = 10.dp

/**
 * On-trip state: gold pill, 264dp ring timer, day tiles, "Terminar carrera" and a disabled
 * "Desconectar" (a trip must be finished before disconnecting).
 */
@Composable
fun OnTripView(
    ui: HomeUiState,
    onFinishTrip: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val breakdown = remember(ui.trips) { DayBreakdown.from(ui.trips) }
    val startedAt = (ui.shiftState as? ShiftState.OnTrip)?.tripStartedAtMs
    val canDisconnect = HomeActions.canDisconnect(ui.shiftState)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        ScreenHeader(status = HeaderStatus.ON_TRIP, onSettingsClick = onOpenSettings)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            TripRing(
                tripSec = ui.tripElapsedSec,
                startedAtText = startedAt?.let { stringResource(R.string.trip_started_at, clockTime(it)) }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    label = stringResource(R.string.total_today),
                    value = Formatters.moneyNoDecimals(breakdown.totalMinor),
                    modifier = Modifier.weight(1f),
                    valueStyle = MiCuadreTheme.typography.moneySmall
                )
                MetricTile(
                    label = stringResource(R.string.connected_label),
                    value = Formatters.durationHMSS(ui.elapsedSec),
                    modifier = Modifier.weight(1f),
                    valueStyle = MiCuadreTheme.typography.moneySmall
                )
            }
        }
        PrimaryActionButton(
            text = stringResource(R.string.finish_trip_cta),
            icon = Icons.Filled.Check,
            height = 76.dp,
            cornerRadius = 24.dp,
            onClick = onFinishTrip,
            modifier = Modifier.padding(top = 16.dp)
        )
        // Disabled by business rule; no click handler is needed while it cannot be enabled.
        OutlinedButton(
            onClick = {},
            enabled = canDisconnect,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(52.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(stringResource(R.string.disconnect_cta), style = MaterialTheme.typography.titleSmall)
        }
        if (!canDisconnect) {
            Text(
                text = stringResource(R.string.disconnect_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}

/**
 * 264dp ring: surfaceVariant track and a gold arc whose sweep follows [RingProgress]
 * (one step per elapsed minute, decorative), with the duration inside.
 */
@Composable
private fun TripRing(tripSec: Long, startedAtText: String?) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val gold = MiCuadreTheme.colors.gold
    val sweep = RingProgress.fraction(tripSec) * 360f

    Box(modifier = Modifier.size(RingSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = RingStroke.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            drawArc(
                color = gold,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SectionLabel(text = stringResource(R.string.trip_duration_label))
            Text(
                text = Formatters.tripTimer(tripSec),
                style = MiCuadreTheme.typography.timer.copy(fontSize = 52.sp, lineHeight = 60.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            if (startedAtText != null) {
                Text(
                    text = startedAtText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
