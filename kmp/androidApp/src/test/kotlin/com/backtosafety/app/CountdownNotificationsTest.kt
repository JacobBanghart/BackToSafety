package com.backtosafety.app

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.backtosafety.app.emergency.CountdownAlertReceiver
import com.backtosafety.app.emergency.CountdownNotifications
import com.backtosafety.core.AppClock
import com.backtosafety.core.CountdownAlert
import com.backtosafety.core.ScheduledAlert
import com.backtosafety.core.Translations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.io.File

/**
 * The Android side of the countdown alerts: what shared EmergencyAway schedules becomes an
 * alarm per alert, and each alarm posts its notification, which opens the emergency.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CountdownNotificationsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val translations: Translations by lazy {
        val locales = File(File(System.getProperty("repoRoot")!!), "i18n/locales")
        Translations.fromJson(
            locales.listFiles()!!.filter { it.isDirectory }.associate { locale ->
                locale.name to locale.listFiles()!!.filter { it.extension == "json" }.associate { it.nameWithoutExtension to it.readText() }
            },
        )
    }
    private var language = "en"
    private val notifications = CountdownNotifications(context, { translations.translator(language, "emergency") }, { "911" })
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notificationManager = shadowOf(context.getSystemService(NotificationManager::class.java))

    /** The app clock at 2026-10-06 16:00 UTC; the alerts for an emergency started then. */
    private val now = 1_791_302_400_000L
    private val warning = ScheduledAlert(CountdownAlert.WARNING, now + 601_000L)
    private val expired = ScheduledAlert(CountdownAlert.EXPIRED, now + 900_000L)

    @Before
    fun freezeClock() {
        AppClock.testSeamsEnabled = true
        AppClock.freeze(now)
    }

    private fun scheduled() = alarms.scheduledAlarms.sortedBy { it.triggerAtTime }

    /** When the alarm for [alert] goes off: that far from now on the elapsed-time clock. */
    private fun elapsedAt(alert: ScheduledAlert) = SystemClock.elapsedRealtime() + (alert.fireAtMs - now)

    /** Fires the alarm for [alert], as AlarmManager would. */
    private fun fire(alarm: ShadowAlarmManager.ScheduledAlarm) {
        val intent = shadowOf(alarm.operation).savedIntent
        CountdownAlertReceiver().onReceive(context, intent)
    }

    @Test
    fun eachAlertIsAnAlarmAtItsTime() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        notifications.schedule(listOf(warning, expired))
        assertEquals(listOf(elapsedAt(warning), elapsedAt(expired)), scheduled().map { it.triggerAtTime })
        assertTrue(scheduled().all { it.type == AlarmManager.ELAPSED_REALTIME_WAKEUP })
    }

    @Test
    fun schedulingReplacesWhatWasScheduled() {
        notifications.schedule(listOf(warning, expired))
        notifications.schedule(listOf(expired))
        assertEquals(listOf(elapsedAt(expired)), scheduled().map { it.triggerAtTime })
    }

    @Test
    fun cancellingClearsAlarmsAndShownAlerts() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications.schedule(listOf(warning, expired))
        fire(scheduled().first())
        assertEquals(1, notificationManager.allNotifications.size)
        notifications.cancelAll()
        assertTrue(scheduled().isEmpty())
        assertTrue(notificationManager.allNotifications.isEmpty())
    }

    @Test
    fun withoutExactAlarmsTheyAreStillScheduled() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(notifications.canScheduleExact())
        notifications.schedule(listOf(expired))
        assertEquals(listOf(elapsedAt(expired)), scheduled().map { it.triggerAtTime })
    }

    @Test
    fun theAlarmPostsTheTranslatedAlertThatOpensTheEmergency() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        language = "es"
        notifications.schedule(listOf(expired))
        fire(scheduled().single())
        val posted = notificationManager.allNotifications.single()
        assertEquals("Hora de llamar al 911", posted.extras.getString("android.title"))
        assertTrue(posted.extras.getString("android.text")!!.startsWith("Terminaron los 15 minutos"))
        assertEquals(CountdownNotifications.CHANNEL_ID, posted.channelId)
        val open = shadowOf(posted.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, open.component?.className)
        assertTrue(open.getBooleanExtra(CountdownNotifications.EXTRA_OPEN_EMERGENCY, false))
    }

    @Test
    fun withoutPermissionNothingIsPosted() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications.schedule(listOf(warning))
        fire(scheduled().single())
        assertTrue(notificationManager.allNotifications.isEmpty())
    }

    @Test
    fun anUnknownAlertIsIgnored() {
        CountdownAlertReceiver().onReceive(context, Intent(context, CountdownAlertReceiver::class.java))
        assertNull(notificationManager.allNotifications.firstOrNull())
    }
}
