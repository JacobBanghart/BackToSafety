package com.backtosafety.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Instant

// The emergency search protocol: the checklist, the 15-minute countdown and the contact
// alert text. Port of utils/emergency.ts; held to spec/vectors/emergency.json.
// `t` is bound to the `emergency` namespace.

const val SEARCH_WINDOW_SECONDS = 15 * 60
const val WARNING_AT_SECONDS = 5 * 60

data class ChecklistStep(
    val id: String,
    val step: Int,
    val title: String,
    val description: String,
    val hint: String? = null,
    val urgent: Boolean = false,
    val checked: Boolean = false,
)

private val STEP_IDS = listOf(
    "home_search", "outside_immediate", "neighbors", "radius_search", "high_risk",
    "familiar_places", "call_911", "silver_alert", "share_info", "coordinate", "document",
)
private val URGENT_STEPS = setOf("high_risk", "call_911")

fun buildInitialSteps(t: Translate, emergencyNumber: String): List<ChecklistStep> {
    val number = mapOf("emergencyNumber" to emergencyNumber)
    return STEP_IDS.mapIndexed { index, id ->
        ChecklistStep(
            id = id,
            step = index + 1,
            title = t("steps.$id.title", if (id == "call_911") number else emptyMap()),
            description = t("steps.$id.description", if (id == "silver_alert") number else emptyMap()),
            hint = if (id == "home_search") t("steps.$id.hint") else null,
            urgent = id in URGENT_STEPS,
        )
    }
}

/** Seconds left in the search window, between 0 and the full window. */
fun secondsRemaining(startedAtMs: Long, nowMs: Long): Int {
    val elapsed = (nowMs - startedAtMs).floorDiv(1000L)
    return (SEARCH_WINDOW_SECONDS - elapsed).coerceIn(0, SEARCH_WINDOW_SECONDS.toLong()).toInt()
}

enum class CountdownAlert(val key: String) { WARNING("warning"), EXPIRED("expired") }

/**
 * Alerts due when the countdown moves from [prev] to [next] seconds left. Ticks can skip
 * seconds (the app was in the background), so alerts fire on crossing a threshold.
 */
fun countdownAlerts(prev: Int, next: Int): List<CountdownAlert> = buildList {
    if (prev >= WARNING_AT_SECONDS && next < WARNING_AT_SECONDS && next > 0) add(CountdownAlert.WARNING)
    if (prev > 0 && next == 0) add(CountdownAlert.EXPIRED)
}

/** MM:SS for the countdown. */
fun formatCountdown(seconds: Int): String =
    "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"

/** Text sent to emergency contacts. [startedTime] arrives already formatted. */
fun buildAlertSms(t: Translate, name: String?, startedTime: String, wearing: String): String {
    val wearingText = if (wearing.isNotEmpty()) t("smsWearing", mapOf("wearing" to wearing)) else ""
    return t(
        "smsMessage",
        mapOf(
            "name" to (name?.takeIf { it.isNotEmpty() } ?: t("smsUnknownName")),
            "time" to startedTime,
            "wearing" to wearingText,
        ),
    )
}

/** Which way they may veer, from their dominant hand. */
fun directionHint(t: Translate, dominantHand: String?): String? = when (dominantHand) {
    "left" -> t("directionHint.left")
    "right" -> t("directionHint.right")
    else -> null
}

/** The `active_emergency` settings record (spec/storage.md). */
data class ActiveEmergency(
    val startedAt: String,
    val wearing: String,
    val checkedSteps: List<String>,
    /** The incidents row for this emergency; absent for emergencies started before it existed. */
    val incidentId: Long? = null,
) {
    val isActive: Boolean get() = true
}

/** Parses the stored record. Null when nothing is active or the value can't be read. */
fun parseActiveEmergency(raw: String?): ActiveEmergency? {
    if (raw.isNullOrEmpty()) return null
    val state = runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return null
    if ((state["isActive"] as? JsonPrimitive)?.booleanOrNull != true) return null
    val startedAt = (state["startedAt"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    if (runCatching { Instant.parse(startedAt) }.isFailure) return null

    return ActiveEmergency(
        startedAt = startedAt,
        wearing = (state["wearing"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: "",
        checkedSteps = runCatching { state["checkedSteps"]!!.jsonArray }.getOrNull()
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull }
            ?: emptyList(),
        incidentId = (state["incidentId"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull,
    )
}

/**
 * The stored form of [ActiveEmergency], in the same key order the RN app writes
 * (JSON.stringify of { startedAt, wearing, checkedSteps, isActive, incidentId? }).
 */
fun serializeActiveEmergency(e: ActiveEmergency): String = buildJsonObject {
    put("startedAt", e.startedAt)
    put("wearing", e.wearing)
    put("checkedSteps", JsonArray(e.checkedSteps.map(::JsonPrimitive)))
    put("isActive", true)
    e.incidentId?.let { put("incidentId", it) }
}.toString()
