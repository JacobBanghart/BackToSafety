package com.backtosafety.app

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.backtosafety.app.emergency.CountdownNotifications
import com.backtosafety.app.emergency.CountdownService
import com.backtosafety.app.emergency.OngoingCountdown
import com.backtosafety.core.AppClock
import com.backtosafety.core.Translations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import kotlin.time.Instant

/**
 * The emergency's countdown as an ongoing notification (Android): [OngoingCountdown] starts and
 * stops the foreground service, which counts down to the expiry on the lock screen and then
 * turns into the call to 911.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CountdownServiceTest {
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
    private val countdown = OngoingCountdown(context, { translations.translator(language, "emergency") }, { "911" })
    private val manager = context.getSystemService(NotificationManager::class.java)

    /** The app clock at 2026-10-06 16:00 UTC; the emergency started a minute before. */
    private val now = 1_791_302_400_000L
    private val startedAt = Instant.fromEpochMilliseconds(now - 60_000L).toString()

    @Before
    fun freezeClock() {
        AppClock.testSeamsEnabled = true
        AppClock.freeze(now)
    }

    /** What [OngoingCountdown] asked of the service, in order. */
    private fun commands(): List<Intent> = generateSequence { shadowOf(context).nextStartedService }.toList()

    /** Runs the service on [command], as Android would. */
    private fun run(command: Intent): ServiceController<CountdownService> =
        Robolectric.buildService(CountdownService::class.java, command).create().startCommand(0, 1)

    private fun pass(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private val Notification.title get() = extras.getString(Notification.EXTRA_TITLE)

    @Test
    fun itStartsWithTheEmergencyAndStopsWithIt() {
        countdown.sync(startedAt)
        countdown.sync(null)
        val (start, stop) = commands()
        assertEquals(CountdownService::class.java.name, start.component?.className)
        assertEquals(now - 60_000L, start.getLongExtra(CountdownService.EXTRA_STARTED_AT, -1))
        assertEquals(CountdownService.ACTION_STOP, stop.action)

        val service = run(start)
        assertEquals(CountdownService.NOTIFICATION_ID, shadowOf(service.get()).lastForegroundNotificationId)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, service.get().foregroundServiceType)
        service.withIntent(stop).startCommand(0, 2)
        assertTrue(shadowOf(service.get()).isStoppedBySelf)
        assertTrue("The countdown should be gone", shadowOf(service.get()).notificationShouldRemoved)
    }

    @Test
    fun itCountsDownToTheExpiryOnAQuietChannel() {
        countdown.sync(startedAt)
        val before = System.currentTimeMillis()
        val service = run(commands().single())
        val after = System.currentTimeMillis()
        val shown = shadowOf(service.get()).lastForegroundNotification
        assertEquals("Emergency In Progress", shown.title)
        assertTrue(shown.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(shown.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        // 14 minutes left on the app's clock (the screen's 14:00): the phone's clock 14 minutes
        // on, and the part second the chronometer drops.
        assertTrue(shown.`when` in (before + 840_999L)..(after + 840_999L))
        assertEquals(CountdownService.CHANNEL_ID, shown.channelId)
        val channel = manager.getNotificationChannel(CountdownService.CHANNEL_ID)!!
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertEquals("Emergency in progress", channel.name)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        assertTrue(shown.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(Notification.VISIBILITY_PUBLIC, shown.visibility)
    }

    @Test
    fun tappingItOpensTheEmergency() {
        countdown.sync(startedAt)
        val shown = shadowOf(run(commands().single()).get()).lastForegroundNotification
        val open = shadowOf(shown.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, open.component?.className)
        assertTrue(open.getBooleanExtra(CountdownNotifications.EXTRA_OPEN_EMERGENCY, false))
    }

    @Test
    fun atTheExpiryItTurnsIntoTheCallTo911() {
        language = "es"
        countdown.sync(startedAt)
        run(commands().single())
        pass(839_999)
        assertEquals("Emergencia en curso", shadowOf(manager).getNotification(CountdownService.NOTIFICATION_ID).title)
        pass(1)
        val shown = shadowOf(manager).getNotification(CountdownService.NOTIFICATION_ID)
        assertEquals("Hora de llamar al 911", shown.title)
        assertFalse(shown.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(shown.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun anEmergencyPastItsExpiryShowsTheCallTo911() {
        countdown.sync(Instant.fromEpochMilliseconds(now - 20 * 60_000L).toString())
        val shown = shadowOf(run(commands().single()).get()).lastForegroundNotification
        assertEquals("Time to Call 911", shown.title)
        assertTrue(shown.extras.getString(Notification.EXTRA_TEXT)!!.startsWith("The 15-minute search is up"))
    }

    @Test
    fun stoppingIsHarmlessWhenItIsntRunning() {
        countdown.sync(null)
        val service = run(commands().single())
        assertTrue(shadowOf(service.get()).isStoppedBySelf)
        assertEquals(0, shadowOf(service.get()).lastForegroundNotificationId)
    }

    @Test
    fun anUnreadableStartShowsNothing() {
        countdown.sync("not a time")
        assertTrue(commands().isEmpty())
    }
}
