package com.backtosafety.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Both apps send the analytics events spec/analytics-events.json names, and name the same
 * screens: every event is tracked somewhere in the Android app and in the iOS app, and the
 * Android app tracks nothing else.
 */
class AnalyticsParityTest {
    private val root = File(System.getProperty("repoRoot") ?: error("repoRoot system property not set"))

    private fun sources(dir: String, ext: String) =
        File(root, dir).walkTopDown().filter { it.isFile && it.extension == ext }.map { it.readText() }.toList()

    private val events: Set<String> =
        Regex(""""([a-z_0-9]+)"""").findAll(
            File(root, "spec/analytics-events.json").readText().substringAfter("\"events\"").substringBefore("]"),
        ).map { it.groupValues[1] }.toSortedSet()

    /** The Swift name Kotlin/Native gives an enum entry: READOUT_911_CALLED -> readout911Called. */
    private fun swiftName(event: String) =
        event.split('_').mapIndexed { i, part -> if (i == 0) part else part.replaceFirstChar { it.uppercase() } }.joinToString("")

    @Test
    fun androidSendsTheSpecEvents() {
        val kotlin = sources("kmp/androidApp/src/main", "kt")
            .flatMap { Regex("""AnalyticsEvent\.([A-Z_0-9]+)""").findAll(it).map { m -> m.groupValues[1].lowercase() } }
            .toSortedSet()
        assertEquals(events, kotlin)
    }

    @Test
    fun iosSendsTheSpecEvents() {
        val swift = sources("kmp/iosApp/BackToSafety", "swift")
            .flatMap { Regex("""\.([a-z][A-Za-z0-9]+)\b""").findAll(it).map { m -> m.groupValues[1] } }
            .toSet()
        assertEquals(emptyList(), events.filter { swiftName(it) !in swift })
    }

    @Test
    fun bothAppsNameTheSameScreens() {
        val kotlin = sources("kmp/androidApp/src/main", "kt")
            .flatMap { Regex("""Analytics\.screen\(\s*"([a-z_]+)"""").findAll(it).map { m -> m.groupValues[1] } }
            .toSortedSet()
        val swift = sources("kmp/iosApp/BackToSafety", "swift")
            .flatMap { Regex("""screen\(path:\s*"([a-z_]+)"""").findAll(it).map { m -> m.groupValues[1] } }
            .toSortedSet()
        assertEquals(setOf("home", "settings"), kotlin)
        assertEquals(kotlin, swift)
    }
}
