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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.micuadre.app.domain.model.Period
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.presentation.HomeUiState
import com.micuadre.app.presentation.MainViewModel
import com.micuadre.app.presentation.NotificationPermission
import com.micuadre.app.presentation.components.FinishTripSheet
import com.micuadre.app.presentation.components.HeaderStatus
import com.micuadre.app.presentation.components.MetricTile
import com.micuadre.app.presentation.components.PeriodButtons
import com.micuadre.app.presentation.components.PeriodSheet
import com.micuadre.app.presentation.components.PrimaryActionButton
import com.micuadre.app.presentation.components.ScreenHeader
import com.micuadre.app.presentation.components.SectionLabel
import com.micuadre.app.presentation.components.TripRow
import com.micuadre.app.presentation.components.cardSurface
import com.micuadre.app.presentation.home.DayBreakdown
import com.micuadre.app.presentation.home.DisconnectedContent
import com.micuadre.app.presentation.home.HomeAction
import com.micuadre.app.presentation.home.HomeActions
import com.micuadre.app.presentation.home.TodaySubtitle
import com.micuadre.app.presentation.home.TodaySummaryModel
import com.micuadre.app.presentation.home.TripRowModel
import com.micuadre.app.presentation.home.clockTime
import com.micuadre.app.presentation.home.shiftStartMs
import com.micuadre.app.ui.theme.MiCuadreTheme
import com.micuadre.app.util.Formatters

