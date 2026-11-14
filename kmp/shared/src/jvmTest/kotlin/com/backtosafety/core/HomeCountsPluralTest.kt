package com.backtosafety.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Home's Contacts and Places tiles say "1 saved" / "1 guardado" in the singular, not "1 guardados". */
class HomeCountsPluralTest {
    private val translations = Translations(
        File(File(System.getProperty("repoRoot") ?: error("repoRoot system property not set")), "i18n/locales")
            .listFiles()!!.filter { it.isDirectory }.associate { locale ->
            locale.name to locale.listFiles()!!.filter { it.extension == "json" }.associate { file ->
                file.nameWithoutExtension to Json.parseToJsonElement(file.readText()).jsonObject
            }
        },
    )

    @Test
    fun theSavedCountsAgreeWithTheirNumber() {
        for (key in listOf("quickActions.contactsSaved", "quickActions.placesSaved")) {
            val es = translations.translator("es", "home")
            val en = translations.translator("en", "home")
            assertEquals("1 guardado", es(key, mapOf("count" to 1)), key)
            assertEquals("2 guardados", es(key, mapOf("count" to 2)), key)
            assertEquals("1 saved", en(key, mapOf("count" to 1)), key)
            assertEquals("3 saved", en(key, mapOf("count" to 3)), key)
        }
    }
}
