package com.micuadre.app.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Uppercase muted section label (13sp by default) used across screens. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 13.sp) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(fontSize = fontSize, letterSpacing = 0.4.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
