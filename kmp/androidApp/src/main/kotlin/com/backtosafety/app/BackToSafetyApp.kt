package com.backtosafety.app

import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.backtosafety.app.emergency.CountdownNotifications
import com.backtosafety.app.ui.PhotoFiles
import com.backtosafety.core.Analytics
import com.backtosafety.core.AnalyticsEvent
import com.backtosafety.core.AppClock
import com.backtosafety.core.EmergencyAway
import com.backtosafety.core.Translations
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.DatabaseOpen
import com.backtosafety.core.db.dataMigrationFailedProperties
import com.backtosafety.core.db.databaseBuilder
import com.backtosafety.core.db.databasePath
import com.backtosafety.core.db.openAppDatabase
import com.backtosafety.core.invoke
import com.posthog.PersonProfiles
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What the app is built on, for as long as the process lives. The database, the translations
 * and the countdown alerts belong to the process, not to MainActivity: the activity is
 * recreated by changes the manifest doesn't absorb (font size, display size), and each new
 * one must find the same open database and the same [EmergencyAway] (which remembers the
 * last countdown tick), not make its own.
 */
class AppServices(
    val store: Store,
    val translations: Translations,
    val notifications: CountdownNotifications,
    val away: EmergencyAway,
    /** Work that must outlive the screen that starts it, such as scheduling alerts as the app is left. */
    val scope: CoroutineScope,
)

/**
 * The process: sets up analytics and the clock seam, and opens [AppServices] off the main
 * thread when MainActivity asks ([start]). The splash screen stays up until [services] arrives.
 */
class BackToSafetyApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val started = AtomicBoolean(false)
    private val _services = MutableStateFlow<AppServices?>(null)

    /** Null until [start]'s work is done; opened once, whatever the activity does. */
    val services: StateFlow<AppServices?> = _services

    /** The app's language, which the countdown alerts are translated in when scheduled. */
    @Volatile
    var language = "en"

    override fun onCreate() {
        super.onCreate()
        AppClock.testSeamsEnabled = BuildConfig.TEST_SEAMS
        setUpAnalytics()
    }

    /**
     * Opens the database, adopting the RN app's, and migrates it, off the main thread. Once per
     * process; MainActivity calls it after handling a test-seam intent, so the clock a seam
     * freezes is the one the first queries see.
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) { _services.value = open() }
    }

    private suspend fun open(): AppServices {
        val path = databasePath(this)
        val store = Store(openAppDatabase(path, databaseBuilder(this, path)))
        store.photoCleaner = { keeping -> PhotoFiles.clean(filesDir, cacheDir, keeping) }
        val translations = loadTranslations()
        val notifications = CountdownNotifications(
            this,
            emergencyT = { translations.translator(language, "emergency") },
            emergencyNumber = { translations.translator(language, "common")("emergencyNumber") },
        )
        migrate(store, DatabaseOpen.migratingFrom)
        return AppServices(store, translations, notifications, EmergencyAway(store, notifications), scope)
    }

    /**
     * utils/posthog.ts: the same PostHog options as the RN app. Without a key (dev and test
     * builds) nothing is set up and every event goes nowhere.
     */
    private fun setUpAnalytics() {
        if (BuildConfig.POSTHOG_KEY.isEmpty()) return
        val config = PostHogAndroidConfig(BuildConfig.POSTHOG_KEY, BuildConfig.POSTHOG_HOST).apply {
            captureApplicationLifecycleEvents = true
            captureScreenViews = false // screens are reported by route, as the RN app does
            preloadFeatureFlags = true
            personProfiles = PersonProfiles.IDENTIFIED_ONLY
            flushAt = 20
            flushIntervalSeconds = 10
            maxBatchSize = 100
            maxQueueSize = 1000
            // Off: the screens show medical details, the safe word and contacts' numbers as
            // plain text, which masking inputs and images wouldn't hide (docs/privacy-policy.md).
            sessionReplay = false
            errorTrackingConfig.autoCapture = true
        }
        PostHogAndroid.setup(applicationContext, config)
        Analytics.sink = { name, properties ->
            @Suppress("UNCHECKED_CAST")
            val props = properties.filterValues { it != null } as Map<String, Any>
            if (name == Analytics.SCREEN) {
                PostHog.screen(properties["\$screen_name"] as String, props - "\$screen_name")
            } else PostHog.capture(name, properties = props)
        }
        Analytics.flusher = { PostHog.flush() }
        Analytics.resetter = { PostHog.reset() }
    }

    /** The RN app's i18n/locales JSON, synced into assets/locales at build time. */
    private fun loadTranslations(): Translations = Translations(
        assets.list("locales")!!.associateWith { locale ->
            assets.list("locales/$locale")!!.associate { file ->
                file.removeSuffix(".json") to Json.parseToJsonElement(
                    assets.open("locales/$locale/$file").bufferedReader().readText(),
                ).jsonObject
            }
        },
    )
}

private const val TAG = "BackToSafety"

/**
 * The first query migrates a database the RN app left ([migratingFrom], data_migrated). If that,
 * or seeding, fails, the app still starts, as on iOS: its screens meet the failure through
 * their own queries and show their defaults. The next launch tries the migration again (it
 * runs in one transaction, so the file is still the RN one). The failure is reported and
 * flushed at once, since a queued event wouldn't survive a crash. Returns whether it worked.
 */
internal suspend fun migrate(store: Store, migratingFrom: Int?): Boolean {
    val migrationStart = SystemClock.elapsedRealtime()
    val seeded = runCatching { store.seed() }
    if (migratingFrom != null) {
        val durationMs = SystemClock.elapsedRealtime() - migrationStart
        Analytics.track(
            AnalyticsEvent.DATA_MIGRATED,
            seeded.fold(
                { store.dataMigratedProperties(migratingFrom, durationMs) },
                { dataMigrationFailedProperties(migratingFrom, durationMs, it::class.simpleName) },
            ),
        )
        if (seeded.isFailure) Analytics.flush()
    }
    seeded.onFailure { Log.e(TAG, "Preparing the database failed", it) }
    // The device is the analytics identity (as on iOS).
    seeded.onSuccess {
        runCatching { store.deviceId() }.onSuccess { if (BuildConfig.POSTHOG_KEY.isNotEmpty()) PostHog.identify(it) }
    }
    return seeded.isSuccess
}
