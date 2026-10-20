package com.micuadre.app.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micuadre.app.R
import com.micuadre.app.domain.model.ConnectedInterval
import com.micuadre.app.presentation.components.cardSurface
import com.micuadre.app.presentation.home.Elapsed
import com.micuadre.app.ui.theme.MiCuadreTheme
import com.micuadre.app.util.Formatters
import kotlinx.coroutines.delay

/** Size of the collapsed bubble and width of the expanded panel, in dp. */
object BubbleDimens {
    const val BUBBLE_DP = 56
    const val PANEL_WIDTH_DP = 264

    /** Placement estimate for the tallest panel (three actions); the real height is measured by layout. */
    const val PANEL_HEIGHT_ESTIMATE_DP = 250
}

/** The bubble window content: the round bubble, or the panel once expanded. */
@Composable
fun BubbleContent(
    ui: BubbleUiState,
    intervals: List<ConnectedInterval>,
    nowMs: () -> Long,
    onAction: (PanelAction) -> Unit,
    onCollapse: () -> Unit
) {
    val state = ui.shiftState
    val elapsed by produceState(Elapsed.of(state, nowMs(), intervals), state, intervals) {
        while (true) {
            value = Elapsed.of(state, nowMs(), intervals)
            delay(1_000L)
        }
    }
    val panel = ui.panel
    if (ui.expanded && panel != null) {
        BubblePanel(panel, elapsed, onAction, onCollapse)
    } else {
        CollapsedBubble(panel?.status, elapsed)
    }
}

@Composable
private fun CollapsedBubble(status: PanelStatus?, elapsed: Elapsed) {
    val onTrip = status == PanelStatus.ON_TRIP
    Box(
        modifier = Modifier
            .size(BubbleDimens.BUBBLE_DP.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)), CircleShape)
    ) {
        // The launcher foreground leaves a safe-zone margin around the wallet, so it is drawn larger.
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.bubble_cd),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(54.dp)
                .align(Alignment.Center)
                .offset(y = if (onTrip) (-4).dp else 0.dp)
        )
        // Status dot: gold on a ride, light while only connected.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 6.dp)
                .size(11.dp)
                .clip(CircleShape)
                .background(if (onTrip) MiCuadreTheme.colors.gold else MaterialTheme.colorScheme.onPrimary)
                .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), CircleShape)
        )
        if (onTrip) {
            Text(
                text = Formatters.tripTimer(elapsed.tripSec),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun BubblePanel(
    panel: BubblePanelModel,
    elapsed: Elapsed,
    onAction: (PanelAction) -> Unit,
    onCollapse: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(BubbleDimens.PANEL_WIDTH_DP.dp)
            .cardSurface(MaterialTheme.shapes.large)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (panel.showsRideTime) R.string.status_on_trip else R.string.status_in_shift
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (panel.showsRideTime) MiCuadreTheme.colors.gold else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = Formatters.tripTimer(if (panel.showsRideTime) elapsed.tripSec else elapsed.shiftSec),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        if (panel.showsRideTime) R.string.bubble_trip_time else R.string.bubble_connected_time
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onCollapse) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.bubble_close_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (panel.confirmingCancel) {
            Text(
                text = stringResource(R.string.bubble_discard_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Column(
            modifier = Modifier.padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            panel.actions.forEach { action -> PanelButton(action, onClick = { onAction(action) }) }
        }
    }
}

@Composable
private fun PanelButton(action: PanelAction, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth()
    when (action) {
        PanelAction.START_TRIP -> Button(onClick, modifier) { Text(stringResource(R.string.start_trip_cta)) }
        PanelAction.FINISH_TRIP -> Button(onClick, modifier) { Text(stringResource(R.string.finish_trip_cta)) }
        PanelAction.ASK_CANCEL_TRIP -> OutlinedButton(onClick, modifier) {
            Text(stringResource(R.string.cancel_trip_cta), color = MaterialTheme.colorScheme.error)
        }
        PanelAction.CONFIRM_CANCEL_TRIP -> Button(
            onClick,
            modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) { Text(stringResource(R.string.bubble_discard_confirm)) }
        PanelAction.KEEP_TRIP -> OutlinedButton(onClick, modifier) {
            Text(stringResource(R.string.cancel_trip_dialog_back))
        }
        PanelAction.OPEN_APP -> TextButton(onClick, modifier) { Text(stringResource(R.string.notif_action_open_app)) }
    }
}
