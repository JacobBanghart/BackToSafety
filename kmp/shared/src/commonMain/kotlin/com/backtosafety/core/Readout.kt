package com.backtosafety.core

import kotlinx.datetime.LocalDate

// What the info sheet shows and copies: the 911 call script and the full-details block.
// Port of utils/readout.ts; held to spec/vectors/readout.json. `t` is bound to the
// `readout` namespace; times arrive already formatted by the platform.

class ReadoutInput(
    val profile: Profile,
    /** When the search started (the emergency's start), formatted for display. Named for the old "last seen" label. */
    val lastSeenTime: String? = null,
    /** For the age line; screens pass the current date. */
    val today: LocalDate,
    /** The start's clock time as the locale says it, with no seconds ("9:00 AM"), for the spoken script. */
    val lastSeenClock: String? = null,
    /** The day the search started, in the device's time zone, so the script can say "today". */
    val lastSeenDate: LocalDate? = null,
)

/**
 * A piece of the 911 script: words to read out, or a blank the caller fills in aloud (the
 * location, which the app doesn't collect, or an unknown time). The screens show a blank as a
 * highlighted gap to fill, not as bracketed text to read out to the dispatcher.
 */
class ScriptPart(val text: String, val blank: Boolean)

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

/**
 * When the search started, as it is said on the phone: "9:00 AM today" or "9:00 AM yesterday"
 * (no date, no seconds), else the full date and time.
 */
private fun spokenStart(input: ReadoutInput, t: Translate): String? {
    val clock = input.lastSeenClock?.takeIf { it.isSet() }
    val date = input.lastSeenDate
    return when {
        clock != null && date == input.today -> t("script.timeToday", mapOf("time" to clock))
        clock != null && date != null && date.toEpochDays() == input.today.toEpochDays() - 1 ->
            t("script.timeYesterday", mapOf("time" to clock))
        else -> input.lastSeenTime?.takeIf { it.isSet() }
    }
}

/** Placeholder for a blank while a sentence is translated: the sentence is split around it. */
private const val BLANK = "\u0000"

/** The 911 script in pieces, its blanks marked (see [ScriptPart]); [buildScript] is the same text. */
fun buildScriptParts(input: ReadoutInput, t: Translate): List<ScriptPart> {
    val p = input.profile
    val appearance = describeAppearance(p, t)
    val important = describeImportantDetails(p, t)
    val sentences = mutableListOf<List<ScriptPart>>()
    fun say(text: String) = sentences.add(listOf(ScriptPart(text, blank = false)))

    // A sentence with one blank in it: "Last known location: ____."
    fun sayWithBlank(key: String, blankKey: String) {
        val (before, after) = t(key, mapOf("blank" to BLANK)).split(BLANK, limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        sentences.add(
            listOfNotNull(
                before.takeIf { it.isNotEmpty() }?.let { ScriptPart(it, false) },
                ScriptPart(t(blankKey), blank = true),
                after.takeIf { it.isNotEmpty() }?.let { ScriptPart(it, false) },
            ),
        )
    }

    say(t("script.opening"))
    say(t("script.name", mapOf("name" to p.name)))
    p.dateOfBirth?.takeIf { it.isSet() }?.let { ageOn(it, input.today) }?.let {
        say(t("script.age", mapOf("age" to it)))
    }
    val started = spokenStart(input, t)
    if (started != null) say(t("script.lastSeenTime", mapOf("time" to started)))
    else sayWithBlank("script.lastSeenUnknown", "script.blankTime")
    // Location isn't collected (blocked on purpose): the caller fills it in.
    sayWithBlank("script.locationUnknown", "script.blankLocation")
    if (appearance.isNotEmpty()) say(t("script.appearance", mapOf("desc" to appearance)))
    if (important.isNotEmpty()) say(t("script.additionalContext", mapOf("desc" to important)))
    if (p.medicAlertId.isSet()) say(t("script.medicAlertId", mapOf("id" to p.medicAlertId)))
    if (p.photoUri.isSet()) say(t("script.photoAvailable"))
    say(t("script.silverAlert"))

    // Sentences are separated by one space; runs of plain words are joined into one part, so
    // the pieces alternate between words and blanks.
    val pieces = sentences.flatMapIndexed { i, parts -> if (i == 0) parts else listOf(ScriptPart(" ", false)) + parts }
    return pieces.fold(mutableListOf<ScriptPart>()) { out, part ->
        val last = out.lastOrNull()
        if (last != null && !last.blank && !part.blank) out[out.lastIndex] = ScriptPart(last.text + part.text, false) else out.add(part)
        out
    }
}

/**
 * The 911 script as one text, for copying: each blank in square brackets
 * ("Last known location: [fill in location].") so a pasted copy still shows the gap.
 */
fun buildScript(input: ReadoutInput, t: Translate): String =
    buildScriptParts(input, t).joinToString("") { if (it.blank) "[${it.text}]" else it.text }

/** What's missing for a stronger script, as translated phrases. */
fun missingScriptDetails(input: ReadoutInput, t: Translate): List<String> = buildList {
    if (!input.lastSeenTime.isSet()) add(t("sections.script.missing.lastSeenTime"))
    if (describeAppearance(input.profile, t).isEmpty()) add(t("sections.script.missing.appearanceDetails"))
    if (describeImportantDetails(input.profile, t).isEmpty()) add(t("sections.script.missing.importantDetails"))
}
