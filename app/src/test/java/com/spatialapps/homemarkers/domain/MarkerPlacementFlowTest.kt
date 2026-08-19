package com.spatialapps.homemarkers.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerPlacementFlowTest {
    @Test
    fun `surface input is accepted only after creation is armed`() {
        assertFalse(MarkerPlacementFlow.canAcceptSurfaceInput(MarkerPlacementPhase.IDLE))
        assertFalse(MarkerPlacementFlow.canAcceptSurfaceInput(MarkerPlacementPhase.PLACING))
        assertTrue(MarkerPlacementFlow.canAcceptSurfaceInput(MarkerPlacementPhase.ARMED))
    }

    @Test
    fun `a missed surface keeps placement armed and success disarms it`() {
        assertEquals(MarkerPlacementPhase.ARMED, MarkerPlacementFlow.afterPlacementAttempt(false))
        assertEquals(MarkerPlacementPhase.IDLE, MarkerPlacementFlow.afterPlacementAttempt(true))
    }

    @Test
    fun `miss feedback is transient for one and a half seconds`() {
        assertEquals(1_500L, MarkerPlacementFlow.MISS_HINT_DURATION_MILLIS)
    }

    @Test
    fun `surface touch triggers once until finger leaves the surface`() {
        val gate = SurfaceTouchGate()
        assertFalse(gate.consume(.08f))
        assertTrue(gate.consume(.03f))
        assertFalse(gate.consume(.02f))
        assertFalse(gate.consume(.05f))
        assertFalse(gate.consume(.07f))
        assertTrue(gate.consume(.03f))
    }

    @Test
    fun `pinch click triggers only on rising edge`() {
        val gate = PinchClickGate()
        assertTrue(gate.consume(.02f))
        assertFalse(gate.consume(.018f))
        assertFalse(gate.consume(.05f))
        assertTrue(gate.consume(.02f))
    }
}
