package com.backtosafety.core

import kotlin.time.Clock

/**
 * The app's notion of "now" (utils/clock.ts). Everything time-sensitive reads it, so test
 * builds can freeze and advance time through the debug clock seam. The app turns
 * [testSeamsEnabled] on only in builds made with test seams.
 */
object AppClock {
    var testSeamsEnabled = false
    private var offsetMs = 0L
    private var frozenAtMs: Long? = null

    fun nowMs(): Long = frozenAtMs ?: (Clock.System.now().toEpochMilliseconds() + offsetMs)

    /** Moves the clock forward (or back), frozen or not. Ignored without test seams. */
    fun advance(ms: Long) {
        if (!testSeamsEnabled) return
        frozenAtMs = frozenAtMs?.plus(ms) ?: run { offsetMs += ms; null }
    }

    /** Stops the clock at [atMs], so screenshots show the same times every run. */
    fun freeze(atMs: Long) {
        if (testSeamsEnabled) frozenAtMs = atMs
    }
}
