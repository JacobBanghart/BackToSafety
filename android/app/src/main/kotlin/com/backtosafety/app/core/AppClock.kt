package com.backtosafety.app.core

import com.backtosafety.core.ffi.appClockAdvance
import com.backtosafety.core.ffi.appClockFreeze
import com.backtosafety.core.ffi.appClockHandleLink
import com.backtosafety.core.ffi.appClockNowMs
import com.backtosafety.core.ffi.appClockSetTestSeams

/**
 * The app's notion of "now" (utils/clock.ts), in the Rust core. Everything time-sensitive reads
 * it, so test builds can freeze and advance time through the debug clock seam. The app turns
 * [testSeamsEnabled] on only in builds made with test seams.
 */
object AppClock {
    var testSeamsEnabled = false
        set(value) {
            field = value
            appClockSetTestSeams(value)
        }

    fun nowMs(): Long = appClockNowMs()

    /** Moves the clock forward (or back), frozen or not. Ignored without test seams. */
    fun advance(ms: Long) = appClockAdvance(ms)

    /** Stops the clock at [atMs], so screenshots show the same times every run. */
    fun freeze(atMs: Long) = appClockFreeze(atMs)

    /** Applies a `backtosafety://debug/clock?at=<ISO>&advance=<seconds>` link; false for any other link. */
    fun handleLink(uri: String): Boolean = appClockHandleLink(uri)
}
