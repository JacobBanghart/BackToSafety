package com.backtosafety.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame times while scrolling the contacts list, without and with the Baseline Profile:
 * `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun setUp() = prepareData()

    @Test
    fun scrollWithoutProfile() = scroll(CompilationMode.None())

    @Test
    fun scrollWithProfile() = scroll(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun scroll(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        // No startupMode: COLD would kill the app between setupBlock and the scroll. Each
        // iteration starts a fresh process itself, so the first scroll is measured every time.
        iterations = 10,
        setupBlock = {
            killProcess()
            pressHome()
            startActivityAndWait()
            device.waitForHome()
            openContacts()
        },
    ) {
        scrollContacts()
    }
}
