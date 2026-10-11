package com.backtosafety.app.core

import com.backtosafety.core.ffi.ChecklistStep
import com.backtosafety.core.ffi.CountdownAlert
import com.backtosafety.core.ffi.Date
import com.backtosafety.core.ffi.Profile
import com.backtosafety.core.ffi.VehicleCheckKind
import com.backtosafety.core.ffi.countdownAlertKey
import com.backtosafety.core.ffi.mobilityOptions
import com.backtosafety.core.ffi.searchWindowSeconds
import com.backtosafety.core.ffi.vehicleCheckKindKey
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import com.backtosafety.core.ffi.ReadoutInput as CoreReadoutInput
import com.backtosafety.core.ffi.buildAlertSms as coreBuildAlertSms
import com.backtosafety.core.ffi.buildCopyBlock as coreBuildCopyBlock
import com.backtosafety.core.ffi.buildInitialSteps as coreBuildInitialSteps
import com.backtosafety.core.ffi.buildScript as coreBuildScript
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

fun describeMobility(stored: String, t: Translate): String = coreDescribeMobility(stored, t.core)

fun buildInitialSteps(t: Translate, emergencyNumber: String): List<ChecklistStep> = coreBuildInitialSteps(t.core, emergencyNumber)

fun buildAlertSms(t: Translate, name: String?, startedTime: String, wearing: String): String = coreBuildAlertSms(t.core, name, startedTime, wearing)

fun directionHint(t: Translate, dominantHand: String?): String? = coreDirectionHint(t.core, dominantHand)

/** What the info sheet shows and copies is built from this; [lastSeenTime] arrives already formatted. */
class ReadoutInput(val profile: Profile, val lastSeenTime: String? = null, val today: LocalDate) {
    internal fun toCore() = CoreReadoutInput(profile, lastSeenTime, Date(today.year, today.month.number, today.day))
}

fun buildCopyBlock(input: ReadoutInput, t: Translate): String = coreBuildCopyBlock(input.toCore(), t.core)

fun buildScript(input: ReadoutInput, t: Translate): String = coreBuildScript(input.toCore(), t.core)

fun missingScriptDetails(input: ReadoutInput, t: Translate): List<String> = coreMissingScriptDetails(input.toCore(), t.core)

/** The stored key of an alert (a notification extra, an analytics property). */
val CountdownAlert.key: String get() = countdownAlertKey(this)

val VehicleCheckKind.key: String get() = vehicleCheckKindKey(this)

/** A date of birth stored as MM/DD/YYYY; null if it isn't a real date. */
fun parseDob(value: String): LocalDate? = coreParseDob(value)?.let { LocalDate(it.year, it.month, it.day) }

/** The stored MM/DD/YYYY form of a date. */
fun formatDob(date: LocalDate): String = coreFormatDob(Date(date.year, date.month.number, date.day))

/** The name analytics reports an error under (save_failed's `error`): never its message. */
fun Throwable.analyticsTypeName(): String? = when (this) {
    is com.backtosafety.core.ffi.StoreFailure.Sqlite -> "SqliteError"
    is com.backtosafety.core.ffi.StoreFailure.Io -> "IoError"
    is com.backtosafety.core.ffi.StoreFailure.UnknownProfileField -> "UnknownProfileField"
    is com.backtosafety.core.ffi.StoreFailure.NewerDatabase -> "NewerDatabase"
    else -> this::class.simpleName
}
