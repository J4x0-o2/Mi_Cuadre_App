package com.micuadre.app.presentation.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micuadre.app.R
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.presentation.HomeUiState
import com.micuadre.app.presentation.MainViewModel
import com.micuadre.app.presentation.NotificationPermission
import com.micuadre.app.presentation.components.FinishTripSheet
import com.micuadre.app.presentation.components.HeaderStatus
import com.micuadre.app.presentation.components.MetricTile
import com.micuadre.app.presentation.components.PrimaryActionButton
import com.micuadre.app.presentation.components.ScreenHeader
import com.micuadre.app.presentation.components.SectionLabel
import com.micuadre.app.presentation.components.TripRow
import com.micuadre.app.presentation.components.cardSurface
import com.micuadre.app.presentation.home.DayBreakdown
import com.micuadre.app.presentation.home.HomeAction
import com.micuadre.app.presentation.home.HomeActions
import com.micuadre.app.presentation.home.TripRowModel
import com.micuadre.app.ui.theme.MiCuadreTheme
import com.micuadre.app.util.Formatters

private val ScreenPadding = 20.dp

@Composable
fun HomeScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> viewModel.onNotificationPermissionResult(granted) }
    )

    LaunchedEffect(state.needsNotificationPermission) {
        if (state.needsNotificationPermission) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val pendingMessage = state.pendingMessage
    val pendingText = pendingMessage?.let { stringResource(it.textRes) }
    LaunchedEffect(pendingMessage?.id) {
        if (pendingMessage != null && pendingText != null) {
            snackbarHostState.showSnackbar(pendingText)
            viewModel.onMessageShown(pendingMessage.id)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state.shiftState) {
                is ShiftState.Disconnected -> DisconnectedView(
                    ui = state,
                    onConnect = { viewModel.onConnectTapped(notificationPermissionState(context)) },
                    onEndShift = { viewModel.onEndShiftTapped() }
                )
                is ShiftState.Connected -> ConnectedView(
                    ui = state,
                    onNewTrip = { viewModel.onNewTripTapped() },
                    onDisconnect = { viewModel.onDisconnectTapped() },
                    onEndShift = { viewModel.onEndShiftTapped() }
                )
                is ShiftState.OnTrip -> OnTripView(
                    ui = state,
                    onFinishTrip = { viewModel.onFinishTripTapped() }
                )
            }
        }
    }

    state.finishSheet?.let { sheet ->
        FinishTripSheet(
            state = sheet,
            onDismiss = { viewModel.onFinishTripDismissed() },
            onConfirm = { platform, otherLabel, amountMinor, method ->
                viewModel.onFinishTripConfirmed(
                    platform = platform,
                    platformOtherLabel = otherLabel,
                    amountMinor = amountMinor,
                    paymentMethod = method
                )
            }
        )
    }

    state.summary?.let { summary ->
        ShiftSummaryDialog(
            summary = summary,
            onClose = { viewModel.onSummaryDismissed() }
        )
    }
}

private fun notificationPermissionState(context: Context): NotificationPermission {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return NotificationPermission.NotRequired
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
    return if (granted) NotificationPermission.Granted else NotificationPermission.NeedsRequest
}

/**
 * Welcome with the primary "Conectarse" action. When a paused shift is still open it also
 * shows the session's day total and offers "Terminar turno".
 */
@Composable
private fun DisconnectedView(
    ui: HomeUiState,
    onConnect: () -> Unit,
    onEndShift: () -> Unit
) {
    val actions = HomeActions.secondary(ui.shiftState, ui.hasOpenShift)
    val paused = ui.hasOpenShift
    val breakdown = remember(ui.trips) { DayBreakdown.from(ui.trips) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenPadding)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        ScreenHeader(status = null)
        if (paused) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TotalCard(breakdown)
                MetricTile(
                    label = stringResource(R.string.trips_label),
                    value = breakdown.tripCount.toString(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.paused_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        PrimaryActionButton(
            text = stringResource(R.string.connect_cta),
            icon = null,
            height = 68.dp,
            cornerRadius = 22.dp,
            onClick = onConnect
        )
        if (HomeAction.END_SHIFT in actions) {
            SecondaryActionButton(
                text = stringResource(R.string.end_shift_cta),
                onClick = onEndShift,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun ConnectedView(
    ui: HomeUiState,
    onNewTrip: () -> Unit,
    onDisconnect: () -> Unit,
    onEndShift: () -> Unit
) {
    val actions = HomeActions.secondary(ui.shiftState, ui.hasOpenShift)
    val breakdown = remember(ui.trips) { DayBreakdown.from(ui.trips) }
    val rows = remember(ui.trips) {
        ui.trips.filter { it.endedAt != null }.map { TripRowModel.from(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenPadding)
            .padding(top = 16.dp)
    ) {
        ScreenHeader(status = HeaderStatus.IN_SHIFT)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "total") { TotalCard(breakdown) }
            item(key = "tiles") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile(
                        label = stringResource(R.string.connected_label),
                        value = Formatters.durationHMSS(ui.elapsedSec),
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = stringResource(R.string.trips_label),
                        value = breakdown.tripCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item(key = "cta") {
                PrimaryActionButton(
                    text = stringResource(R.string.start_trip_cta),
                    icon = Icons.Filled.PlayArrow,
                    height = 68.dp,
                    cornerRadius = 22.dp,
                    onClick = onNewTrip
                )
            }
            item(key = "list-label") {
                SectionLabel(
                    text = stringResource(R.string.todays_trips),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (rows.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.no_trips_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(rows, key = { it.id }) { row -> TripRow(model = row) }
            }
        }
        Row(
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            actions.forEach { action ->
                when (action) {
                    HomeAction.DISCONNECT -> SecondaryActionButton(
                        text = stringResource(R.string.disconnect_cta),
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f)
                    )
                    HomeAction.END_SHIFT -> SecondaryActionButton(
                        text = stringResource(R.string.end_shift_cta),
                        onClick = onEndShift,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** "TOTAL DEL DÍA" card with the cash / transfer split. */
@Composable
private fun TotalCard(breakdown: DayBreakdown) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(MaterialTheme.shapes.large)
            .padding(20.dp)
    ) {
        SectionLabel(text = stringResource(R.string.total_today))
        Text(
            text = Formatters.moneyNoDecimals(breakdown.totalMinor),
            style = MiCuadreTheme.typography.moneyLarge.copy(fontSize = 46.sp, lineHeight = 54.sp),
            color = MiCuadreTheme.colors.money,
            modifier = Modifier.padding(top = 6.dp)
        )
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricTile(
                label = stringResource(R.string.cash),
                value = Formatters.moneyNoDecimals(breakdown.cashMinor),
                modifier = Modifier.weight(1f),
                valueStyle = MiCuadreTheme.typography.moneySmall,
                onCard = true
            )
            MetricTile(
                label = stringResource(R.string.transfer),
                value = Formatters.moneyNoDecimals(breakdown.transferMinor),
                modifier = Modifier.weight(1f),
                valueStyle = MiCuadreTheme.typography.moneySmall,
                onCard = true
            )
        }
    }
}

@Composable
private fun SecondaryActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}