private val ScreenPadding = 20.dp

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
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
            val summary = state.summary
            if (summary != null) {
                ShiftSummaryScreen(
                    summary = summary,
                    onClose = { viewModel.onSummaryDismissed() }
                )
            } else when (state.shiftState) {
                is ShiftState.Disconnected -> DisconnectedView(
                    ui = state,
                    onConnect = { viewModel.onConnectTapped(notificationPermissionState(context)) },
                    onEndShift = { viewModel.onEndShiftTapped() },
                    onPeriodTapped = viewModel::onPeriodTapped,
                    onOpenSettings = onOpenSettings
                )
                is ShiftState.Connected -> ConnectedView(
                    ui = state,
                    onNewTrip = { viewModel.onNewTripTapped() },
                    onDisconnect = { viewModel.onDisconnectTapped() },
                    onEndShift = { viewModel.onEndShiftTapped() },
                    onPeriodTapped = viewModel::onPeriodTapped,
                    onOpenSettings = onOpenSettings
                )
                is ShiftState.OnTrip -> OnTripView(
                    ui = state,
                    onFinishTrip = { viewModel.onFinishTripTapped() },
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }

    state.periodSheet?.takeIf { it != Period.TODAY }?.let { period ->
        PeriodSheet(
            period = period,
            summary = if (period == Period.WEEK) state.week else state.month,
            today = state.todayDate,
            onPeriodSelected = viewModel::onPeriodTapped,
            onDismiss = viewModel::onPeriodSheetDismissed
        )
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
 * "Resumen de hoy" with the primary "Conectarse" action: every shift started today, plus the
 * week and month totals. With no shift yet today it shows the welcome instead. A paused shift
 * keeps its hint and offers "Terminar turno"; when it started on a previous day (so today's
 * summary is empty) its own totals are shown as "Total del turno".
 */
@Composable
private fun DisconnectedView(
    ui: HomeUiState,
    onConnect: () -> Unit,
    onEndShift: () -> Unit,
    onPeriodTapped: (Period) -> Unit,
    onOpenSettings: () -> Unit
) {
    val actions = HomeActions.secondary(ui.shiftState, ui.hasOpenShift)
    val paused = ui.hasOpenShift
    val content = DisconnectedContent.choose(ui.hasOpenShift, ui.today?.sessionCount)
    val today = remember(ui.today) { ui.today?.let(TodaySummaryModel::from) ?: TodaySummaryModel.EMPTY }
    val openShift = remember(ui.trips, ui.connectedIntervals) {
        TodaySummaryModel.ofOpenShift(DayBreakdown.from(ui.trips), ui.connectedIntervals)
    }
    val openShiftStart = remember(ui.shiftState, ui.connectedIntervals) {
        shiftStartMs(ui.shiftState, ui.connectedIntervals)
    }
    val shown = if (content == DisconnectedContent.OPEN_SHIFT) openShift else today
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenPadding)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        ScreenHeader(status = null, onSettingsClick = onOpenSettings)
        if (content != DisconnectedContent.WELCOME) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (content == DisconnectedContent.OPEN_SHIFT) {
                    Text(
                        text = stringResource(R.string.paused_shift_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TotalCard(
                        label = stringResource(R.string.total_shift),
                        totalMinor = shown.totalMinor,
                        cashMinor = shown.cashMinor,
                        transferMinor = shown.transferMinor,
                        trailing = openShiftStart?.let { stringResource(R.string.since_time, clockTime(it)) }
                    )
                } else {
                    Column {
                        Text(
                            text = stringResource(R.string.today_summary_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ui.todayDate?.let { date ->
                            Text(
                                text = TodaySubtitle.format(date, today.sessionCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TotalCard(
                        label = stringResource(R.string.total_today),
                        totalMinor = today.totalMinor,
                        cashMinor = today.cashMinor,
                        transferMinor = today.transferMinor
                    )
                }
                TodayTiles(shown)
                EarningsSection(ui, onPeriodTapped)
                if (paused) {
                    Text(
                        text = stringResource(R.string.paused_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.weight(1f),
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
                EarningsSection(ui, onPeriodTapped, modifier = Modifier.padding(bottom = 16.dp))
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

/** Carreras / Conectado (h:mm) / Por hora of the day; "—" per hour while nothing is connected. */
@Composable
private fun TodayTiles(today: TodaySummaryModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricTile(
            label = stringResource(R.string.trips_label),
            value = today.tripCount.toString(),
            modifier = Modifier.weight(1f),
            valueStyle = MiCuadreTheme.typography.moneySmall,
            compact = true
        )
        MetricTile(
            label = stringResource(R.string.connected_label),
            value = Formatters.durationHMM(today.connectedSeconds),
            modifier = Modifier.weight(1f),
            valueStyle = MiCuadreTheme.typography.moneySmall,
            compact = true
        )
        MetricTile(
            label = stringResource(R.string.per_hour_label),
            value = today.perHourMinor?.let(Formatters::moneyCompact) ?: stringResource(R.string.per_hour_unknown),
            modifier = Modifier.weight(1f),
            valueStyle = MiCuadreTheme.typography.moneySmall,
            compact = true
        )
    }
}

/** "MIS INGRESOS" label with the week / month buttons. */
@Composable
private fun EarningsSection(
    ui: HomeUiState,
    onPeriodTapped: (Period) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(text = stringResource(R.string.earnings_title), modifier = Modifier.padding(top = 4.dp))
        PeriodButtons(week = ui.week, month = ui.month, onPeriodTapped = onPeriodTapped)
    }
}

@Composable
private fun ConnectedView(
    ui: HomeUiState,
    onNewTrip: () -> Unit,
    onDisconnect: () -> Unit,
    onEndShift: () -> Unit,
    onPeriodTapped: (Period) -> Unit,
    onOpenSettings: () -> Unit
) {
    val actions = HomeActions.secondary(ui.shiftState, ui.hasOpenShift)
    val breakdown = remember(ui.trips) { DayBreakdown.from(ui.trips) }
    val shiftStart = remember(ui.shiftState, ui.connectedIntervals) {
        shiftStartMs(ui.shiftState, ui.connectedIntervals)
    }
    val rows = remember(ui.trips) {
        ui.trips.filter { it.endedAt != null }.map { TripRowModel.from(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenPadding)
            .padding(top = 16.dp)
    ) {
        ScreenHeader(status = HeaderStatus.IN_SHIFT, onSettingsClick = onOpenSettings)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "total") {
                TotalCard(
                    label = stringResource(R.string.total_shift),
                    totalMinor = breakdown.totalMinor,
                    cashMinor = breakdown.cashMinor,
                    transferMinor = breakdown.transferMinor,
                    trailing = shiftStart?.let { stringResource(R.string.since_time, clockTime(it)) }
                )
            }
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
            item(key = "periods") {
                PeriodButtons(week = ui.week, month = ui.month, onPeriodTapped = onPeriodTapped)
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
                    text = stringResource(R.string.shift_trips),
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

/** Total card with the cash / transfer split; [trailing] sits at the right of the label. */
@Composable
private fun TotalCard(
    label: String,
    totalMinor: Long,
    cashMinor: Long,
    transferMinor: Long,
    trailing: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(MaterialTheme.shapes.large)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(text = label, modifier = Modifier.weight(1f))
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = Formatters.moneyNoDecimals(totalMinor),
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
                value = Formatters.moneyNoDecimals(cashMinor),
                modifier = Modifier.weight(1f),
                valueStyle = MiCuadreTheme.typography.moneySmall,
                onCard = true
            )
            MetricTile(
                label = stringResource(R.string.transfer),
                value = Formatters.moneyNoDecimals(transferMinor),
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
