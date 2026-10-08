package com.backtosafety.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Kotlin app sends the same analytics events as the RN app: every event the RN source
 * passes to track() is tracked somewhere in the Android app, and nothing else is.
 */
class AnalyticsParityTest {
    private val root = File(System.getProperty("repoRoot") ?: error("repoRoot system property not set"))

    private fun sources(dir: String, ext: String) =
        File(root, dir).walkTopDown().filter { it.isFile && it.extension == ext }.map { it.readText() }

    @Test
    fun kotlinSendsTheEventsTheRnAppSends() {
        val rn = (sources("app", "tsx") + sources("components", "tsx") + sources("context", "tsx"))
            .flatMap { Regex("""\btrack\(\s*'([a-z_0-9]+)'""").findAll(it).map { m -> m.groupValues[1] } }
            .toSortedSet()
        val kotlin = sources("kmp/androidApp/src/main", "kt")
            .flatMap { Regex("""AnalyticsEvent\.([A-Z_0-9]+)""").findAll(it).map { m -> m.groupValues[1].lowercase() } }
            .toSortedSet()
        assertTrue(rn.size > 30, "found only $rn in the RN source")
        assertEquals(rn, kotlin)
    }
}
