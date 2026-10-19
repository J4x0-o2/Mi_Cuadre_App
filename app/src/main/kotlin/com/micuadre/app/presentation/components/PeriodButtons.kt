package com.micuadre.app.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.micuadre.app.R
import com.micuadre.app.domain.model.Period
import com.micuadre.app.domain.model.PeriodSummary
import com.micuadre.app.ui.theme.MiCuadreTheme
import com.micuadre.app.util.Formatters

/**
 * Two equal "Esta semana" / "Este mes" buttons, each showing the period total. Tapping one asks
 * for that period's detail. Amounts read "—" until the summary has loaded.
 */
@Composable
fun PeriodButtons(
    week: PeriodSummary?,
    month: PeriodSummary?,
    onPeriodTapped: (Period) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PeriodButton(
            label = stringResource(R.string.period_week),
            totalMinor = week?.totalMinor,
            onClick = { onPeriodTapped(Period.WEEK) },
            modifier = Modifier.weight(1f)
        )
        PeriodButton(
            label = stringResource(R.string.period_month),
            totalMinor = month?.totalMinor,
            onClick = { onPeriodTapped(Period.MONTH) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PeriodButton(
    label: String,
    totalMinor: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .cardSurface(shape)
            .clickable(onClickLabel = stringResource(R.string.period_open_detail), role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SectionLabel(text = label)
            Text(
                text = totalMinor?.let(Formatters::moneyNoDecimals) ?: stringResource(R.string.per_hour_unknown),
                style = MiCuadreTheme.typography.moneySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
