package com.micuadre.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micuadre.app.R
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.presentation.finish.AmountInput
import com.micuadre.app.presentation.finish.FinishSheetState
import com.micuadre.app.presentation.finish.FinishSheetSubtitle
import com.micuadre.app.ui.theme.MiCuadreTheme

/**
 * Bottom sheet that closes a trip: amount (autofocused, numeric keyboard),
 * platform (last used preselected) and payment method.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishTripSheet(
    state: FinishSheetState,
    onDismiss: () -> Unit,
    onConfirm: (platform: Platform, otherLabel: String?, amountMinor: Long, paymentMethod: PaymentMethod) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amountField by remember { mutableStateOf(TextFieldValue("")) }
    var platform by remember { mutableStateOf(state.initialSelection.platform) }
    var otherLabel by remember { mutableStateOf(state.initialSelection.otherLabel.orEmpty()) }
    var payment by remember { mutableStateOf<PaymentMethod?>(null) }
    var showAmountError by remember { mutableStateOf(false) }
    var showPaymentError by remember { mutableStateOf(false) }
    var showOtherError by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    val subtitle = remember(state) {
        FinishSheetSubtitle.format(state.tripStartedAtMs, state.tripEndedAtMs)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.finish_sheet_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close_sheet),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionLabel(
                text = stringResource(R.string.trip_amount),
                modifier = Modifier.padding(top = 20.dp)
            )
            AmountField(
                value = amountField,
                isError = showAmountError,
                focusRequester = focusRequester,
                onValueChange = { input ->
                    val formatted = AmountInput.format(input.text)
                    amountField = TextFieldValue(formatted, TextRange(formatted.length))
                    showAmountError = false
                },
                modifier = Modifier.padding(top = 8.dp)
            )
            if (showAmountError) {
                ErrorText(stringResource(R.string.amount_required))
            }

            SectionLabel(
                text = stringResource(R.string.platform_label),
                modifier = Modifier.padding(top = 20.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlatformChip(stringResource(R.string.indriver), platform == Platform.INDRIVER, Modifier.weight(1f)) {
                    platform = Platform.INDRIVER
                    showOtherError = false
                }
                PlatformChip(stringResource(R.string.didi), platform == Platform.DIDI, Modifier.weight(1f)) {
                    platform = Platform.DIDI
                    showOtherError = false
                }
                PlatformChip(stringResource(R.string.other), platform == Platform.OTHER, Modifier.weight(1f)) {
                    platform = Platform.OTHER
                }
            }
            if (platform == Platform.OTHER) {
                OutlinedTextField(
                    value = otherLabel,
                    onValueChange = {
                        otherLabel = it
                        showOtherError = false
                    },
                    label = { Text(stringResource(R.string.other_platform_hint)) },
                    singleLine = true,
                    isError = showOtherError,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
                if (showOtherError) {
                    ErrorText(stringResource(R.string.other_platform_required))
                }
            }

            SectionLabel(
                text = stringResource(R.string.payment_method),
                modifier = Modifier.padding(top = 20.dp)
            )
            PaymentSegmented(
                selected = payment,
                onSelect = {
                    payment = it
                    showPaymentError = false
                },
                modifier = Modifier.padding(top = 8.dp)
            )
            if (showPaymentError) {
                ErrorText(stringResource(R.string.payment_required))
            }

            Button(
                onClick = {
                    val minor = AmountInput.toMinor(amountField.text)
                    val method = payment
                    val label = otherLabel.trim()
                    var invalid = false
                    if (minor == null) { showAmountError = true; invalid = true }
                    if (method == null) { showPaymentError = true; invalid = true }
                    if (platform == Platform.OTHER && label.isEmpty()) { showOtherError = true; invalid = true }
                    if (invalid) return@Button
                    onConfirm(platform, label.takeIf { platform == Platform.OTHER }, minor!!, method!!)
                },
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .height(68.dp)
            ) {
                Text(stringResource(R.string.save_trip), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun AmountField(
    value: TextFieldValue,
    isError: Boolean,
    focusRequester: FocusRequester,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val amountStyle = MiCuadreTheme.typography.moneyLarge.copy(
        fontSize = 40.sp,
        lineHeight = 48.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .border(BorderStroke(2.dp, borderColor), RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$",
            style = amountStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.padding(end = 8.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = amountStyle,
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.amount_hint),
                            style = amountStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
private fun PlatformChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .height(52.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                shape
            )
            .border(BorderStroke(if (selected) 2.dp else 1.dp, borderColor), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PaymentSegmented(
    selected: PaymentMethod?,
    onSelect: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PaymentSegment(stringResource(R.string.cash), selected == PaymentMethod.CASH, Modifier.weight(1f)) {
            onSelect(PaymentMethod.CASH)
        }
        PaymentSegment(stringResource(R.string.transfer), selected == PaymentMethod.TRANSFER, Modifier.weight(1f)) {
            onSelect(PaymentMethod.TRANSFER)
        }
    }
}

@Composable
private fun PaymentSegment(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(52.dp)
            .background(if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 4.dp)
    )
}
