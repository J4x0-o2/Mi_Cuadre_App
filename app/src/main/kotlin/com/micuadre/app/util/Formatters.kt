package com.micuadre.app.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Money and duration formatters.
 *
 * Money: COP, prefix `$`, es_CO locale → grouping `.`, decimal `,`.
 * Amounts are stored as `Long amountMinor` (centésimos de COP).
 */
object Formatters {

    private val coSymbols = DecimalFormatSymbols(Locale("es", "CO")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    private val moneyFormat = DecimalFormat("$ #,##0.00", coSymbols)
    private val moneyFormatNoDecimals = DecimalFormat("$ #,##0", coSymbols)

    /**
     * Formats an amount stored in minor units (centésimos) as COP.
     * `150050` minor → `$ 1.500,50`. Always shows two decimals for currency.
     */
    fun money(minor: Long): String = moneyFormat.format(minor / 100.0)

    /**
     * Formats an amount stored in minor units (centésimos) as COP without decimals.
     * `150000` minor → `$ 1.500`.
     */
    fun moneyNoDecimals(minor: Long): String = moneyFormatNoDecimals.format(minor / 100.0)

    /**
     * Parses a user-entered amount string into minor units.
     * Accepts both `1.500,50` (es_CO) and `1500.50` (en_US) shapes.
     * Returns null on invalid input.
     */
    fun parseAmount(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        // Normalize: if it has both '.' and ',', assume '.' is grouping and ',' is decimal.
        // If it has only ',', treat it as decimal.
        // If it has only '.', treat it as decimal (US style).
        val hasDot = trimmed.contains('.')
        val hasComma = trimmed.contains(',')

        val normalized = when {
            hasDot && hasComma -> trimmed.replace(".", "").replace(',', '.')
            hasComma -> trimmed.replace(',', '.')
            else -> trimmed
        }

        val asDouble = normalized.toDoubleOrNull() ?: return null
        if (asDouble < 0) return null
        // Round to nearest centésimo to avoid floating point drift.
        return Math.round(asDouble * 100.0)
    }

    /**
     * Formats a duration in seconds as HH:MM:SS.
     * Negative inputs are clamped to 0.
     */
    fun durationHMMSS(seconds: Long): String {
        val s = if (seconds < 0) 0 else seconds
        val hours = s / 3600
        val minutes = (s % 3600) / 60
        val secs = s % 60
        return "%02d:%02d:%02d".format(hours, minutes, secs)
    }

    /**
     * Formats a duration in seconds as MM:SS.
     * Caps minutes at 99 (i.e. durations >= ~100 minutes render as `99:59`).
     */
    fun durationMMSS(seconds: Long): String {
        val s = if (seconds < 0) 0 else seconds
        val totalMinutes = s / 60
        val minutes = if (totalMinutes > 99) 99 else totalMinutes
        val secs = s % 60
        return "%02d:%02d".format(minutes, secs)
    }
}
