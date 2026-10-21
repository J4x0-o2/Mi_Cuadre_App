package com.micuadre.app.domain.model

/** Normalisation and comparison rules for deduction names; same rules as [PlatformName]. */
object DeductionName {
    const val MAX_LENGTH = PlatformName.MAX_LENGTH

    fun normalize(raw: String): String = PlatformName.normalize(raw)

    /** Case-insensitive equality on normalised names, so "peaje" and " PEAJE " collide. */
    fun sameName(a: String, b: String): Boolean = PlatformName.sameName(a, b)
}
