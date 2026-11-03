package com.micuadre.app.flowta

import android.os.Bundle
import com.micuadre.app.data.local.entity.PendingFlowTaIncomeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FlowTaSendResultTest {

    private fun reply(status: String?, message: String? = null) = Bundle().apply {
        status?.let { putString("status", it) }
        message?.let { putString("message", it) }
    }

    @Test
    fun `OK and DUPLICATE are delivered`() {
        assertEquals(FlowTaSendResult.Delivered, FlowTaSendResult.fromReply(reply("OK")))
        assertEquals(FlowTaSendResult.Delivered, FlowTaSendResult.fromReply(reply("DUPLICATE")))
    }

    @Test
    fun `NOT_LINKED maps to NotLinked`() {
        assertEquals(FlowTaSendResult.NotLinked, FlowTaSendResult.fromReply(reply("NOT_LINKED")))
    }

    @Test
    fun `INVALID is rejected with its message`() {
        assertEquals(FlowTaSendResult.Rejected("bad amount"), FlowTaSendResult.fromReply(reply("INVALID", "bad amount")))
        assertEquals(FlowTaSendResult.Rejected("INVALID"), FlowTaSendResult.fromReply(reply("INVALID")))
    }

    @Test
    fun `ERROR, unknown status and missing status are row failures`() {
        assertTrue(FlowTaSendResult.fromReply(reply("ERROR", "db locked")) is FlowTaSendResult.RowFailure)
        assertTrue(FlowTaSendResult.fromReply(reply("WHATEVER")) is FlowTaSendResult.RowFailure)
        assertTrue(FlowTaSendResult.fromReply(reply(null)) is FlowTaSendResult.RowFailure)
    }

    @Test
    fun `a null reply is an environment failure`() {
        assertEquals(FlowTaSendResult.EnvironmentFailure("NO_REPLY"), FlowTaSendResult.fromReply(null))
    }

    @Test
    fun `failure reasons say what happened`() {
        assertEquals(
            FlowTaSendResult.RowFailure("ERROR: db locked"),
            FlowTaSendResult.fromReply(reply("ERROR", "db locked"))
        )
        assertEquals(
            FlowTaSendResult.RowFailure("UNKNOWN_STATUS: WHATEVER"),
            FlowTaSendResult.fromReply(reply("WHATEVER"))
        )
    }

    @Test
    fun `every thrown failure is an environment failure with the exception name`() {
        assertEquals(
            FlowTaSendResult.EnvironmentFailure("SecurityException: key mismatch"),
            FlowTaSendResult.fromFailure(SecurityException("key mismatch"))
        )
        assertEquals(
            FlowTaSendResult.EnvironmentFailure("IllegalArgumentException: Unknown authority"),
            FlowTaSendResult.fromFailure(IllegalArgumentException("Unknown authority"))
        )
        assertEquals(
            FlowTaSendResult.EnvironmentFailure("IllegalStateException: boom"),
            FlowTaSendResult.fromFailure(IllegalStateException("boom"))
        )
    }

    @Test
    fun `reply keys and status values live in the contract`() {
        assertEquals("status", FlowTaIncomeContract.KEY_STATUS)
        assertEquals("message", FlowTaIncomeContract.KEY_MESSAGE)
        assertEquals(
            listOf("OK", "DUPLICATE", "NOT_LINKED", "INVALID", "ERROR", "LINKED"),
            listOf(
                FlowTaIncomeContract.STATUS_OK, FlowTaIncomeContract.STATUS_DUPLICATE,
                FlowTaIncomeContract.STATUS_NOT_LINKED, FlowTaIncomeContract.STATUS_INVALID,
                FlowTaIncomeContract.STATUS_ERROR, FlowTaIncomeContract.STATUS_LINKED
            )
        )
    }

    @Test
    fun `income extras carry contract v1 with exact keys and types`() {
        val row = PendingFlowTaIncomeEntity(
            tripId = 42L, amountMinor = 1_250_000L, paymentMethod = "TRANSFER",
            endedAt = 1_700_000_000_000L, platformName = "Didi", createdAt = 5L
        )

        val extras = FlowTaIncomeContract.extras(row)

        assertEquals(
            setOf("contract_version", "external_id", "payment_method", "amount_minor", "occurred_at", "note"),
            extras.keySet()
        )
        assertEquals(1, extras.getInt("contract_version"))
        assertEquals("42", extras.getString("external_id"))
        assertEquals("TRANSFER", extras.getString("payment_method"))
        assertEquals(1_250_000L, extras.getLong("amount_minor"))
        assertEquals(1_700_000_000_000L, extras.getLong("occurred_at"))
        assertEquals("MiCuadre · Didi", extras.getString("note"))
        assertFalse(extras.containsKey("source_app"))
    }

    @Test
    fun `note falls back to the app name when the platform name is blank`() {
        assertEquals("MiCuadre", FlowTaIncomeContract.note(""))
        assertEquals("MiCuadre", FlowTaIncomeContract.note("  "))
        assertEquals("MiCuadre · inDrive", FlowTaIncomeContract.note("inDrive"))
    }
}
