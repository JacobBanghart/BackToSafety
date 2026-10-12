package com.backtosafety.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals

class AppClockTest {
    @Test
    fun todayIsTheDeviceDateOfTheAppClock() {
        AppClock.testSeamsEnabled = true
        // Local noon, so the device's zone can't move it to another day.
        val noon = LocalDate(2026, 10, 11).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + 12 * 3_600_000L
        AppClock.freeze(noon)
        assertEquals(LocalDate(2026, 10, 11), AppClock.today())
        AppClock.advance(24 * 3_600_000L)
        assertEquals(LocalDate(2026, 10, 12), AppClock.today())
    }
}
