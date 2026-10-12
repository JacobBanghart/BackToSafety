package com.backtosafety.app

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.backtosafety.app.emergency.CountdownAlertReceiver
import com.backtosafety.app.emergency.CountdownNotifications
import com.backtosafety.core.AppClock
import com.backtosafety.core.CountdownAlert
import com.backtosafety.core.ScheduledAlert
import com.backtosafety.core.Translations
import org.junit.Assert.assertArrayEquals
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
import org.robolectric.shadows.ShadowPowerManager
import java.io.File
import java.time.Duration

/**
 * The Android side of the countdown alerts: what shared EmergencyAway schedules becomes an
 * alarm per alert and a timer, under a wake lock, that posts it on time unless the alarm did;
 * each alert opens the emergency.
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
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notificationManager = shadowOf(manager)

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

    /** Fires [alarm], as AlarmManager would: it's gone, its one-shot PendingIntent used up. */
    private fun fire(alarm: ShadowAlarmManager.ScheduledAlarm) {
        val intent = shadowOf(alarm.operation).savedIntent
        context.getSystemService(AlarmManager::class.java).cancel(alarm.operation!!)
        alarm.operation!!.cancel()
        CountdownAlertReceiver().onReceive(context, intent)
    }

    /** Lets [ms] pass on the main thread, where the timers run. */
    private fun pass(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun posted() = notificationManager.allNotifications.map { it.channelId }

    private fun wakeLock() = ShadowPowerManager.getLatestWakeLock()

    @Test
    fun theTimerPostsEachAlertOnTime() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val expiryAlarmAt = elapsedAt(expired)
        notifications.schedule(listOf(warning, expired))
        pass(600_999)
        assertTrue(posted().isEmpty())
        pass(1)
        assertEquals(listOf(CountdownNotifications.channelId(CountdownAlert.WARNING)), posted())
        // Its alarm, the backstop, won't post it again.
        assertEquals(listOf(expiryAlarmAt), scheduled().map { it.triggerAtTime })
        pass(298_999)
        assertEquals(1, posted().size)
        pass(1)
        assertEquals(CountdownAlert.entries.map { CountdownNotifications.channelId(it) }.toSet(), posted().toSet())
        assertTrue(scheduled().isEmpty())
    }

    @Test
    fun theWakeLockIsHeldUntilTheLastAlertOnly() {
        notifications.schedule(listOf(warning, expired))
        assertTrue(wakeLock().isHeld)
        pass(899_999)
        assertTrue("let go before the expiry", wakeLock().isHeld)
        pass(1)
        assertFalse(wakeLock().isHeld)
    }

    @Test
    fun cancellingStopsTheTimersAndLetsGoOfTheWakeLock() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications.schedule(listOf(warning, expired))
        notifications.cancelAll()
        assertFalse(wakeLock().isHeld)
        pass(900_000)
        assertTrue(posted().isEmpty())
    }

    @Test
    fun anAlertWhoseAlarmWentOffFirstIsntPostedAgain() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications.schedule(listOf(expired))
        fire(scheduled().single())
        // Seen and dismissed before the timer comes round (it was held up, say).
        manager.cancelAll()
        pass(900_000)
        assertTrue(posted().isEmpty())
    }

    @Test
    fun aCaughtUpAlertIsPostedAtOnce() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications.post(CountdownAlert.EXPIRED)
        val alert = notificationManager.allNotifications.single()
        assertEquals("Time to Call 911", alert.extras.getString("android.title"))
        assertEquals(CountdownNotifications.channelId(CountdownAlert.EXPIRED), alert.channelId)
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
        assertEquals(CountdownNotifications.channelId(CountdownAlert.EXPIRED), posted.channelId)
        val open = shadowOf(posted.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, open.component?.className)
        assertTrue(open.getBooleanExtra(CountdownNotifications.EXTRA_OPEN_EMERGENCY, false))
    }

    @Test
    fun eachAlertHasItsOwnChannelWithItsVibration() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications.schedule(listOf(warning, expired))
        scheduled().forEach { fire(it) }
        // From Android 8 the channel decides the vibration, so the patterns live on the channels.
        val channels = CountdownAlert.entries.associateWith { manager.getNotificationChannel(CountdownNotifications.channelId(it))!! }
        assertEquals(2, channels.values.map { it.id }.toSet().size)
        assertArrayEquals(longArrayOf(0, 30, 80, 30), channels.getValue(CountdownAlert.WARNING).vibrationPattern)
        assertArrayEquals(longArrayOf(0, 500, 200, 500), channels.getValue(CountdownAlert.EXPIRED).vibrationPattern)
        assertTrue(channels.values.all { it.shouldVibrate() })
        assertEquals(
            channels.values.map { it.id }.toSet(),
            notificationManager.allNotifications.map { it.channelId }.toSet(),
        )
    }

    @Test
    fun channelNamesAreTranslated() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        language = "es"
        notifications.schedule(listOf(warning, expired))
        scheduled().forEach { fire(it) }
        assertEquals("Aviso de la cuenta regresiva", manager.getNotificationChannel(CountdownNotifications.channelId(CountdownAlert.WARNING))!!.name)
        assertEquals("Fin de la cuenta regresiva", manager.getNotificationChannel(CountdownNotifications.channelId(CountdownAlert.EXPIRED))!!.name)
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
