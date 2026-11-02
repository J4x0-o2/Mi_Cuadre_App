package com.micuadre.app.presentation.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micuadre.app.R
import com.micuadre.app.flowta.ConnectFlowTaContract
import com.micuadre.app.overlay.OverlayPermissionSettings
import com.micuadre.app.presentation.components.SectionLabel
import com.micuadre.app.presentation.components.cardSurface
import com.micuadre.app.presentation.settings.BubbleSettings
import com.micuadre.app.presentation.settings.FlowTaSyncEffect
import com.micuadre.app.presentation.settings.FlowTaSyncState
import com.micuadre.app.presentation.settings.FlowTaSyncViewModel
import com.micuadre.app.presentation.settings.SettingsViewModel
import com.micuadre.app.presentation.settings.ThemeOption
import com.micuadre.app.presentation.settings.ThemeOptions

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPlatforms: () -> Unit,
    onOpenDeductions: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    flowTaViewModel: FlowTaSyncViewModel = hiltViewModel()
) {
    val selected by viewModel.themeMode.collectAsStateWithLifecycle()
    val platformCounts by viewModel.platformCounts.collectAsStateWithLifecycle()
    val deductionNames by viewModel.deductionNames.collectAsStateWithLifecycle()
    val bubble by viewModel.bubble.collectAsStateWithLifecycle()
    val flowTa by flowTaViewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    // The user may have just granted (or revoked) the overlay permission in the system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResume()
        flowTaViewModel.onResume()
    }
    val context = LocalContext.current
    val connectLauncher = rememberLauncherForActivityResult(ConnectFlowTaContract()) { connected ->
        flowTaViewModel.onConnectResult(connected)
    }
    LaunchedEffect(Unit) {
        flowTaViewModel.effects.collect { effect ->
            when (effect) {
                FlowTaSyncEffect.LaunchConnect -> try {
                    connectLauncher.launch(Unit)
                } catch (e: ActivityNotFoundException) {
                    flowTaViewModel.onLaunchFailed()
                } catch (e: SecurityException) {
                    flowTaViewModel.onLaunchFailed()
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.permissionRequests.collect {
            if (!OverlayPermissionSettings.open(context)) viewModel.onPermissionSettingsUnavailable()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsStateWithLifecycle()
    val messageText = message?.let { stringResource(it.textRes) }
    LaunchedEffect(message?.id) {
        val current = message
        if (current != null && messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.onMessageShown(current.id)
        }
    }

    val flowTaMessage by flowTaViewModel.message.collectAsStateWithLifecycle()
    val flowTaMessageText = flowTaMessage?.let { stringResource(it.textRes) }
    LaunchedEffect(flowTaMessage?.id) {
        val current = flowTaMessage
        if (current != null && flowTaMessageText != null) {
            snackbarHostState.showSnackbar(flowTaMessageText)
            flowTaViewModel.onMessageShown(current.id)
        }
    }

    if (flowTa.showInstallDialog) {
        FlowTaInstallDialog(
            onDownload = {
                flowTaViewModel.onInstallDialogDismissed()
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FLOWTA_RELEASES_URL)))
                } catch (e: ActivityNotFoundException) {
                    // No browser installed; nothing else to offer.
                }
            },
            onDismiss = flowTaViewModel::onInstallDialogDismissed
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
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
                        contentDescription = stringResource(R.string.settings_back_cd),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            SectionLabel(
                text = stringResource(R.string.settings_appearance),
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .cardSurface(MaterialTheme.shapes.large)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThemeOptions.all.forEach { option ->
                    ThemeOptionRow(
                        option = option,
                        selected = option.mode == selected,
                        onSelect = { viewModel.onSelect(option.mode) }
                    )
                }
            }
            SectionLabel(
                text = stringResource(R.string.settings_quick_access),
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
            BubbleRow(
                bubble = bubble,
                onToggle = viewModel::onBubbleToggled,
                onRetryPermission = viewModel::onBubblePermissionRetry
            )
            SectionLabel(
                text = stringResource(R.string.settings_integrations),
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
            FlowTaRow(
                state = flowTa,
                onToggle = flowTaViewModel::onToggled,
                onReconnect = flowTaViewModel::onReconnectClicked
            )
            SectionLabel(
                text = stringResource(R.string.settings_platforms),
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
            PlatformsRow(
                summary = platformCounts?.let { counts ->
                    if (counts.total == 0) {
                        stringResource(R.string.settings_platforms_none)
                    } else {
                        pluralStringResource(
                            R.plurals.settings_platforms_summary, counts.visible, counts.visible, counts.total
                        )
                    }
                },
                onClick = onOpenPlatforms
            )
            // Same card shape as the platforms row, separated by the 8 dp gap used between sibling cards.
            Spacer(Modifier.height(8.dp))
            SettingsLinkRow(
                title = stringResource(R.string.settings_deductions_manage),
                summary = deductionNames?.let { names ->
                    if (names.isEmpty()) stringResource(R.string.settings_deductions_none) else names.joinToString(", ")
                },
                onClick = onOpenDeductions
            )
            SectionLabel(
                text = stringResource(R.string.settings_history),
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
            SettingsLinkRow(
                title = stringResource(R.string.settings_history),
                summary = stringResource(R.string.settings_history_summary),
                onClick = onOpenHistory
            )
            SectionLabel(
                text = stringResource(R.string.settings_about),
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .cardSurface(MaterialTheme.shapes.large)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_version),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = viewModel.versionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BubbleRow(
    bubble: BubbleSettings,
    onToggle: (Boolean) -> Unit,
    onRetryPermission: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            // While the permission is pending, tapping the row sends the user back to the system page.
            .clickable(role = Role.Button) { if (bubble.permissionPending) onRetryPermission() else onToggle(!bubble.enabled) }
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = stringResource(R.string.settings_bubble_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(
                    if (bubble.permissionPending) R.string.settings_bubble_permission_pending
                    else R.string.settings_bubble_subtitle
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = bubble.enabled, onCheckedChange = onToggle)
    }
}

private const val FLOWTA_RELEASES_URL = "https://github.com/J4x0-o2/FlowTa/releases"

@Composable
private fun FlowTaRow(state: FlowTaSyncState, onToggle: (Boolean) -> Unit, onReconnect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onToggle(!state.enabled) }
                .heightIn(min = 56.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.settings_flowta_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        if (state.showReconnect) R.string.settings_flowta_reconnect_hint
                        else R.string.settings_flowta_subtitle
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = state.enabled, onCheckedChange = onToggle)
        }
        if (state.showReconnect) {
            TextButton(onClick = onReconnect, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.settings_flowta_reconnect))
            }
        }
    }
}

@Composable
private fun FlowTaInstallDialog(onDownload: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_flowta_install_title)) },
        text = { Text(stringResource(R.string.settings_flowta_install_message)) },
        confirmButton = {
            TextButton(onClick = onDownload) { Text(stringResource(R.string.settings_flowta_install_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_flowta_install_dismiss)) }
        }
    )
}

@Composable
private fun PlatformsRow(summary: String?, onClick: () -> Unit) =
    SettingsLinkRow(title = stringResource(R.string.settings_platforms_manage), summary = summary, onClick = onClick)

@Composable
private fun SettingsLinkRow(title: String, summary: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemeOptionRow(option: ThemeOption, selected: Boolean, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = stringResource(option.labelRes),
                style = MaterialTheme.typography.titleSmall,
                color = content
            )
            Text(
                text = stringResource(option.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) content else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
