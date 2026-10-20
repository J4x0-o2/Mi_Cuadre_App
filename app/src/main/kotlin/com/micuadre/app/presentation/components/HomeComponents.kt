package com.micuadre.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micuadre.app.R
import com.micuadre.app.ui.theme.MiCuadreTheme

/** Surface + 1dp outlineVariant border: the card look shared by every Home block. */
@Composable
fun Modifier.cardSurface(shape: Shape): Modifier = this
    .background(MaterialTheme.colorScheme.surface, shape)
    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)

/** Status shown at the right of the header. */
enum class HeaderStatus { IN_SHIFT, ON_TRIP }

/** App name on the left, optional status pill and settings button on the right. */
@Composable
fun ScreenHeader(
    status: HeaderStatus?,
    onSettingsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        when (status) {
            HeaderStatus.IN_SHIFT -> StatusPill(
                text = stringResource(R.string.status_in_shift),
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                dot = MaterialTheme.colorScheme.primary
            )
            HeaderStatus.ON_TRIP -> StatusPill(
                text = stringResource(R.string.status_on_trip),
                container = MiCuadreTheme.colors.goldContainer,
                content = MiCuadreTheme.colors.onGoldContainer,
                dot = MiCuadreTheme.colors.gold
            )
            null -> Unit
        }
        if (onSettingsClick != null) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.padding(start = 8.dp).size(44.dp)
            ) {
                Icon(
                    imageVector = SettingsIcon,
                    contentDescription = stringResource(R.string.settings_open_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, container: Color, content: Color, dot: Color) {
    Row(
        modifier = Modifier
            .background(container, CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).background(dot, CircleShape))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/**
 * Label + value tile (radius 22). By default a bordered card; with [onCard] it sits inside
 * another card on a surfaceVariant fill (the cash / transfer split). [compact] trims the padding
 * and label size so three tiles fit in a row.
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MiCuadreTheme.typography.money,
    onCard: Boolean = false,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(22.dp)
    val base = if (onCard) {
        modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape)
    } else {
        modifier.cardSurface(shape)
    }
    Column(modifier = base.padding(horizontal = if (compact) 12.dp else 16.dp, vertical = 14.dp)) {
        SectionLabel(text = label, fontSize = if (compact) 11.sp else 13.sp)
        Text(
            text = value,
            style = valueStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** Full-width filled primary action with a leading icon (play / check). */
@Composable
fun PrimaryActionButton(
    text: String,
    icon: ImageVector?,
    height: Dp,
    cornerRadius: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(cornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (icon != null) Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Text(text = text, style = MaterialTheme.typography.titleMedium)
        }
    }
}
