package com.backtosafety.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.backtosafety.core.AppClock
import com.backtosafety.core.Translations
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.databaseBuilder
import com.backtosafety.core.db.databasePath
import com.backtosafety.core.db.openAppDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppClock.testSeamsEnabled = BuildConfig.TEST_SEAMS
        handleTestSeam(intent)
        val path = databasePath(this)
        val store = Store(openAppDatabase(path, databaseBuilder(this, path)))
        val translations = loadTranslations()
        setContent {
            // testTag(...) values surface as Android resource IDs, matching the RN
            // app's testIDs, so the same Maestro flows drive both (spec/testids.json).
            App(store, translations, Modifier.semantics { testTagsAsResourceId = true })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTestSeam(intent)
    }

    /**
     * app/debug/clock.tsx: `backtosafety://debug/clock?at=<ISO time>` freezes the clock,
     * `?advance=<seconds>` moves it forward. The current screen stays; it re-reads the clock.
     */
    private fun handleTestSeam(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.host != "debug" || uri.path != "/clock") return
        uri.getQueryParameter("at")?.let { at ->
            runCatching { java.time.OffsetDateTime.parse(at).toInstant().toEpochMilli() }.getOrNull()
                ?.let(AppClock::freeze)
        }
        uri.getQueryParameter("advance")?.toDoubleOrNull()?.let { AppClock.advance((it * 1000).toLong()) }
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
