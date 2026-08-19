package com.spatialapps.homemarkers.domain

enum class MarkerPlacementPhase {
    IDLE,
    ARMED,
    PLACING,
}

object MarkerPlacementFlow {
    const val MISS_HINT_DURATION_MILLIS = 1_500L

    fun canAcceptSurfaceInput(phase: MarkerPlacementPhase): Boolean =
        phase == MarkerPlacementPhase.ARMED

    fun afterPlacementAttempt(succeeded: Boolean): MarkerPlacementPhase =
        if (succeeded) MarkerPlacementPhase.IDLE else MarkerPlacementPhase.ARMED
}

/** Rising-edge gate for a fingertip contacting a detected real-world surface. */
class SurfaceTouchGate(
    private val contactDistanceMetres: Float = 0.035f,
    private val releaseDistanceMetres: Float = 0.06f,
) {
    private var touching = false

    fun consume(distanceToSurfaceMetres: Float?): Boolean {
        val touchingNow = distanceToSurfaceMetres != null &&
            distanceToSurfaceMetres <= if (touching) releaseDistanceMetres else contactDistanceMetres
        val startedTouch = touchingNow && !touching
        touching = touchingNow
        return startedTouch
    }

    fun reset() {
        touching = false
    }
}

/** Rising-edge gate for an index-finger/thumb spatial click. */
class PinchClickGate(
    private val pressDistanceMetres: Float = 0.025f,
    private val releaseDistanceMetres: Float = 0.04f,
) {
    private var pinched = false

    fun consume(fingertipDistanceMetres: Float?): Boolean {
        val pinchedNow = fingertipDistanceMetres != null &&
            fingertipDistanceMetres <= if (pinched) releaseDistanceMetres else pressDistanceMetres
        val startedPinch = pinchedNow && !pinched
        pinched = pinchedNow
        return startedPinch
    }

    fun reset() {
        pinched = false
    }
}
