package com.micuadre.app.util

/** Test double for [Clock] with a manually controlled wall-clock value. */
class FakeClock(var now: Long = 0L) : Clock {
    override fun nowMs(): Long = now
}
