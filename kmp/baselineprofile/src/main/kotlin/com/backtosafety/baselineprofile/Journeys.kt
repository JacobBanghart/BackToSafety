package com.backtosafety.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/** The app under test (androidApp's applicationId). */
const val PACKAGE_NAME = "com.backtosafety.app"

/** Enough contacts that the list scrolls a few screens on a phone. */
private const val CONTACTS = 14

private const val TIMEOUT_MS = 10_000L

/** Scrolls down a screen takes at most: [CONTACTS] rows are about three screens. */
private const val MAX_SCROLLS = 8

/** The app's testTags, which it exposes as resource IDs (MainActivity, testTagsAsResourceId). */
private fun id(testTag: String): BySelector = By.res(testTag)

fun UiDevice.find(testTag: String): UiObject2 =
    checkNotNull(wait(Until.findObject(id(testTag)), TIMEOUT_MS)) { "$testTag didn't show" }

fun UiDevice.tap(testTag: String) = find(testTag).click()

/** The screen's scrolling content, held clear of the edges, where a swipe means back. */
private fun UiDevice.scrollable(): UiObject2? = findObject(By.scrollable(true))?.also { it.setGestureMargin(displayWidth / 5) }

/** Scrolls down until [testTag] shows, then taps it. */
private fun UiDevice.scrollToAndTap(testTag: String) {
    // scroll()'s "can scroll further" is unreliable on Compose, so a bounded number of tries.
    repeat(MAX_SCROLLS) { if (!hasObject(id(testTag))) scrollable()?.scroll(Direction.DOWN, 0.8f) }
    tap(testTag)
}

/**
 * Types into a field, again if a screen still settling dropped it (the first field of a form
 * opened a moment earlier sometimes does). Phone fields format what's typed, so only the
 * start of it is compared.
 */
private fun UiDevice.type(testTag: String, text: String) {
    val start = text.filter(Char::isLetterOrDigit).take(3)
    repeat(3) {
        waitForIdle()
        find(testTag).text = text
        waitForIdle()
        if (find(testTag).text.orEmpty().filter(Char::isLetterOrDigit).startsWith(start)) return
    }
    error("$testTag didn't take \"$text\"")
}

/** Closes the keyboard if it's up (back would otherwise leave the screen). */
private fun UiDevice.hideKeyboard() {
    if ("mInputShown=true" in executeShellCommand("dumpsys input_method")) pressBack()
}

/** Home has finished loading: its main button is up. */
fun UiDevice.waitForHome() = find("home-start-emergency")

/**
 * The notification prompt home shows once would cover home in every run, so the permission
 * is granted up front (the app then records the answer without a dialog).
 */
fun UiDevice.grantNotifications() {
    executeShellCommand("pm grant $PACKAGE_NAME android.permission.POST_NOTIFICATIONS")
}

/** From the welcome screen of a fresh install to home, through every onboarding step. */
fun UiDevice.onboard() {
    tap("onboarding-get-started")
    type("onboarding-name-input", "Margaret Smith")
    type("onboarding-name-nickname", "Maggie")
    hideKeyboard()
    tap("onboarding-name-continue")
    tap("onboarding-photo-skip")
    type("onboarding-appearance-height", "5'4\"")
    tap("onboarding-appearance-continue")
    type("onboarding-contact-name", "John Smith")
    type("onboarding-contact-phone", "5551234567")
    hideKeyboard()
    tap("onboarding-contact-continue")
    tap("onboarding-complete-home")
    waitForHome()
}

/** Adds contacts from the contacts list until it has [CONTACTS]. */
private fun UiDevice.fillContacts() {
    // Only rows on screen are found, so the count is the last row's, at the bottom of the list.
    repeat(MAX_SCROLLS) { scrollable()?.scroll(Direction.DOWN, 0.8f) }
    var count = (CONTACTS downTo 1).firstOrNull { hasObject(id("contacts-item-${it - 1}-name")) } ?: 0
    while (count < CONTACTS) {
        scrollToAndTap("contacts-add")
        type("contacts-form-name", "Contact ${count + 1}")
        type("contacts-form-phone", "555123${4000 + count}")
        type("contacts-form-address", "${100 + count} Main Street, Springfield")
        hideKeyboard()
        tap("contacts-save")
        find("contacts-item-0-name")
        count++
    }
}

/**
 * The data the startup and scroll runs expect: onboarded, with a list of contacts. Whatever
 * the app holds already is kept, so this is quick after the first time.
 */
fun prepareData() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    device.grantNotifications()
    device.executeShellCommand("am start -W -n $PACKAGE_NAME/.MainActivity")
    val welcome = device.wait(Until.hasObject(id("onboarding-get-started")), TIMEOUT_MS)
    if (welcome) device.onboard() else device.waitForHome()
    device.tap("home-contacts")
    device.find("contacts-item-0-name")
    device.fillContacts()
    device.executeShellCommand("am force-stop $PACKAGE_NAME")
}

/** Opens the contacts list from home and waits for its rows. */
fun MacrobenchmarkScope.openContacts() {
    device.tap("home-contacts")
    device.find("contacts-item-0-name")
}

/** Scrolls the contacts list to the bottom and back, as a caregiver looking for someone would. */
fun MacrobenchmarkScope.scrollContacts() {
    val list = checkNotNull(device.scrollable()) { "the contacts list doesn't scroll" }
    repeat(2) {
        list.fling(Direction.DOWN)
        device.waitForIdle()
        list.fling(Direction.UP)
        device.waitForIdle()
    }
}
