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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.micuadre.app.R
import com.micuadre.app.domain.model.Deduction
import com.micuadre.app.domain.model.DeductionName
import com.micuadre.app.presentation.components.cardSurface
import com.micuadre.app.presentation.deductions.DeductionDialog
import com.micuadre.app.presentation.deductions.DeductionFormError
import com.micuadre.app.presentation.deductions.DeductionSecondary
import com.micuadre.app.presentation.deductions.DeductionToggleA11y
import com.micuadre.app.presentation.deductions.DeductionsViewModel
import com.micuadre.app.presentation.deductions.secondaryLine
import com.micuadre.app.presentation.finish.AmountInput
import com.micuadre.app.util.Formatters

@Composable
fun DeductionsScreen(
    onBack: () -> Unit,
    viewModel: DeductionsViewModel = hiltViewModel()
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
                    text = stringResource(R.string.deductions_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                text = stringResource(R.string.deductions_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
            )
            if (state.deductions.isEmpty() && !state.isLoading) {
                Text(
                    text = stringResource(R.string.deductions_empty),
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
                    state.deductions.forEach { deduction ->
                        DeductionRow(
                            deduction = deduction,
                            onToggleVisible = { viewModel.onToggleVisible(deduction.id, it) },
                            onEdit = { viewModel.onEditClick(deduction.id) },
                            onDelete = { viewModel.onDeleteClick(deduction.id) }
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.deductions_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
            Button(
                onClick = viewModel::onAddClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(top = 16.dp)
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.deductions_add),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    when (val dialog = state.dialog) {
        is DeductionDialog.Add -> DeductionFormDialog(
            title = stringResource(R.string.deductions_add_title),
            initialName = "",
            initialAmount = "",
            error = dialog.error,
            saving = state.saving,
            onEdited = viewModel::onFormEdited,
            onConfirm = viewModel::onConfirmAdd,
            onDismiss = viewModel::onDismissDialog
        )
        is DeductionDialog.Edit -> DeductionFormDialog(
            title = stringResource(R.string.deductions_edit_title),
            initialName = dialog.currentName,
            initialAmount = dialog.currentAmount,
            error = dialog.error,
            saving = state.saving,
            onEdited = viewModel::onFormEdited,
            onConfirm = viewModel::onConfirmEdit,
            onDismiss = viewModel::onDismissDialog
        )
        is DeductionDialog.Delete -> AlertDialog(
            onDismissRequest = viewModel::onDismissDialog,
            title = { Text(stringResource(R.string.deductions_delete_title, dialog.name)) },
            text = { Text(stringResource(R.string.deductions_delete_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmDelete, enabled = !state.saving) {
                    Text(
                        text = stringResource(R.string.deductions_delete_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissDialog) {
                    Text(stringResource(R.string.deductions_cancel))
                }
            }
        )
        null -> Unit
    }
}

@Composable
private fun DeductionRow(
    deduction: Deduction,
    onToggleVisible: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val toggleLabel = stringResource(DeductionToggleA11y.LABEL_RES, deduction.name)
    val toggleState = stringResource(DeductionToggleA11y.stateRes(deduction.visible))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text = deduction.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when (val line = deduction.secondaryLine()) {
                    is DeductionSecondary.Usual ->
                        stringResource(R.string.deductions_usual, Formatters.moneyNoDecimals(line.amountMinor))
                    DeductionSecondary.NoUsual -> stringResource(R.string.deductions_no_usual)
                    DeductionSecondary.Hidden -> stringResource(R.string.deductions_hidden)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.deductions_edit_cd, deduction.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.deductions_delete_cd, deduction.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = deduction.visible,
            onCheckedChange = onToggleVisible,
            modifier = Modifier.semantics {
                contentDescription = toggleLabel
                stateDescription = toggleState
            }
        )
    }
}

/** Shared by "add" and "edit": name and usual amount fields with the validation error shown inline. */
@Composable
private fun DeductionFormDialog(
    title: String,
    initialName: String,
    initialAmount: String,
    error: DeductionFormError?,
    saving: Boolean,
    onEdited: () -> Unit,
    onConfirm: (name: String, amount: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var amount by rememberSaveable { mutableStateOf(initialAmount) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onEdited()
                    },
                    label = { Text(stringResource(R.string.deductions_name_label)) },
                    singleLine = true,
                    isError = error != null && error != DeductionFormError.InvalidAmount,
                    supportingText = nameErrorText(error)?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = AmountInput.format(it)
                        onEdited()
                    },
                    label = { Text(stringResource(R.string.deductions_amount_label)) },
                    prefix = { Text("$") },
                    singleLine = true,
                    isError = error == DeductionFormError.InvalidAmount,
                    supportingText = {
                        Text(
                            stringResource(
                                if (error == DeductionFormError.InvalidAmount) R.string.deductions_error_amount
                                else R.string.deductions_amount_helper
                            )
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(name, amount) }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, amount) }, enabled = !saving) {
                Text(stringResource(R.string.deductions_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.deductions_cancel))
            }
        }
    )
}

/** Error text for the name field; the amount error is shown under the amount field instead. */
@Composable
private fun nameErrorText(error: DeductionFormError?): String? = when (error) {
    DeductionFormError.Blank -> stringResource(R.string.deductions_error_blank)
    DeductionFormError.Duplicate -> stringResource(R.string.deductions_error_duplicate)
    DeductionFormError.TooLong -> stringResource(R.string.deductions_error_too_long, DeductionName.MAX_LENGTH)
    DeductionFormError.InvalidAmount, null -> null
}
