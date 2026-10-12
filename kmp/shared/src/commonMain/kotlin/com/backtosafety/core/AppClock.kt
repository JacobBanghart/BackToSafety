package com.backtosafety.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

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

    /**
     * Today's date in the device's time zone, in the Gregorian calendar whatever calendar the
     * user's region prefers ([LocalDate] is always Gregorian). Both apps take "today" from here
     * rather than from the platform's calendar, whose year, month and day can be Buddhist, Hebrew,
     * Persian and so on, and it follows the debug clock too.
     */
    fun today(): LocalDate = Instant.fromEpochMilliseconds(nowMs()).toLocalDateTime(TimeZone.currentSystemDefault()).date

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
