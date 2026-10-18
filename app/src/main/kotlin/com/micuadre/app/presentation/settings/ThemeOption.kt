package com.micuadre.app.presentation.settings

import androidx.annotation.StringRes
import com.micuadre.app.R
import com.micuadre.app.domain.model.ThemeMode

/** One selectable row of the appearance card. */
data class ThemeOption(
    val mode: ThemeMode,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int
)

object ThemeOptions {
    val all: List<ThemeOption> = listOf(
        ThemeOption(ThemeMode.SYSTEM, R.string.theme_system, R.string.theme_system_desc),
        ThemeOption(ThemeMode.LIGHT, R.string.theme_light, R.string.theme_light_desc),
        ThemeOption(ThemeMode.DARK, R.string.theme_dark, R.string.theme_dark_desc),
        ThemeOption(ThemeMode.SCHEDULED, R.string.theme_scheduled, R.string.theme_scheduled_desc)
    )
}
