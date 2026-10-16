package com.micuadre.app.presentation.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micuadre.app.R
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.presentation.HomeUiState
import com.micuadre.app.presentation.MainViewModel
import com.micuadre.app.presentation.NotificationPermission
import com.micuadre.app.presentation.components.BigTimer
import com.micuadre.app.presentation.components.FinishTripSheet
import com.micuadre.app.presentation.components.PlatformPickerSheet
import com.micuadre.app.presentation.components.TripRow
import com.micuadre.app.util.Formatters

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

    // Local state for the two-step finish flow.
    // Step 1: show platform picker; step 2: show finish sheet with chosen platform.
    var pendingPlatform by remember { mutableStateOf<Platform?>(null) }
    var pendingOtherLabel by remember { mutableStateOf<String?>(null) }

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
                    trips = state.trips,
                    totalMinor = state.totalMinor,
                    onConnect = { viewModel.onConnectTapped(notificationPermissionState(context)) },
                    onEndShift = { viewModel.onEndShiftTapped() }
                )
                is ShiftState.Connected -> ConnectedView(
                    ui = state,
                    onNewTrip = { viewModel.onNewTripTapped() },
                    onDisconnect = { viewModel.onDisconnectTapped() }
                )
                is ShiftState.OnTrip -> OnTripView(
                    ui = state,
                    onFinishTrip = {
                        viewModel.onFinishTripTapped()
                        // Open platform picker alongside the finish sheet flag.
                        // The sheet opens only if the user confirms a platform.
                    }
                )
            }
        }
    }

    if (state.showFinishSheet && pendingPlatform == null) {
        PlatformPickerSheet(
            onDismiss = {
                pendingPlatform = null
                pendingOtherLabel = null
                viewModel.onFinishTripDismissed()
            },
            onConfirm = { platform, otherLabel ->
                pendingPlatform = platform
                pendingOtherLabel = otherLabel
            }
        )
    }

    val chosen = pendingPlatform
    if (chosen != null) {
        FinishTripSheet(
            platform = chosen,
            platformOtherLabel = pendingOtherLabel,
            onDismiss = {
                pendingPlatform = null
                pendingOtherLabel = null
                viewModel.onFinishTripDismissed()
            },
            onConfirm = { amountMinor, method ->
                viewModel.onFinishTripConfirmed(
                    platform = chosen,
                    platformOtherLabel = pendingOtherLabel,
                    amountMinor = amountMinor,
                    paymentMethod = method
                )
                pendingPlatform = null
                pendingOtherLabel = null
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

@Composable
private fun DisconnectedView(
    trips: List<Trip>,
    totalMinor: Long,
    onConnect: () -> Unit,
    onEndShift: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(72.dp))
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "M",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.headlineLarge
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (trips.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.total_today),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = Formatters.money(totalMinor),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(trips, key = { it.id }) { trip -> TripRow(trip = trip) }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Button(
            onClick = onConnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) { Text(stringResource(R.string.connect), style = MaterialTheme.typography.titleMedium) }

        if (trips.isNotEmpty()) {
            OutlinedButton(
                onClick = onEndShift,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(top = 8.dp, bottom = 24.dp),
                shape = RoundedCornerShape(28.dp)
            ) { Text(stringResource(R.string.end_shift), style = MaterialTheme.typography.titleMedium) }
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = stringResource(R.string.app_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun ConnectedView(
    ui: HomeUiState,
    onNewTrip: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.connected),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        BigTimer(seconds = ui.elapsedSec, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.total_today),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = Formatters.money(ui.totalMinor),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onNewTrip,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) { Text(stringResource(R.string.new_trip)) }

        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(top = 8.dp),
            shape = RoundedCornerShape(28.dp)
        ) { Text(stringResource(R.string.disconnect)) }

        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (ui.trips.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_trips_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(ui.trips, key = { it.id }) { trip -> TripRow(trip = trip) }
            }
        }
    }
}

@Composable
private fun OnTripView(
    ui: HomeUiState,
    onFinishTrip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.trip_in_progress),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        BigTimer(seconds = ui.elapsedSec, modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.trip_time),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.total_today),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = Formatters.money(ui.totalMinor),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onFinishTrip,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) { Text(stringResource(R.string.finish_trip)) }
    }
}
