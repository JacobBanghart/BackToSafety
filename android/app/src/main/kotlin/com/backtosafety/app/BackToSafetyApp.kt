package com.backtosafety.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.backtosafety.app.core.Analytics
import com.backtosafety.app.core.AppClock
import com.backtosafety.app.core.EmergencyAway
import com.backtosafety.app.core.Store
import com.backtosafety.app.core.Translations
import com.backtosafety.app.core.invoke
import com.backtosafety.app.emergency.CountdownNotifications
import com.backtosafety.app.emergency.OngoingCountdown
import com.backtosafety.app.ui.PhotoFiles
import com.backtosafety.app.ui.runCatchingCancellable
import com.posthog.PersonProfiles
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
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
    /** The emergency's countdown as an ongoing notification, while one runs. */
    val countdown: OngoingCountdown,
    /** Work that must outlive the screen that starts it, such as scheduling alerts as the app is left. */
    val scope: CoroutineScope,
)

/**
 * The process: sets up analytics, and brings the Rust core up off the main thread from
 * [onCreate]: loading the library and registering UniFFI's functions is the biggest single cost
 * of a cold start, so it overlaps with the activity's setup instead of following it. [services]
 * is published once MainActivity (or a receiver) asks ([start]); the splash screen stays up
 * until it arrives.
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

    private val opened = CompletableDeferred<AppServices>()

    override fun onCreate() {
        super.onCreate()
        setUpAnalytics()
        Thread({
            try {
                opened.complete(open())
            } catch (t: Throwable) {
                opened.completeExceptionally(t)
            }
        }, "core-startup").start()
    }

    /**
     * Publishes [services] once the core is up, applying a test build's debug clock link
     * ([clockLink]) first, so the clock it freezes is the one the first screen reads. Once per
     * process; later calls do nothing.
     */
    fun start(clockLink: String? = null) {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            val services = opened.await()
            clockLink?.let(AppClock::handleLink)
            _services.value = services
        }
    }

    /**
     * Opens the database, adopting the one the RN and KMP apps left, and migrates it. Runs on
     * the core-startup thread.
     */
    private fun open(): AppServices {
        // Only ever turned on, never off: a test that turned the seams on must not race this thread.
        if (BuildConfig.TEST_SEAMS) AppClock.testSeamsEnabled = true
        // Where the RN app's expo-sqlite kept nijii.db, and the KMP app after it (spec/storage.md).
        val store = Store.open(File(filesDir, "SQLite/nijii.db").absolutePath)
        store.photoCleaner = { keeping -> PhotoFiles.clean(filesDir, cacheDir, keeping) }
        val translations = loadTranslations(this)
        val emergencyT = { translations.translator(language, "emergency") }
        val emergencyNumber = { translations.translator(language, "common")("emergencyNumber") }
        val notifications = CountdownNotifications(this, emergencyT, emergencyNumber)
        runBlocking { migrate(store) }
        return AppServices(
            store, translations, notifications, EmergencyAway(store, notifications), OngoingCountdown(this, emergencyT, emergencyNumber), scope,
        )
    }

    /**
     * Runs [block] on the main thread once [services] is open, opening it if no activity has
     * (a receiver woke the process).
     */
    fun withServices(block: suspend (AppServices) -> Unit) {
        start()
        scope.launch { block(services.filterNotNull().first()) }
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
}

/** The RN app's i18n/locales JSON, synced into assets/locales at build time. */
internal fun loadTranslations(context: Context): Translations = Translations.fromJson(
    context.assets.list("locales")!!.associateWith { locale ->
        context.assets.list("locales/$locale")!!.associate { file ->
            file.removeSuffix(".json") to context.assets.open("locales/$locale/$file").bufferedReader().use { it.readText() }
        }
    },
)

private const val TAG = "BackToSafety"

/**
 * Seeds the database; the first launch on a database the RN app left also migrates it, and the
 * core reports data_migrated. If that fails, the app still starts, as on iOS: its screens meet
 * the failure through their own queries and show their defaults. The next launch tries the
 * migration again (it runs in one transaction, so the file is still the RN one). The core
 * reports a failure and flushes it at once, since a queued event wouldn't survive a crash.
 * Returns whether it worked.
 */
internal suspend fun migrate(store: Store): Boolean {
    val seeded = runCatchingCancellable { store.seed() }
    seeded.onFailure { Log.e(TAG, "Preparing the database failed", it) }
    // The device is the analytics identity (as on iOS).
    seeded.onSuccess {
        runCatchingCancellable { store.deviceId() }.onSuccess { if (BuildConfig.POSTHOG_KEY.isNotEmpty()) PostHog.identify(it) }
    }
    return seeded.isSuccess
}
