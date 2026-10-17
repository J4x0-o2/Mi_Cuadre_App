package com.micuadre.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.util.Formatters

@Composable
fun TripRow(
    trip: Trip,
    modifier: Modifier = Modifier
) {
    val platformLabel = when (trip.platform) {
        Platform.INDRIVER -> stringResource(R.string.indriver)
        Platform.DIDI -> stringResource(R.string.didi)
        Platform.OTHER -> trip.platformOtherLabel ?: stringResource(R.string.other)
    }
    val paymentLabel = when (trip.paymentMethod) {
        PaymentMethod.CASH -> stringResource(R.string.cash)
        PaymentMethod.TRANSFER -> stringResource(R.string.transfer)
        null -> "—"
    }
    val durationSec = trip.durationMs / 1000L

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.padding(end = 12.dp)) {
                Text(
                    text = platformLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$paymentLabel · ${Formatters.durationMMSS(durationSec)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = trip.amountMinor?.let { Formatters.money(it) } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
