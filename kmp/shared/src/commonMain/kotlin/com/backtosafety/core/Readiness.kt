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

    /** At least one contact in the Alert Circle (texted when Alert Circle is tapped in a search). */
    CONTACTS("contacts"),

    /** At least one place they might go. */
    PLACES("places"),
}

/**
 * One line of the readiness card. [needsAlertCircle]: contacts are saved but none is in the Alert
 * Circle, so a search's Alert Circle would text no one; the line says so rather than "Add".
 */
data class ReadinessCheck(val item: ReadinessItem, val done: Boolean, val needsAlertCircle: Boolean = false) {
    /** The line's label: home `readiness.items.<labelKey>`. */
    val labelKey: String get() = if (needsAlertCircle) "contactsNoAlertCircle" else item.key

    /** What the line's action says: home `readiness.<actionKey>`. */
    val actionKey: String get() = when {
        done -> "done"
        needsAlertCircle -> "turnOn"
        else -> "add"
    }
}

/**
 * The readiness card's lines, in order, from what's stored. A missing profile counts as nothing
 * filled in; the description uses the same fields as the script's appearance line. Contacts are
 * ready only when at least one of them ([inAlertCircle]) gets the emergency texts.
 */
fun readinessChecklist(profile: Profile?, contacts: Int, inAlertCircle: Int, places: Int): List<ReadinessCheck> = listOf(
    ReadinessCheck(ReadinessItem.PHOTO, profile?.photoUri.isSet()),
    ReadinessCheck(
        ReadinessItem.DESCRIPTION,
        profile != null && listOf(profile.height, profile.weight, profile.hairColor, profile.eyeColor, profile.identifyingMarks)
            .any { it.isSet() },
    ),
    ReadinessCheck(ReadinessItem.CONTACTS, inAlertCircle > 0, needsAlertCircle = contacts > 0 && inAlertCircle == 0),
    ReadinessCheck(ReadinessItem.PLACES, places > 0),
)
