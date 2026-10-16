package com.micuadre.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.micuadre.app.domain.model.Platform

/**
 * Bottom sheet that asks the user to pick a platform for a new trip.
 * Returns the picked [Platform] (and optional other label) via [onConfirm].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformPickerSheet(
    onDismiss: () -> Unit,
    onConfirm: (platform: Platform, otherLabel: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var selected by remember { mutableStateOf<Platform?>(null) }
    var otherLabel by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

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
                text = "Plataforma",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlatformChoice(
                    label = "InDriver",
                    selected = selected == Platform.INDRIVER,
                    onClick = { selected = Platform.INDRIVER },
                    modifier = Modifier.weight(1f)
                )
                PlatformChoice(
                    label = "Didi",
                    selected = selected == Platform.DIDI,
                    onClick = { selected = Platform.DIDI },
                    modifier = Modifier.weight(1f)
                )
                PlatformChoice(
                    label = "Otro",
                    selected = selected == Platform.OTHER,
                    onClick = { selected = Platform.OTHER },
                    modifier = Modifier.weight(1f)
                )
            }

            if (selected == Platform.OTHER) {
                OutlinedTextField(
                    value = otherLabel,
                    onValueChange = { otherLabel = it },
                    label = { Text("¿Cuál?") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }

            if (showError) {
                Text(
                    text = "Elegí plataforma",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
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
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        val s = selected
                        if (s == null) {
                            showError = true
                            return@Button
                        }
                        if (s == Platform.OTHER && otherLabel.isBlank()) {
                            showError = true
                            return@Button
                        }
                        onConfirm(s, otherLabel.trim().takeIf { s == Platform.OTHER && it.isNotEmpty() })
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Continuar")
                }
            }
        }
    }
}

@Composable
private fun PlatformChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content
        ),
        border = if (!selected) androidx.compose.foundation.BorderStroke(
            1.5.dp,
            MaterialTheme.colorScheme.outline
        ) else null
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
