package com.micuadre.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.micuadre.app.R
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.presentation.home.TripRowModel
import com.micuadre.app.ui.theme.MiCuadreTheme
import com.micuadre.app.util.Formatters

/** "Carreras de hoy" row: 42dp platform badge, platform + "HH:mm · N min", amount + payment. */
@Composable
fun TripRow(
    model: TripRowModel,
    modifier: Modifier = Modifier
) {
    val platformLabel = when (model.platform) {
        Platform.INDRIVER -> stringResource(R.string.indriver)
        Platform.DIDI -> stringResource(R.string.didi)
        Platform.OTHER -> model.otherLabel ?: stringResource(R.string.other)
    }
    val paymentLabel = when (model.payment) {
        PaymentMethod.CASH -> stringResource(R.string.cash)
        PaymentMethod.TRANSFER -> stringResource(R.string.transfer)
        null -> "—"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface(RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = model.badge,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = platformLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = model.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = model.amountMinor?.let { Formatters.moneyNoDecimals(it) } ?: "—",
                style = MiCuadreTheme.typography.moneySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = paymentLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
