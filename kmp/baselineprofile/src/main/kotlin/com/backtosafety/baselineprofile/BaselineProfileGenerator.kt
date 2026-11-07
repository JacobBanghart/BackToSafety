package com.backtosafety.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Writes androidApp's Baseline Profile (the code to compile ahead of time) from the paths a
 * user takes, and its startup profile (what R8 puts first in the dex) from cold start alone.
 * `./gradlew :androidApp:generateReleaseBaselineProfile` with a phone or an API 33+ emulator
 * connected; docs/baseline-profile.md has the rest.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    /** A cold start to a settled home: the launch every later use of the app begins with. */
    @Test
    fun startup() {
        prepareData()
        rule.collect(PACKAGE_NAME, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            device.waitForHome()
        }
    }

    /** The first launch: the welcome screen and every onboarding step, from a fresh install. */
    @Test
    fun onboarding() = rule.collect(PACKAGE_NAME, maxIterations = 5) {
        killProcess()
        device.executeShellCommand("pm clear $PACKAGE_NAME")
        device.grantNotifications()
        startActivityAndWait()
        device.onboard()
    }

    /** From home to the contacts list and through it, and the other screens home opens. */
    @Test
    fun home() {
        prepareData()
        rule.collect(PACKAGE_NAME) {
            pressHome()
            startActivityAndWait()
            device.waitForHome()
            openContacts()
            scrollContacts()
            device.pressBack()
            for (screen in listOf("home-places", "home-profile", "home-settings")) {
                device.waitForHome()
                device.tap(screen)
                device.waitForIdle()
                device.pressBack()
            }
            device.waitForHome()
        }
    }
}
