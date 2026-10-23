package com.micuadre.app.presentation

import com.micuadre.app.domain.model.ShiftState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PersistedShiftRestorerTest {

    private var restores = 0
    private val failures = mutableListOf<Throwable>()

    private fun restorer(
        state: ShiftState = ShiftState.Disconnected,
        hasRestorableShift: suspend () -> Boolean
    ) = PersistedShiftRestorer(
        state = { state },
        hasRestorableShift = hasRestorableShift,
        restore = { restores++ },
        onFailure = { failures += it }
    )

    @Test
    fun `restores when a session is stored and nothing is published`() = runTest {
        restorer { true }.restoreIfNeeded()

        assertEquals(1, restores)
    }

    @Test
    fun `does not restore when no session is stored`() = runTest {
        restorer { false }.restoreIfNeeded()

        assertEquals(0, restores)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `does not restore over a state the service already published`() = runTest {
        restorer(state = ShiftState.Connected(connectedSinceMs = 1L)) { true }.restoreIfNeeded()

        assertEquals(0, restores)
    }

    @Test
    fun `a failing lookup is reported and nothing is restored`() = runTest {
        val boom = IllegalStateException("db closed")

        restorer { throw boom }.restoreIfNeeded()

        assertEquals(0, restores)
        assertEquals(listOf<Throwable>(boom), failures)
    }

    @Test
    fun `cancellation is rethrown, not reported as a failure`() = runTest {
        try {
            restorer { throw CancellationException("scope cancelled") }.restoreIfNeeded()
            fail("expected the cancellation to propagate")
        } catch (expected: CancellationException) {
            // propagated
        }

        assertEquals(0, restores)
        assertTrue(failures.isEmpty())
    }
}
