package com.backtosafety.core

import kotlin.test.Test
import kotlin.test.assertEquals

class AbbreviateIdTest {
    @Test
    fun aUuidKeepsItsStartAndEnd() {
        assertEquals("3f2a9c1b…41ef", abbreviateId("3f2a9c1b-7d4e-4b2a-9c1d-0a5e6f7b41ef"))
    }

    @Test
    fun aShortValueIsShownWhole() {
        assertEquals("—", abbreviateId("—"))
        assertEquals("1234567890123", abbreviateId("1234567890123"))
    }
}
