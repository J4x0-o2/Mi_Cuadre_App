package com.micuadre.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformCountsTest {

    @Test
    fun `counts visible against total`() {
        val counts = PlatformCounts.of(
            listOf(
                Platform(1, "A"),
                Platform(2, "B", visible = false),
                Platform(3, "C"),
                Platform(4, "D")
            )
        )

        assertEquals(PlatformCounts(visible = 3, total = 4), counts)
    }

    @Test
    fun `deleted platforms are not counted`() {
        val counts = PlatformCounts.of(
            listOf(Platform(1, "A"), Platform(2, "B", deletedAt = 5L))
        )

        assertEquals(PlatformCounts(visible = 1, total = 1), counts)
    }

    @Test
    fun `empty list counts zero of zero`() {
        assertEquals(PlatformCounts(0, 0), PlatformCounts.of(emptyList()))
    }
}
