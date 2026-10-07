package com.backtosafety.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
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
        val path = databasePath(this)
        val store = Store(openAppDatabase(path, databaseBuilder(this, path)))
        val translations = loadTranslations()
        setContent {
            // testTag(...) values surface as Android resource IDs, matching the RN
            // app's testIDs, so the same Maestro flows drive both (spec/testids.json).
            App(store, translations, Modifier.semantics { testTagsAsResourceId = true })
        }
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
