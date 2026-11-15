package com.backtosafety.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadinessTest {
    private fun done(checks: List<ReadinessCheck>) = checks.filter { it.done }.map { it.item }

    @Test
    fun noProfileAndNothingSavedIsNothingDone() {
        val checks = readinessChecklist(null, 0, 0, 0)
        assertEquals(ReadinessItem.entries.toList(), checks.map { it.item })
        assertEquals(emptyList(), done(checks))
    }

    @Test
    fun aNameAloneIsNotADescription() {
        assertEquals(emptyList(), done(readinessChecklist(Profile(name = "Margaret"), 0, 0, 0)))
    }

    @Test
    fun eachLineIsDoneByItsOwnData() {
        assertEquals(
            listOf(ReadinessItem.DESCRIPTION, ReadinessItem.CONTACTS),
            done(readinessChecklist(Profile(name = "Margaret", hairColor = "Gray"), 2, 1, 0)),
        )
        assertEquals(
            listOf(ReadinessItem.PHOTO, ReadinessItem.PLACES),
            done(readinessChecklist(Profile(name = "Margaret", photoUri = "file:///p.jpg"), 0, 0, 1)),
        )
    }

    private fun contacts(saved: Int, inAlertCircle: Int) = readinessChecklist(null, saved, inAlertCircle, 0).single { it.item == ReadinessItem.CONTACTS }

    @Test
    fun contactsAreReadyOnlyWithSomeoneInTheAlertCircle() {
        val ready = contacts(saved = 3, inAlertCircle = 1)
        assertTrue(ready.done)
        assertFalse(ready.needsAlertCircle)
        assertEquals("contacts", ready.labelKey)
        assertEquals("done", ready.actionKey)
    }

    @Test
    fun contactsWithNoOneInTheAlertCircleSayTurnItOn() {
        // Saved contacts that no one would be texted: the search's Alert Circle would reach nobody.
        val off = contacts(saved = 2, inAlertCircle = 0)
        assertFalse(off.done)
        assertTrue(off.needsAlertCircle)
        assertEquals("contactsNoAlertCircle", off.labelKey)
        assertEquals("turnOn", off.actionKey)
    }

    @Test
    fun noContactsAtAllSayAdd() {
        val none = contacts(saved = 0, inAlertCircle = 0)
        assertFalse(none.done)
        assertFalse(none.needsAlertCircle)
        assertEquals("contacts", none.labelKey)
        assertEquals("add", none.actionKey)
    }
}
