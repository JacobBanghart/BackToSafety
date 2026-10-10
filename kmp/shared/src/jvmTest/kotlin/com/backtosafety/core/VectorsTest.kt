package com.backtosafety.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Runs every JSON file in spec/vectors against the Kotlin core: the twin of
 * spec/vectors.test.ts, which holds the RN app to the same input→output pairs.
 * Every case must have a blessed `expected` (blessing happens on the RN side).
 */
class VectorsTest {
    private val root = File(System.getProperty("repoRoot") ?: error("repoRoot system property not set"))

    private val translations = Translations(
        File(root, "i18n/locales").listFiles()!!.filter { it.isDirectory }.associate { locale ->
            locale.name to locale.listFiles()!!.filter { it.extension == "json" }.associate { file ->
                file.nameWithoutExtension to Json.parseToJsonElement(file.readText()).jsonObject
            }
        },
    )

    private fun JsonElement.str(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonArray.str(i: Int): String = this[i].str()!!
    private fun localDate(iso: String) = LocalDate.parse(iso)
    private fun json(value: String?): JsonElement = if (value == null) JsonNull else JsonPrimitive(value)
    private fun json(value: Int?): JsonElement = if (value == null) JsonNull else JsonPrimitive(value)
    private fun json(values: List<String>): JsonElement = JsonArray(values.map(::JsonPrimitive))

    private fun profile(o: JsonObject): Profile {
        fun f(name: String) = o[name]?.str()
        return Profile(
            name = f("name")!!, nickname = f("nickname"), dateOfBirth = f("dateOfBirth"),
            photoUri = f("photoUri"), height = f("height"), weight = f("weight"),
            hairColor = f("hairColor"), eyeColor = f("eyeColor"), identifyingMarks = f("identifyingMarks"),
            medicalConditions = f("medicalConditions"), medications = f("medications"),
            allergies = f("allergies"), cognitiveStatus = f("cognitiveStatus"),
            dominantHand = f("dominantHand"), mobilityLevel = f("mobilityLevel"),
            communicationPreference = f("communicationPreference"), escalationSigns = f("escalationSigns"),
            deescalationTechniques = f("deescalationTechniques"), approachGuidance = f("approachGuidance"),
            likes = f("likes"), dislikesTriggers = f("dislikesTriggers"), safeWord = f("safeWord"),
            locativeDeviceInfo = f("locativeDeviceInfo"), idBracelets = f("idBracelets"),
            medicAlertId = f("medicAlertId"), medicAlertHotline = f("medicAlertHotline"),
        )
    }

    private fun readoutInput(o: JsonObject) = ReadoutInput(
        profile = profile(o["profile"]!!.jsonObject),
        lastSeenTime = o["lastSeenTime"]?.str(),
        today = localDate(o["today"]!!.str()!!),
    )

    private fun ms(iso: String) = kotlin.time.Instant.parse(iso).toEpochMilliseconds()
    private fun iso(ms: Long) = json(kotlin.time.Instant.fromEpochMilliseconds(ms).toString())
    private fun alert(key: String) = CountdownAlert.entries.single { it.key == key }
    private fun json(properties: Map<String, Any?>): JsonElement = JsonObject(
        properties.mapValues { (_, v) ->
            when (v) {
                null -> JsonNull
                is Boolean -> JsonPrimitive(v)
                is Number -> JsonPrimitive(v)
                else -> JsonPrimitive(v.toString())
            }
        },
    )

    private fun readoutT(args: JsonArray) = translations.translator(args.str(0), "readout")
    private fun emergencyT(args: JsonArray) = translations.translator(args.str(0), "emergency")

    private val adapters: Map<String, (JsonArray) -> JsonElement> = mapOf(
        "parseDob" to { a -> json(parseDob(a.str(0))?.toString()) },
        "ageOn" to { a -> json(ageOn(a.str(0), localDate(a.str(1)))) },
        "formatDob" to { a -> json(formatDob(localDate(a.str(0)))) },
        "formatHeightInput" to { a -> json(formatHeightInput(a.str(0))) },
        "formatWeightInput" to { a -> json(formatWeightInput(a.str(0))) },
        "formatMedicAlertIdInput" to { a -> json(formatMedicAlertIdInput(a.str(0))) },
        "formatDobInput" to { a -> json(formatDobInput(a.str(0))) },
        "formatPhoneNumber" to { a -> json(formatPhoneNumber(a.str(0))) },
        "formatPhoneInput" to { a -> json(formatPhoneInput(a.str(0))) },
        "stripPhoneFormatting" to { a -> json(stripPhoneFormatting(a.str(0))) },
        "normalizeSmsRecipient" to { a -> json(normalizeSmsRecipient(a.str(0))) },
        "normalizeUniqueSmsRecipients" to { a ->
            json(normalizeUniqueSmsRecipients(a[0].jsonArray.map { it.str()!! }))
        },
        "photoFileName" to { a -> json(photoFileName(a.str(0))) },
        "describeMobility" to { a -> json(describeMobility(a.str(1), readoutT(a))) },
        "needsVehicleCheck" to { a -> JsonPrimitive(needsVehicleCheck(a[0].str())) },
        "vehicleCheckKind" to { a -> json(vehicleCheckKind(a.str(0)).key) },
        "buildScript" to { a -> json(buildScript(readoutInput(a[1].jsonObject), readoutT(a))) },
        "buildCopyBlock" to { a -> json(buildCopyBlock(readoutInput(a[1].jsonObject), readoutT(a))) },
        "missingScriptDetails" to { a -> json(missingScriptDetails(readoutInput(a[1].jsonObject), readoutT(a))) },
        "buildInitialSteps" to { a ->
            JsonArray(
                buildInitialSteps(emergencyT(a), a.str(1)).map { s ->
                    buildJsonObject {
                        put("id", s.id)
                        put("step", s.step)
                        put("title", s.title)
                        put("description", s.description)
                        s.hint?.let { put("hint", it) }
                        if (s.urgent) put("urgent", true)
                        put("checked", s.checked)
                    }
                },
            )
        },
        "secondsRemaining" to { a ->
            json(secondsRemaining(kotlin.time.Instant.parse(a.str(0)).toEpochMilliseconds(),
                kotlin.time.Instant.parse(a.str(1)).toEpochMilliseconds()))
        },
        "countdownAlerts" to { a -> json(countdownAlerts(a[0].jsonPrimitive.int, a[1].jsonPrimitive.int).map { it.key }) },
        "formatCountdown" to { a -> json(formatCountdown(a[0].jsonPrimitive.int)) },
        "buildAlertSms" to { a ->
            val o = a[1].jsonObject
            json(buildAlertSms(emergencyT(a), o["name"]?.str(), o["startedTime"]!!.str()!!, o["wearing"]!!.str()!!))
        },
        "directionHint" to { a -> json(directionHint(emergencyT(a), a[1].str())) },
        "alertDueAt" to { a -> iso(alertDueAtMs(alert(a.str(0)), ms(a.str(1)))) },
        "alertsToSchedule" to { a ->
            JsonArray(
                alertsToSchedule(ms(a.str(0)), ms(a.str(1))).map { s ->
                    buildJsonObject {
                        put("alert", s.alert.key)
                        put("fireAt", iso(s.fireAtMs))
                    }
                },
            )
        },
        "catchUpAlert" to { a -> json(catchUpAlert(ms(a.str(0)), ms(a.str(1)), ms(a.str(2)))?.key) },
        "countdownAlertProperties" to { a ->
            json(countdownAlertProperties(alert(a.str(0)), AlertDelivery.entries.single { it.key == a.str(1) }))
        },
        "emergencyEndedProperties" to { a -> json(emergencyEndedProperties(ms(a.str(0)), ms(a.str(1)), a[2].jsonPrimitive.int)) },
        "readinessProperties" to { a ->
            val p = (a[0] as? JsonObject)?.let(::profile)
            json(readinessProperties(p, a[1].jsonPrimitive.int, a[2].jsonPrimitive.int, a[3].jsonPrimitive.int))
        },
        "smsResultProperties" to { a ->
            json(smsResultProperties(SmsResult.entries.single { it.key == a.str(0) }, a[1].jsonPrimitive.int))
        },
        "dialFailedProperties" to { a -> json(dialFailedProperties(DialTarget.entries.single { it.key == a.str(0) }, a.str(1))) },
        "parseActiveEmergency" to { a ->
            parseActiveEmergency(a[0].str())?.let { e ->
                buildJsonObject {
                    put("startedAt", e.startedAt)
                    put("wearing", e.wearing)
                    put("checkedSteps", json(e.checkedSteps))
                    put("isActive", e.isActive)
                    e.incidentId?.let { put("incidentId", it) }
                }
            } ?: JsonNull
        },
    )

    @Test
    fun everyVectorMatches() {
        val failures = mutableListOf<String>()
        var cases = 0
        val files = File(root, "spec/vectors").listFiles()!!.filter { it.extension == "json" }.sortedBy { it.name }
        for (file in files) {
            val functions = Json.parseToJsonElement(file.readText()).jsonObject["functions"]!!.jsonObject
            for ((fn, list) in functions) {
                val adapter = adapters[fn]
                if (adapter == null) {
                    failures += "${file.name} $fn: no Kotlin adapter"
                    continue
                }
                list.jsonArray.forEachIndexed { i, case ->
                    cases++
                    val c = case.jsonObject
                    val expected = c["expected"] ?: run {
                        failures += "${file.name} $fn[$i]: no expected value (bless on the RN side)"
                        return@forEachIndexed
                    }
                    val actual = runCatching { adapter(c["args"]!!.jsonArray) }
                        .getOrElse { e -> JsonPrimitive("threw ${e::class.simpleName}: ${e.message}") }
                    if (!sameJson(actual, expected)) {
                        failures += "${file.name} $fn[$i] ${c["args"]}\n    expected $expected\n    actual   $actual"
                    }
                }
            }
        }
        if (failures.isNotEmpty()) fail("${failures.size} of $cases vector cases failed:\n" + failures.joinToString("\n"))
        println("$cases vector cases passed")
    }

    /** JSON equality where 900 and 900.0 are the same number. */
    private fun sameJson(a: JsonElement, b: JsonElement): Boolean = when {
        a is JsonPrimitive && b is JsonPrimitive && !a.isString && !b.isString &&
            a.contentOrNull?.toDoubleOrNull() != null && b.contentOrNull?.toDoubleOrNull() != null ->
            a.contentOrNull!!.toDouble() == b.contentOrNull!!.toDouble()
        a is JsonArray && b is JsonArray -> a.size == b.size && a.indices.all { sameJson(a[it], b[it]) }
        a is JsonObject && b is JsonObject -> a.keys == b.keys && a.keys.all { sameJson(a[it]!!, b[it]!!) }
        else -> a == b
    }
}
