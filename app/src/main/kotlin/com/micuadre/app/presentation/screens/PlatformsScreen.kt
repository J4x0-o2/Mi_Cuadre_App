package com.micuadre.app.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micuadre.app.R
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.PlatformName
import com.micuadre.app.presentation.components.cardSurface
import com.micuadre.app.presentation.platforms.PlatformDialog
import com.micuadre.app.presentation.platforms.PlatformNameError
import com.micuadre.app.presentation.platforms.PlatformsViewModel

@Composable
fun PlatformsScreen(
    onBack: () -> Unit,
    viewModel: PlatformsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

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
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back_cd),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = stringResource(R.string.platforms_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                text = stringResource(R.string.platforms_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
            )
            if (state.platforms.isEmpty() && !state.isLoading) {
                Text(
                    text = stringResource(R.string.platforms_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cardSurface(MaterialTheme.shapes.large)
                        .padding(vertical = 4.dp)
                ) {
                    state.platforms.forEach { platform ->
                        PlatformRow(
                            platform = platform,
                            onToggleVisible = { viewModel.onToggleVisible(platform.id, it) },
                            onRename = { viewModel.onRenameClick(platform.id) },
                            onDelete = { viewModel.onDeleteClick(platform.id) }
                        )
                    }
                }
            }
            if (state.showNoneVisibleHint) {
                Text(
                    text = stringResource(R.string.platforms_none_visible),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Button(
                onClick = viewModel::onAddClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(top = 16.dp)
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.platforms_add),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    when (val dialog = state.dialog) {
        is PlatformDialog.Add -> NameDialog(
            title = stringResource(R.string.platforms_add_title),
            initialName = "",
            error = dialog.error,
            saving = state.saving,
            onNameEdited = viewModel::onNameEdited,
            onConfirm = viewModel::onConfirmAdd,
            onDismiss = viewModel::onDismissDialog
        )
        is PlatformDialog.Rename -> NameDialog(
            title = stringResource(R.string.platforms_rename_title),
            initialName = dialog.currentName,
            error = dialog.error,
            saving = state.saving,
            onNameEdited = viewModel::onNameEdited,
            onConfirm = viewModel::onConfirmRename,
            onDismiss = viewModel::onDismissDialog
        )
        is PlatformDialog.Delete -> AlertDialog(
            onDismissRequest = viewModel::onDismissDialog,
            title = { Text(stringResource(R.string.platforms_delete_title, dialog.name)) },
            text = { Text(stringResource(R.string.platforms_delete_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmDelete, enabled = !state.saving) {
                    Text(
                        text = stringResource(R.string.platforms_delete_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissDialog) {
                    Text(stringResource(R.string.platforms_cancel))
                }
            }
        )
        null -> Unit
    }
}

@Composable
private fun PlatformRow(
    platform: Platform,
    onToggleVisible: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val visibleLabel = stringResource(R.string.platforms_visible_cd, platform.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = platform.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRename) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.platforms_rename_cd, platform.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.platforms_delete_cd, platform.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = platform.visible,
            onCheckedChange = onToggleVisible,
            modifier = Modifier.semantics { contentDescription = visibleLabel }
        )
    }
}

/** Shared by "add" and "rename": a single text field with the validation error shown inline. */
@Composable
private fun NameDialog(
    title: String,
    initialName: String,
    error: PlatformNameError?,
    saving: Boolean,
    onNameEdited: () -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    onNameEdited()
                },
                label = { Text(stringResource(R.string.platforms_name_label)) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(errorText(it)) } },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onConfirm(name) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = !saving) {
                Text(stringResource(R.string.platforms_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.platforms_cancel))
            }
        }
    )
}

@Composable
private fun errorText(error: PlatformNameError): String = when (error) {
    PlatformNameError.Blank -> stringResource(R.string.platforms_error_blank)
    PlatformNameError.Duplicate -> stringResource(R.string.platforms_error_duplicate)
    PlatformNameError.TooLong -> stringResource(R.string.platforms_error_too_long, PlatformName.MAX_LENGTH)
}
