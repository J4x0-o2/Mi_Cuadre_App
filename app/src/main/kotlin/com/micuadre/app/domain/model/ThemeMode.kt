package com.micuadre.app.domain.model

/** How the app decides between its light and dark palettes. */
enum class ThemeMode {
    /** Follow the phone's dark/light setting. Default. */
    SYSTEM,
    LIGHT,
    DARK,

    /** Dark from 18:00 to 06:00, light otherwise. */
    SCHEDULED;

    companion object {
        /** Unknown, missing or corrupt stored values fall back to [SYSTEM]. */
        fun fromStored(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
