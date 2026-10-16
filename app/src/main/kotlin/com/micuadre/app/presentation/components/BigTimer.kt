package com.micuadre.app.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Large monospace timer used in the connected screen. Format is HH:MM:SS.
 */
@Composable
fun BigTimer(
    seconds: Long,
    modifier: Modifier = Modifier
) {
    val text = "%02d:%02d:%02d".format(
        seconds / 3600,
        (seconds % 3600) / 60,
        seconds % 60
    )
    Box(modifier = modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
