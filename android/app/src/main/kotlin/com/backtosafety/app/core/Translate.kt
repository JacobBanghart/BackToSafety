package com.backtosafety.app.core

import com.backtosafety.core.ffi.Translator
import com.backtosafety.core.ffi.Translations as CoreTranslations

/**
 * Looks up a translated string. `vars` fill `{{name}}` placeholders; a `count` var picks the
 * `_one`/`_other` plural form, an `ns` var reads from another namespace, and a `defaultValue`
 * var stands in for a missing key. The lookup itself is the Rust core's.
 */
class Translate internal constructor(internal val core: Translator) : (String, Map<String, Any?>) -> String {
    override fun invoke(key: String, vars: Map<String, Any?>): String = core.tv(key, vars.toPropValues())
}

/** `t(key)` with no variables. */
operator fun Translate.invoke(key: String): String = core.t(key)

/** The locale files, bound to a locale and namespace as [Translate]s. */
class Translations private constructor(private val core: CoreTranslations) {
    fun translator(locale: String, namespace: String): Translate = Translate(core.translator(locale, namespace))

    companion object {
        /** From the locale files' raw JSON (locale -> namespace -> text). */
        fun fromJson(resources: Map<String, Map<String, String>>): Translations = Translations(CoreTranslations.fromJson(resources))
    }
}
