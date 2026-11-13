package com.backtosafety.core

/**
 * What home's "Ready for an emergency" card lists: the things that make the 911 script and the
 * search stronger, each filled in or not. [key] is the i18n key under home `readiness.items`.
 */
enum class ReadinessItem(val key: String) {
    /** A photo for the dispatcher and the people searching. */
    PHOTO("photo"),

    /** Something a stranger could recognise them by (height, weight, hair, eyes, marks). */
    DESCRIPTION("description"),

    /** At least one contact to call or alert. */
    CONTACTS("contacts"),

    /** At least one place they might go. */
    PLACES("places"),
}

/** One line of the readiness card. */
data class ReadinessCheck(val item: ReadinessItem, val done: Boolean)

/**
 * The readiness card's lines, in order, from what's stored. A missing profile counts as nothing
 * filled in; the description uses the same fields as the script's appearance line.
 */
fun readinessChecklist(profile: Profile?, contacts: Int, places: Int): List<ReadinessCheck> = listOf(
    ReadinessCheck(ReadinessItem.PHOTO, profile?.photoUri.isSet()),
    ReadinessCheck(
        ReadinessItem.DESCRIPTION,
        profile != null && listOf(profile.height, profile.weight, profile.hairColor, profile.eyeColor, profile.identifyingMarks)
            .any { it.isSet() },
    ),
    ReadinessCheck(ReadinessItem.CONTACTS, contacts > 0),
    ReadinessCheck(ReadinessItem.PLACES, places > 0),
)
