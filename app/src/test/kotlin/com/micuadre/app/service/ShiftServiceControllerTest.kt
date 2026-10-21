package com.micuadre.app.service

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShiftServiceControllerTest {

    private fun intentWith(amounts: Map<Long, Long>) =
        Intent().putExtras(ShiftServiceController.endShiftExtras(amounts))

    @Test
    fun `end shift amounts survive the intent round trip`() {
        val amounts = mapOf(3L to 1_500_000L, 1L to 850_000L)

        assertEquals(amounts, ShiftServiceController.deductionAmountsFrom(intentWith(amounts)))
    }

    @Test
    fun `no amounts and a plain intent both read as empty`() {
        assertEquals(emptyMap<Long, Long>(), ShiftServiceController.deductionAmountsFrom(intentWith(emptyMap())))
        assertEquals(emptyMap<Long, Long>(), ShiftServiceController.deductionAmountsFrom(Intent()))
    }

    @Test
    fun `mismatched id and amount arrays are discarded as a whole`() {
        val intent = Intent()
            .putExtra(ShiftServiceController.EXTRA_DEDUCTION_IDS, longArrayOf(1L, 2L))
            .putExtra(ShiftServiceController.EXTRA_DEDUCTION_AMOUNTS, longArrayOf(100L))

        assertEquals(emptyMap<Long, Long>(), ShiftServiceController.deductionAmountsFrom(intent))
    }

    @Test
    fun `endShift starts the service with the end action and the amounts`() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        ShiftServiceController(app).endShift(mapOf(2L to 700_000L))

        val sent = shadowOf(app).nextStartedService
        assertEquals(ShiftServiceController.ACTION_END_SHIFT, sent.action)
        assertEquals(mapOf(2L to 700_000L), ShiftServiceController.deductionAmountsFrom(sent))
    }

    @Test
    fun `endShift without amounts carries no extras`() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        ShiftServiceController(app).endShift()

        val sent = shadowOf(app).nextStartedService
        assertEquals(ShiftServiceController.ACTION_END_SHIFT, sent.action)
        assertNull(sent.extras?.getLongArray(ShiftServiceController.EXTRA_DEDUCTION_IDS))
    }
}
