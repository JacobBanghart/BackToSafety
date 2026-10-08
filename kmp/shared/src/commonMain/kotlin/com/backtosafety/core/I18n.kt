package com.backtosafety.core

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Looks up a translated string. `vars` fill `{{name}}` placeholders; a `count` var picks
 * the `_one`/`_other` plural form, an `ns` var reads from another namespace, and a
 * `defaultValue` var stands in for a missing key.
 */
typealias Translate = (key: String, vars: Map<String, Any?>) -> String

/**
 * The same i18n/locales JSON the RN app ships, with the subset of i18next behavior the
 * app uses: nested keys by dots, English fallback (also for empty strings, as the RN app
 * sets returnEmptyString: false), `{{var}}` interpolation and English/Spanish plurals.
 * A key found nowhere comes back as its defaultValue, or else the key itself, as in i18next.
 */
class Translations(
    /** locale -> namespace -> parsed JSON */
    private val resources: Map<String, Map<String, JsonObject>>,
    private val fallbackLocale: String = "en",
) {
    fun translator(locale: String, namespace: String): Translate = { key, vars ->
        val ns = vars["ns"] as? String ?: namespace
        val resolvedKey = pluralKey(locale, ns, key, vars["count"])
        val template = lookup(locale, ns, resolvedKey)
            ?: lookup(fallbackLocale, ns, resolvedKey)
            ?: vars["defaultValue"] as? String
            ?: resolvedKey
        interpolate(template, vars)
    }

    private fun pluralKey(locale: String, ns: String, key: String, count: Any?): String {
        if (count !is Number) return key
        // English and Spanish: one for exactly 1, other otherwise.
        val form = if (count.toLong() == 1L && count.toDouble() == 1.0) "${key}_one" else "${key}_other"
        return if (lookup(locale, ns, form) != null || lookup(fallbackLocale, ns, form) != null) form else key
    }

    private fun lookup(locale: String, ns: String, key: String): String? {
        var node: JsonElement = resources[locale]?.get(ns) ?: return null
        for (part in key.split('.')) {
            node = (node as? JsonObject)?.get(part) ?: return null
        }
        return (node as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
    }

    private fun interpolate(template: String, vars: Map<String, Any?>): String =
        PLACEHOLDER.replace(template) { match ->
            val name = match.groupValues[1]
            if (name in vars) formatValue(vars[name]) else match.value
        }

    private fun formatValue(value: Any?): String = when (value) {
        null -> ""
        is Double -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        else -> value.toString()
    }

    private companion object {
        val PLACEHOLDER = Regex("""\{\{\s*(\w+)\s*\}\}""")
    }
}

/** `t(key)` with no variables. */
operator fun Translate.invoke(key: String): String = this(key, emptyMap())
