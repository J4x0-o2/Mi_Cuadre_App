package com.micuadre.app.overlay

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionUpdatesTest {

    @Test
    fun `a granted permission is reported once and the flow ends`() = runTest {
        var checks = 0

        val seen = permissionUpdates(canDraw = { checks++; true }, pollMs = 1_000L).toList()

        assertEquals(listOf(true), seen)
        assertEquals(1, checks)
    }

    @Test
    fun `a missing permission is polled until it is granted`() = runTest {
        var granted = false
        val seen = mutableListOf<Boolean>()
        val job = launch { permissionUpdates(canDraw = { granted }, pollMs = 1_000L).toList(seen) }

        runCurrent()
        assertEquals(listOf(false), seen)

        advanceTimeBy(3_500L)
        assertEquals(listOf(false), seen)

        granted = true
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(listOf(false, true), seen)
        job.join()
    }

    @Test
    fun `polling stops once the collector is gone`() = runTest {
        var checks = 0
        val job = launch { permissionUpdates(canDraw = { checks++; false }, pollMs = 1_000L).toList() }
        runCurrent()
        advanceTimeBy(2_500L)
        val before = checks

        job.cancel()
        advanceTimeBy(10_000L)

        assertEquals(before, checks)
    }
}
