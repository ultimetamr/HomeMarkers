package com.spatialapps.homemarkers.domain

object MarkerHighlightPresentationPolicy {
    const val TARGET_ALPHA = 1f
    const val NON_TARGET_ALPHA = 0.18f

    fun alpha(dimmed: Boolean): Float = if (dimmed) NON_TARGET_ALPHA else TARGET_ALPHA
}
