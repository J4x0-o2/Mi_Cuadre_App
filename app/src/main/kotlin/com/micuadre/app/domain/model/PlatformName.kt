package com.micuadre.app.domain.model

/** Normalisation and comparison rules for platform names. */
object PlatformName {
    const val MAX_LENGTH = 30

    fun normalize(raw: String): String = raw.trim()

    /** Case-insensitive equality on normalised names, so "beat" and " BEAT " collide. */
    fun sameName(a: String, b: String): Boolean = normalize(a).equals(normalize(b), ignoreCase = true)
}
