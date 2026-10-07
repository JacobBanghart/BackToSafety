package com.backtosafety.core

import kotlinx.datetime.LocalDate

// What the info sheet shows and copies: the 911 call script and the full-details block.
// Port of utils/readout.ts; held to spec/vectors/readout.json. `t` is bound to the
// `readout` namespace; times arrive already formatted by the platform.

class ReadoutInput(
    val profile: Profile,
    /** Last-seen time, formatted for display. */
    val lastSeenTime: String? = null,
    /** For the age line; screens pass the current date. */
    val today: LocalDate,
)

private val VEHICLE_MOBILITY_VALUES = listOf(
    "motorized wheelchair", "mobility scooter", "bicycle", "has vehicle",
    "manual wheelchair", "uses walker", "uses cane",
)

fun needsVehicleCheck(mobilityLevel: String?): Boolean {
    if (!mobilityLevel.isSet()) return false
    val value = mobilityLevel!!.lowercase()
    return VEHICLE_MOBILITY_VALUES.any { it in value }
}

enum class VehicleCheckKind(val key: String) { VEHICLE("vehicle"), AID("aid") }

/** Which note to show for a vehicle check: a vehicle or bike, or a mobility aid. */
fun vehicleCheckKind(mobilityLevel: String): VehicleCheckKind {
    val value = mobilityLevel.lowercase()
    return if (listOf("vehicle", "bicycle", "bike", "scooter").any { it in value }) VehicleCheckKind.VEHICLE
    else VehicleCheckKind.AID
}

fun describeAppearance(profile: Profile, t: Translate): String = buildList {
    if (profile.height.isSet()) add(profile.height!!)
    if (profile.weight.isSet()) add(profile.weight!!)
    if (profile.hairColor.isSet()) add(t("copyBlock.hairColor", mapOf("color" to profile.hairColor)))
    if (profile.eyeColor.isSet()) add(t("copyBlock.eyeColor", mapOf("color" to profile.eyeColor)))
    if (profile.identifyingMarks.isSet()) add(profile.identifyingMarks!!)
}.joinToString(", ")

fun describeImportantDetails(profile: Profile, t: Translate): String = buildList {
    if (profile.medicalConditions.isSet()) add(profile.medicalConditions!!)
    if (profile.allergies.isSet()) add(t("copyBlock.allergies", mapOf("value" to profile.allergies)))
}.joinToString(". ")

fun buildCopyBlock(input: ReadoutInput, t: Translate): String {
    val p = input.profile
    val appearance = describeAppearance(p, t)
    val important = describeImportantDetails(p, t)
    fun line(set: Boolean, key: String, vars: Map<String, Any?>): String? = if (set) t(key, vars) else null

    return listOfNotNull(
        if (p.nickname.isSet()) t("copyBlock.nameWithNickname", mapOf("name" to p.name, "nickname" to p.nickname))
        else t("copyBlock.name", mapOf("name" to p.name)),
        line(p.dateOfBirth.isSet(), "copyBlock.dob", mapOf("dob" to p.dateOfBirth)),
        line(appearance.isNotEmpty(), "copyBlock.appearance", mapOf("desc" to appearance)),
        line(important.isNotEmpty(), "copyBlock.importantDetails", mapOf("desc" to important)),
        line(p.medications.isSet(), "copyBlock.medications", mapOf("value" to p.medications)),
        line(p.cognitiveStatus.isSet(), "copyBlock.cognitiveStatus", mapOf("value" to p.cognitiveStatus)),
        if (p.mobilityLevel.isSet()) t("copyBlock.mobility", mapOf("value" to describeMobility(p.mobilityLevel!!, t))) else null,
        if (needsVehicleCheck(p.mobilityLevel)) t("copyBlock.mobilityVehicleNote") else null,
        line(p.communicationPreference.isSet(), "copyBlock.communication", mapOf("value" to p.communicationPreference)),
        line(p.escalationSigns.isSet(), "copyBlock.escalation", mapOf("value" to p.escalationSigns)),
        line(p.dislikesTriggers.isSet(), "copyBlock.triggers", mapOf("value" to p.dislikesTriggers)),
        line(p.deescalationTechniques.isSet(), "copyBlock.deescalation", mapOf("value" to p.deescalationTechniques)),
        line(p.likes.isSet(), "copyBlock.likes", mapOf("value" to p.likes)),
        line(p.approachGuidance.isSet(), "copyBlock.approach", mapOf("value" to p.approachGuidance)),
        line(p.safeWord.isSet(), "copyBlock.safeWord", mapOf("value" to p.safeWord)),
        t("copyBlock.lastSeen", mapOf("time" to (input.lastSeenTime ?: t("copyBlock.unknown")))),
        // The app doesn't collect location (blocked on purpose); this is a line to fill in.
        t("copyBlock.coordinates", mapOf("coords" to t("copyBlock.unknown"))),
        line(p.locativeDeviceInfo.isSet(), "copyBlock.locator", mapOf("value" to p.locativeDeviceInfo)),
        line(p.idBracelets.isSet(), "copyBlock.idBracelet", mapOf("value" to p.idBracelets)),
        line(p.medicAlertId.isSet(), "copyBlock.medicAlertId", mapOf("value" to p.medicAlertId)),
        line(p.medicAlertHotline.isSet(), "copyBlock.medicAlertHotline", mapOf("value" to p.medicAlertHotline)),
        // Blank line before the reminder
        "",
        t("copyBlock.wearingReminder"),
    ).joinToString("\n")
}

fun buildScript(input: ReadoutInput, t: Translate): String {
    val p = input.profile
    val appearance = describeAppearance(p, t)
    val important = describeImportantDetails(p, t)

    return buildList {
        add(t("script.opening"))
        add(t("script.name", mapOf("name" to p.name)))
        p.dateOfBirth?.takeIf { it.isSet() }?.let { ageOn(it, input.today) }?.let {
            add(t("script.age", mapOf("age" to it)))
        }
        if (input.lastSeenTime.isSet()) add(t("script.lastSeenTime", mapOf("time" to input.lastSeenTime)))
        else add(t("script.lastSeenUnknown"))
        // Location isn't collected (blocked on purpose): the caller fills it in.
        add(t("script.locationUnknown"))
        if (appearance.isNotEmpty()) add(t("script.appearance", mapOf("desc" to appearance)))
        if (important.isNotEmpty()) add(t("script.additionalContext", mapOf("desc" to important)))
        if (p.medicAlertId.isSet()) add(t("script.medicAlertId", mapOf("id" to p.medicAlertId)))
        if (p.photoUri.isSet()) add(t("script.photoAvailable"))
        add(t("script.silverAlert"))
    }.joinToString(" ")
}

/** What's missing for a stronger script, as translated phrases. */
fun missingScriptDetails(input: ReadoutInput, t: Translate): List<String> = buildList {
    if (!input.lastSeenTime.isSet()) add(t("sections.script.missing.lastSeenTime"))
    if (describeAppearance(input.profile, t).isEmpty()) add(t("sections.script.missing.appearanceDetails"))
    if (describeImportantDetails(input.profile, t).isEmpty()) add(t("sections.script.missing.importantDetails"))
}
