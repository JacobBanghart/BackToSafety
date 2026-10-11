package com.backtosafety.app.core

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import java.io.File

/**
 * Brings the Rust core up off the main thread, from Application.onCreate: it loads JNA and the
 * library and registers UniFFI's ~300 functions (about 30 ms on the emulator, the biggest single
 * cost of a cold start), opens the database and reads the locale files. The first screen's
 * setup overlaps with that instead of waiting for it, and [await] is where it's needed.
 */
object CoreStartup {
    class Ready(val store: Store, val translations: Translations)

    private val ready = CompletableDeferred<Ready>()

    /** Starts the work once; later calls do nothing. */
    @Synchronized
    fun begin(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        Thread({
            try {
                // Where the RN app's expo-sqlite kept nijii.db, and the KMP app after it (spec/storage.md).
                val store = Store.open(File(app.filesDir, "SQLite/nijii.db").absolutePath)
                ready.complete(Ready(store, loadTranslations(app)))
            } catch (t: Throwable) {
                ready.completeExceptionally(t)
            }
        }, "core-startup").start()
    }

    private var started = false

    suspend fun await(): Ready = ready.await()

    /** The RN app's i18n/locales JSON, synced into assets/locales at build time. */
    private fun loadTranslations(context: Context): Translations = Translations.fromJson(
        context.assets.list("locales")!!.associateWith { locale ->
            context.assets.list("locales/$locale")!!.associate { file ->
                file.removeSuffix(".json") to context.assets.open("locales/$locale/$file").bufferedReader().use { it.readText() }
            }
        },
    )
}
