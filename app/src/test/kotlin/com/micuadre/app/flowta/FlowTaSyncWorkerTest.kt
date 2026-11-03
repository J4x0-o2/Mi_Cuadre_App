package com.micuadre.app.flowta

import androidx.work.ListenableWorker.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FlowTaSyncWorkerTest {

    @Test
    fun `DONE succeeds and RETRY retries`() = runBlocking {
        assertEquals(Result.success(), workResultOf { FlowTaSyncRunner.Outcome.DONE })
        assertEquals(Result.retry(), workResultOf { FlowTaSyncRunner.Outcome.RETRY })
    }

    @Test
    fun `an unexpected exception retries instead of failing the work`() = runBlocking {
        assertEquals(Result.retry(), workResultOf { throw IllegalStateException("room closed") })
    }

    @Test
    fun `cancellation is not swallowed`() = runBlocking {
        try {
            workResultOf { throw CancellationException("stopped") }
            fail("expected CancellationException")
        } catch (expected: CancellationException) {
            assertEquals("stopped", expected.message)
        }
    }
}
