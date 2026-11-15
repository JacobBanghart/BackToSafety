package com.backtosafety.app.core

import com.backtosafety.app.core.startedAtMs
import com.backtosafety.core.ffi.ActiveEmergency
import com.backtosafety.core.ffi.ChecklistStep
import com.backtosafety.core.ffi.CountdownAlert
import com.backtosafety.core.ffi.Date
import com.backtosafety.core.ffi.Profile
import com.backtosafety.core.ffi.ProfileField
import com.backtosafety.core.ffi.ProfileKeyboard
import com.backtosafety.core.ffi.ScriptPart
import com.backtosafety.core.ffi.VehicleCheckKind
import com.backtosafety.core.ffi.activeEmergencyStartedAtMs
import com.backtosafety.core.ffi.countdownAlertKey
import com.backtosafety.core.ffi.databaseVersion
import com.backtosafety.core.ffi.emergencyStepCount
import com.backtosafety.core.ffi.mobilityOptions
import com.backtosafety.core.ffi.profileTextFields
import com.backtosafety.core.ffi.searchWindowSeconds
import com.backtosafety.core.ffi.vehicleCheckKindKey
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import com.backtosafety.core.ffi.ReadoutInput as CoreReadoutInput
import com.backtosafety.core.ffi.buildAlertSms as coreBuildAlertSms
import com.backtosafety.core.ffi.buildCopyBlock as coreBuildCopyBlock
import com.backtosafety.core.ffi.buildInitialSteps as coreBuildInitialSteps
import com.backtosafety.core.ffi.buildScript as coreBuildScript
import com.backtosafety.core.ffi.buildScriptParts as coreBuildScriptParts
import com.backtosafety.core.ffi.describeMobility as coreDescribeMobility
import com.backtosafety.core.ffi.directionHint as coreDirectionHint
import com.backtosafety.core.ffi.formatDob as coreFormatDob
import com.backtosafety.core.ffi.missingScriptDetails as coreMissingScriptDetails
import com.backtosafety.core.ffi.parseDob as coreParseDob

// The core's functions that take a translator, over the app's [Translate]. The rest of the
// core's functions (formatters, phone, age, alerts) are imported from com.backtosafety.core.ffi.

/** The search window, in seconds (15 minutes). */
val SEARCH_WINDOW_SECONDS: Int = searchWindowSeconds()

/** Mobility option labels as stored (English), each with its translation key in the profile namespace. */
val MOBILITY_OPTION_KEYS: Map<String, String> = mobilityOptions().associate { it.label to it.key }

val MOBILITY_OPTIONS: List<String> = MOBILITY_OPTION_KEYS.keys.toList()

/** The emergency checklist's step count (Home's "n of 11 steps"). */
val EMERGENCY_STEP_COUNT: Int = emergencyStepCount()

/** The database schema version (Settings shows it in developer mode). */
val DATABASE_VERSION: Int = databaseVersion()

/** The profile fields edited as text, in the form's order. */
val PROFILE_TEXT_FIELDS: List<ProfileField> = profileTextFields()

// ProfileField's attributes, spelled as the KMP properties the screens read.
val ProfileField.key: String get() = key()
val ProfileField.hasHint: Boolean get() = hasHint()
val ProfileField.multiline: Boolean get() = multiline()
val ProfileField.keyboard: ProfileKeyboard get() = keyboard()
val ProfileField.capitalizeWords: Boolean get() = capitalizeWords()

/** The emergency's start in epoch ms, parsed once from its ISO time; null if it doesn't parse. */
val ActiveEmergency.startedAtMs: Long? get() = activeEmergencyStartedAtMs(this)

fun describeMobility(stored: String, t: Translate): String = coreDescribeMobility(stored, t.core)

fun buildInitialSteps(t: Translate, emergencyNumber: String): List<ChecklistStep> = coreBuildInitialSteps(t.core, emergencyNumber)

fun buildAlertSms(t: Translate, name: String?, startedTime: String, wearing: String): String = coreBuildAlertSms(t.core, name, startedTime, wearing)

fun directionHint(t: Translate, dominantHand: String?): String? = coreDirectionHint(t.core, dominantHand)

/**
 * What the info sheet shows and copies is built from this. [lastSeenTime] (when the search
 * started) arrives already formatted; [lastSeenClock] is its clock time as the locale says it,
 * for the spoken script, and [lastSeenDate] its day, so the script can say "today".
 */
class ReadoutInput(
    val profile: Profile,
    val lastSeenTime: String? = null,
    val today: LocalDate,
    val lastSeenClock: String? = null,
    val lastSeenDate: LocalDate? = null,
) {
    internal fun toCore() = CoreReadoutInput(profile, lastSeenTime, today.toCore(), lastSeenClock, lastSeenDate?.toCore())
}

internal fun LocalDate.toCore() = Date(year, month.number, day)

internal fun Date.toLocalDate() = LocalDate(year, month, day)

fun buildCopyBlock(input: ReadoutInput, t: Translate): String = coreBuildCopyBlock(input.toCore(), t.core)

fun buildScript(input: ReadoutInput, t: Translate): String = coreBuildScript(input.toCore(), t.core)

/** The 911 script in parts, the blanks the caller still has to fill marked. */
fun buildScriptParts(input: ReadoutInput, t: Translate): List<ScriptPart> = coreBuildScriptParts(input.toCore(), t.core)

fun missingScriptDetails(input: ReadoutInput, t: Translate): List<String> = coreMissingScriptDetails(input.toCore(), t.core)

/** The stored key of an alert (a notification extra, an analytics property). */
val CountdownAlert.key: String get() = countdownAlertKey(this)

val VehicleCheckKind.key: String get() = vehicleCheckKindKey(this)

/** A date of birth stored as MM/DD/YYYY; null if it isn't a real date. */
fun parseDob(value: String): LocalDate? = coreParseDob(value)?.toLocalDate()

/** The stored MM/DD/YYYY form of a date. */
fun formatDob(date: LocalDate): String = coreFormatDob(date.toCore())

/** The name analytics reports an error under (save_failed's `error`): never its message. */
fun Throwable.analyticsTypeName(): String? = when (this) {
    is com.backtosafety.core.ffi.StoreFailure.Sqlite -> "SqliteError"
    is com.backtosafety.core.ffi.StoreFailure.Io -> "IoError"
    is com.backtosafety.core.ffi.StoreFailure.UnrecognizedDatabase -> "UnrecognizedDatabase"
    is com.backtosafety.core.ffi.StoreFailure.Closed -> "Closed"
    is com.backtosafety.core.ffi.StoreFailure.NewerDatabase -> "NewerDatabase"
    else -> this::class.simpleName
}
