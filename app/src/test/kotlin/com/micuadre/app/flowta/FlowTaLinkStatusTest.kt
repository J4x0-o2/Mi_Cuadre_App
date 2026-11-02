package com.micuadre.app.flowta

import org.junit.Assert.assertEquals
import org.junit.Test

class FlowTaLinkStatusTest {

    @Test
    fun `maps LINKED and NOT_LINKED`() {
        assertEquals(FlowTaLinkStatus.LINKED, FlowTaLinkStatus.fromResult("LINKED"))
        assertEquals(FlowTaLinkStatus.NOT_LINKED, FlowTaLinkStatus.fromResult("NOT_LINKED"))
    }

    @Test
    fun `ERROR, unknown, empty and null are unavailable`() {
        assertEquals(FlowTaLinkStatus.UNAVAILABLE, FlowTaLinkStatus.fromResult("ERROR"))
        assertEquals(FlowTaLinkStatus.UNAVAILABLE, FlowTaLinkStatus.fromResult("linked"))
        assertEquals(FlowTaLinkStatus.UNAVAILABLE, FlowTaLinkStatus.fromResult(""))
        assertEquals(FlowTaLinkStatus.UNAVAILABLE, FlowTaLinkStatus.fromResult(null))
    }
}
