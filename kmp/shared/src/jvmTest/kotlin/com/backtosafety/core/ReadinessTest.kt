package com.backtosafety.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadinessTest {
    private fun done(checks: List<ReadinessCheck>) = checks.filter { it.done }.map { it.item }

    @Test
    fun noProfileAndNothingSavedIsNothingDone() {
        val checks = readinessChecklist(null, 0, 0)
        assertEquals(ReadinessItem.entries.toList(), checks.map { it.item })
        assertEquals(emptyList(), done(checks))
    }

    @Test
    fun aNameAloneIsNotADescription() {
        assertEquals(emptyList(), done(readinessChecklist(Profile(name = "Margaret"), 0, 0)))
    }

    @Test
    fun eachLineIsDoneByItsOwnData() {
        assertEquals(
            listOf(ReadinessItem.DESCRIPTION, ReadinessItem.CONTACTS),
            done(readinessChecklist(Profile(name = "Margaret", hairColor = "Gray"), 2, 0)),
        )
        assertEquals(
            listOf(ReadinessItem.PHOTO, ReadinessItem.PLACES),
            done(readinessChecklist(Profile(name = "Margaret", photoUri = "file:///p.jpg"), 0, 1)),
        )
    }
}
