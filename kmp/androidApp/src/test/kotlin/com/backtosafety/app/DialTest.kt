package com.backtosafety.app

import android.app.Activity
import android.content.Intent
import com.backtosafety.app.ui.dial
import com.backtosafety.core.Analytics
import com.backtosafety.core.DialTarget
import com.backtosafety.core.Translations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

/** Calling from a device with no phone app (tablets) shows the number and reports dial_failed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DialTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val tCommon = File(File(System.getProperty("repoRoot")!!), "i18n/locales").let { locales ->
        Translations.fromJson(
            locales.listFiles()!!.filter { it.isDirectory }.associate { locale ->
                locale.name to locale.listFiles()!!.filter { it.extension == "json" }.associate { it.nameWithoutExtension to it.readText() }
            },
        ).translator("en", "common")
    }
    private val tracked = mutableListOf<Pair<String, Map<String, Any?>>>()

    init {
        Analytics.sink = { name, properties -> tracked += name to properties }
    }

    @After
    fun releaseAnalytics() {
        Analytics.sink = { _, _ -> }
    }

    @Test
    fun aPhoneOpensTheDialer() {
        shadowOf(activity.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:911")),
            android.content.pm.ResolveInfo().apply { activityInfo = android.content.pm.ActivityInfo().apply { packageName = "dialer"; name = "Dialer" } },
        )
        assertTrue(dial(activity, "911", DialTarget.EMERGENCY, "emergency", tCommon))
        assertEquals(Intent.ACTION_DIAL, shadowOf(activity).nextStartedActivity.action)
        assertTrue(tracked.isEmpty())
    }

    @Test
    fun noPhoneAppShowsTheNumberAndReportsIt() {
        shadowOf(activity.application).checkActivities(true)
        assertFalse(dial(activity, "5551234567", DialTarget.CONTACT, "contacts", tCommon))
        assertEquals(listOf("dial_failed" to mapOf<String, Any?>("target" to "contact", "screen" to "contacts")), tracked)
        val dialog = shadowOf(ShadowAlertDialog.getLatestAlertDialog())
        assertEquals("Can't Place Calls", dialog.title)
        assertEquals("This device can't make phone calls. Call (555) 123-4567 from a phone.", dialog.message)
    }
}
