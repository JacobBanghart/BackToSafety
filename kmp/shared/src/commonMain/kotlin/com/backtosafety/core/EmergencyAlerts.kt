package com.backtosafety.core

// When the countdown alerts are due, what to schedule on the phone while the emergency
// screen isn't showing, what to catch up on when it shows again, and the properties of the
// metrics around them. Both apps call these rather than deciding anything themselves; held to
// spec/vectors/alerts.json.

/** When [alert] is due for an emergency started at [startedAtMs]: the moment [countdownAlerts] fires it. */
fun alertDueAtMs(alert: CountdownAlert, startedAtMs: Long): Long = when (alert) {
    // The warning fires when fewer than WARNING_AT_SECONDS remain: one second after the mark.
    CountdownAlert.WARNING -> startedAtMs + (SEARCH_WINDOW_SECONDS - WARNING_AT_SECONDS + 1) * 1000L
    CountdownAlert.EXPIRED -> startedAtMs + SEARCH_WINDOW_SECONDS * 1000L
}

/** An alert for the phone to deliver as a notification at [fireAtMs]. */
data class ScheduledAlert(val alert: CountdownAlert, val fireAtMs: Long)

/**
 * The alerts to schedule when the emergency screen stops showing at [nowMs] (the app went to
 * the background, or the user went to another screen): those not yet due, earliest first.
 */
fun alertsToSchedule(startedAtMs: Long, nowMs: Long): List<ScheduledAlert> =
    CountdownAlert.entries.map { ScheduledAlert(it, alertDueAtMs(it, startedAtMs)) }.filter { it.fireAtMs > nowMs }

/**
 * The alert to show when the emergency screen shows again at [nowMs], after being away since
 * [awayFromMs]: the most serious one that came due in between, or null. Expiry outranks the
 * warning, and nothing that was already due before leaving repeats. [awayFromMs] is the screen's
 * last countdown tick before it stopped showing, so an alert due between that tick and leaving
 * is caught up rather than lost.
 */
fun catchUpAlert(startedAtMs: Long, awayFromMs: Long, nowMs: Long): CountdownAlert? =
    listOf(CountdownAlert.EXPIRED, CountdownAlert.WARNING).firstOrNull {
        alertDueAtMs(it, startedAtMs) in (awayFromMs + 1)..nowMs
    }

/** How a countdown alert reached the caregiver: on the emergency screen, or caught up on return. */
enum class AlertDelivery(val key: String) { IN_APP("in_app"), CATCH_UP("catch_up") }

/** countdown_alert's properties. */
fun countdownAlertProperties(alert: CountdownAlert, delivery: AlertDelivery): Map<String, Any?> =
    mapOf("kind" to alert.key, "delivery" to delivery.key)

/**
 * emergency_resumed's properties: back on the emergency screen after [awayFromMs], and the
 * alert caught up on, if any.
 */
fun emergencyResumedProperties(awayFromMs: Long, nowMs: Long, caughtUp: CountdownAlert?): Map<String, Any?> =
    mapOf("away_s" to ((nowMs - awayFromMs) / 1000L).coerceAtLeast(0), "catch_up" to caughtUp?.key)

/** emergency_completed's and emergency_cancelled's properties. */
fun emergencyEndedProperties(startedAtMs: Long, nowMs: Long, checkedCount: Int): Map<String, Any?> =
    mapOf("checked_count" to checkedCount, "duration_s" to ((nowMs - startedAtMs) / 1000L).coerceAtLeast(0))

/**
 * How ready the info sheet is, sent with app_ready: what's filled in before anyone needs it.
 * [contactsAlerted] counts contacts with notify-on-emergency on.
 */
fun readinessProperties(profile: Profile?, contacts: Int, contactsAlerted: Int, places: Int): Map<String, Any?> {
    val fields = profile?.let {
        listOf(
            it.nickname, it.dateOfBirth, it.photoUri, it.height, it.weight, it.hairColor, it.eyeColor,
            it.identifyingMarks, it.medicalConditions, it.medications, it.allergies, it.cognitiveStatus,
            it.dominantHand?.takeIf { hand -> hand != "unknown" }, it.mobilityLevel, it.communicationPreference,
            it.escalationSigns, it.deescalationTechniques, it.approachGuidance, it.likes, it.dislikesTriggers,
            it.safeWord, it.locativeDeviceInfo, it.idBracelets, it.medicAlertId, it.medicAlertHotline,
        ).count { value -> !value.isNullOrBlank() }
    } ?: 0
    return mapOf(
        "has_profile" to (profile != null),
        "has_photo" to !profile?.photoUri.isNullOrBlank(),
        "has_medical" to !profile?.medicalConditions.isNullOrBlank(),
        "profile_fields_filled" to fields,
        "contacts_count" to contacts,
        "alert_contacts_count" to contactsAlerted,
        "places_count" to places,
    )
}

/** What happened to the alert text for the alert circle (emergency_sms_result). */
enum class SmsResult(val key: String) {
    SENT("sent"),
    CANCELLED("cancelled"),
    FAILED("failed"),

    /** The device can't send texts. */
    UNAVAILABLE("unavailable"),

    /** Handed to the messaging app, which doesn't report back (Android). */
    HANDED_OFF("handed_off"),
}

/** emergency_sms_result's properties. */
fun smsResultProperties(result: SmsResult, recipients: Int): Map<String, Any?> =
    mapOf("result" to result.key, "recipient_count" to recipients)

/** Which number a dial was for (dial_failed). */
enum class DialTarget(val key: String) { EMERGENCY("emergency"), CONTACT("contact"), MEDICALERT("medicalert") }

/** dial_failed's properties: the device couldn't place the call (no phone app: iPads, tablets). */
fun dialFailedProperties(target: DialTarget, screen: String): Map<String, Any?> = mapOf("target" to target.key, "screen" to screen)

/**
 * save_failed's properties: a write to the store failed and the user saw an error.
 * [errorType] is the error's type name (never its message, which could hold what was typed).
 */
fun saveFailedProperties(screen: String, action: String, errorType: String?): Map<String, Any?> =
    mapOf("screen" to screen, "action" to action, "error" to (errorType ?: "unknown"))
