package com.micuadre.app.presentation.finish

/**
 * Whole-peso amount typed in the finish-trip sheet.
 *
 * The field only holds digits; the dot is a display-only thousands separator
 * ("18.500"). Persistence uses minor units (centésimos), so pesos * 100.
 */
object AmountInput {

    /** Nine digits (< 1.000 millones COP) keeps pesos * 100 far from Long overflow. */
    private const val MAX_DIGITS = 9

    /** Keeps digits only, drops leading zeros, caps the length and groups thousands with a dot. */
    fun format(raw: String): String {
        val digits = raw.filter { it in '0'..'9' }.trimStart('0').take(MAX_DIGITS)
        return digits.reversed().chunked(3).joinToString(".").reversed()
    }

    /** Converts a (formatted) peso amount to minor units; null when empty or zero. */
    fun toMinor(formatted: String): Long? {
        val pesos = format(formatted).replace(".", "").toLongOrNull() ?: return null
        return if (pesos > 0L) pesos * 100L else null
    }
}
