package com.backtosafety.app

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.backtosafety.app.emergency.BootReceiver
import com.backtosafety.app.emergency.CountdownNotifications
import com.backtosafety.app.emergency.CountdownService
import com.backtosafety.app.emergency.OngoingCountdown
import com.backtosafety.app.emergency.resumeAfterRestart
import com.backtosafety.core.ActiveEmergency
import com.backtosafety.core.AppClock
import com.backtosafety.core.CountdownAlert
import com.backtosafety.core.EmergencyAway
import com.backtosafety.core.Translations
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import kotlin.time.Instant

/**
 * After the phone restarts mid-emergency: the alert that came due while it was off is posted,
 * the rest are scheduled again (as shared EmergencyAway.restarted says), and the countdown
 * shows again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BootReceiverTest {
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
    private val notificationManager = shadowOf(context.getSystemService(NotificationManager::class.java))
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private lateinit var db: AppDatabase
    private lateinit var services: AppServices

    /** The app clock at 2026-10-06 16:00 UTC. */
    private val now = 1_791_302_400_000L

    @Before
    fun setUp() {
        AppClock.testSeamsEnabled = true
        AppClock.freeze(now)
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .allowMainThreadQueries()
            .build()
        val store = Store(db)
        val emergencyT = { translations.translator(language, "emergency") }
        val notifications = CountdownNotifications(context, emergencyT) { "911" }
        services = AppServices(
            store, translations, notifications, EmergencyAway(store, notifications),
            OngoingCountdown(context, emergencyT) { "911" }, CoroutineScope(Dispatchers.Unconfined),
        )
        runBlocking { store.seed() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** An emergency started [minutesAgo] minutes ago, left for another screen at minute 2. */
    private fun emergency(minutesAgo: Int) = runBlocking {
        val startedMs = now - minutesAgo * 60_000L
        services.store.saveActiveEmergency(ActiveEmergency(Instant.fromEpochMilliseconds(startedMs).toString(), "", emptyList()))
        services.store.putSetting(Store.EMERGENCY_AWAY_FROM, (startedMs + 120_000L).toString())
    }

    private fun restart() = runBlocking { resumeAfterRestart(services) { language = it } }

    private fun posted() = notificationManager.allNotifications

    private fun startedServices(): List<Intent> = generateSequence { shadowOf(context).nextStartedService }.toList()

    @Test
    fun theAlertThatCameDueIsPostedAndTheRestScheduled() {
        emergency(minutesAgo = 11)
        restart()
        assertEquals(listOf(CountdownNotifications.channelId(CountdownAlert.WARNING)), posted().map { it.channelId })
        // The expiry, four minutes on, on the elapsed-time clock a restart starts afresh.
        assertEquals(listOf(SystemClock.elapsedRealtime() + 240_000L), alarms.scheduledAlarms.map { it.triggerAtTime })
        val countdown = startedServices().single()
        assertEquals(CountdownService::class.java.name, countdown.component?.className)
        assertEquals(now - 660_000L, countdown.getLongExtra(CountdownService.EXTRA_STARTED_AT, -1))
    }

    @Test
    fun pastTheExpiryTheCallTo911IsPosted() {
        emergency(minutesAgo = 30)
        restart()
        assertEquals("Time to Call 911", posted().single().extras.getString("android.title"))
        assertTrue(alarms.scheduledAlarms.isEmpty())
        assertEquals(1, startedServices().size)
    }

    @Test
    fun theAlertsAreInTheLanguageLastChosen() {
        runBlocking { services.store.putSetting(Store.LANGUAGE_PREFERENCE, "es") }
        emergency(minutesAgo = 30)
        restart()
        // Spanish shows in dev builds, which the tests are.
        assertEquals("Hora de llamar al 911", posted().single().extras.getString("android.title"))
    }

    @Test
    fun withoutAnEmergencyNothingHappens() {
        restart()
        assertTrue(posted().isEmpty())
        assertTrue(alarms.scheduledAlarms.isEmpty())
        assertTrue(startedServices().isEmpty())
    }

    @Test
    fun theReceiverIsThereForTheBoot() {
        val receivers = context.packageManager.queryBroadcastReceivers(Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(context.packageName), 0)
        assertEquals(listOf(BootReceiver::class.java.name), receivers.map { it.activityInfo.name })
    }

    @Test
    fun otherBroadcastsAreIgnored() {
        BootReceiver().onReceive(context, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(posted().isEmpty())
        assertTrue(startedServices().isEmpty())
    }
}
