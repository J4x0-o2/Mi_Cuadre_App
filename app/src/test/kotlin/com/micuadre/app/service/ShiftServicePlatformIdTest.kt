package com.micuadre.app.service

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Parsing of the platform id carried by a finish-trip service intent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShiftServicePlatformIdTest {

    private fun intentWith(block: Intent.() -> Unit) =
        Intent(ShiftServiceController.ACTION_FINISH_TRIP).apply(block)

    @Test
    fun `missing extra yields null`() {
        assertNull(ShiftServiceController.platformIdFrom(intentWith { }))
    }

    @Test
    fun `valid id is returned`() {
        val intent = intentWith { putExtra(ShiftServiceController.EXTRA_PLATFORM_ID, 7L) }

        assertEquals(7L, ShiftServiceController.platformIdFrom(intent))
    }

    @Test
    fun `non-positive ids are rejected`() {
        val zero = intentWith { putExtra(ShiftServiceController.EXTRA_PLATFORM_ID, 0L) }
        val negative = intentWith { putExtra(ShiftServiceController.EXTRA_PLATFORM_ID, -3L) }

        assertNull(ShiftServiceController.platformIdFrom(zero))
        assertNull(ShiftServiceController.platformIdFrom(negative))
    }

    @Test
    fun `an extra of the wrong type yields null`() {
        val intent = intentWith { putExtra(ShiftServiceController.EXTRA_PLATFORM_ID, "7") }

        assertNull(ShiftServiceController.platformIdFrom(intent))
    }
}
