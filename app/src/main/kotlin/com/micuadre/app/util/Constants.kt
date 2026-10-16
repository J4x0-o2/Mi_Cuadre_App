package com.micuadre.app.util

/**
 * Constants used across the app. Keeps magic numbers out of feature code.
 */
object Constants {
    const val NOTIFICATION_CHANNEL_ID: String = "shift_service_channel"
    const val NOTIFICATION_ID: Int = 1001

    /** How often the foreground notification and elapsed timer refresh (ms). */
    const val TICKER_INTERVAL_MS: Long = 1_000L

    /** Amount input accepted separators. We accept both comma and dot. */
    const val AMOUNT_GROUPING_SEPARATOR: Char = '.'
    const val AMOUNT_DECIMAL_SEPARATOR: Char = ','

    /** Locale used to format currency and durations. */
    const val LOCALE_LANGUAGE: String = "es"
    const val LOCALE_COUNTRY: String = "CO"
}