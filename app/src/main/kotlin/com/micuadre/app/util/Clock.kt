package com.micuadre.app.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source of "now" for everything that is persisted or displayed as elapsed
 * time. Values are wall-clock epoch milliseconds so they stay meaningful
 * across process death and device reboots (unlike boot-relative clocks).
 */
interface Clock {
    fun nowMs(): Long
}

/** Production [Clock] backed by [System.currentTimeMillis]. */
@Singleton
class SystemWallClock @Inject constructor() : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
