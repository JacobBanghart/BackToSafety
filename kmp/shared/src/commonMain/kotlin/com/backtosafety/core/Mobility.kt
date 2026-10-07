package com.backtosafety.core

/**
 * Mobility is stored as comma-separated English option labels plus any "Other" free
 * text (spec/storage.md). Labels stay English in storage; [describeMobility] translates
 * them for display. Port of utils/mobility.ts.
 */
val MOBILITY_OPTION_KEYS: Map<String, String> = linkedMapOf(
    "Walks independently" to "mobilityOptions.walksIndependently",
    "Uses cane" to "mobilityOptions.usesCane",
    "Uses walker" to "mobilityOptions.usesWalker",
    "Manual wheelchair" to "mobilityOptions.manualWheelchair",
    "Motorized wheelchair" to "mobilityOptions.motorizedWheelchair",
    "Mobility scooter" to "mobilityOptions.mobilityScooter",
    "Bicycle" to "mobilityOptions.bicycle",
    "Has vehicle" to "mobilityOptions.hasVehicle",
    "Other" to "mobilityOptions.other",
)

val MOBILITY_OPTIONS: List<String> = MOBILITY_OPTION_KEYS.keys.toList()

/** The stored mobility value with known labels translated; custom text passes through. */
fun describeMobility(stored: String, t: Translate): String =
    stored.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(", ") { token ->
            MOBILITY_OPTION_KEYS[token]?.let { t(it, mapOf("ns" to "profile")) } ?: token
        }
