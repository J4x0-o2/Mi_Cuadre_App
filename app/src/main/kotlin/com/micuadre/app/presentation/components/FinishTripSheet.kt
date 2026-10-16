package com.micuadre.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.util.Formatters

/**
 * Bottom sheet for finalizing a trip. Captures amount (COP) and payment
 * method, and confirms with the caller via [onConfirm].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishTripSheet(
    platform: Platform,
    platformOtherLabel: String?,
    onDismiss: () -> Unit,
    onConfirm: (amountMinor: Long, paymentMethod: PaymentMethod) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var amountText by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf<PaymentMethod?>(null) }
    var showAmountError by remember { mutableStateOf(false) }
    var showPaymentError by remember { mutableStateOf(false) }

    val platformLabel = when (platform) {
        Platform.INDRIVER -> "InDriver"
        Platform.DIDI -> "Didi"
        Platform.OTHER -> platformOtherLabel ?: "Otro"
    }

    LaunchedEffect(Unit) { /* hook for future analytics */ }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Finalizar carrera",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = platformLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = "Monto de la carrera",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 24.dp)
            )
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    showAmountError = false
                },
                placeholder = { Text("0") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = showAmountError,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            if (showAmountError) {
                Text(
                    text = "Ingresá el monto",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Text(
                text = "Método de pago",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 24.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PaymentChoice(
                    label = "Efectivo",
                    selected = payment == PaymentMethod.CASH,
                    onClick = {
                        payment = PaymentMethod.CASH
                        showPaymentError = false
                    },
                    modifier = Modifier.weight(1f)
                )
                PaymentChoice(
                    label = "Transferencia",
                    selected = payment == PaymentMethod.TRANSFER,
                    onClick = {
                        payment = PaymentMethod.TRANSFER
                        showPaymentError = false
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            if (showPaymentError) {
                Text(
                    text = "Elegí método de pago",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancelar") }
                Button(
                    onClick = {
                        val parsed = Formatters.parseAmount(amountText)
                        val p = payment
                        var invalid = false
                        if (parsed == null || parsed <= 0L) {
                            showAmountError = true
                            invalid = true
                        }
                        if (p == null) {
                            showPaymentError = true
                            invalid = true
                        }
                        if (invalid) return@Button
                        onConfirm(parsed!!, p!!)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Guardar carrera") }
            }
        }
    }
}

@Composable
private fun PaymentChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .background(
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = container,
                contentColor = content
            ),
            border = if (!selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline) else BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        ) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
