package com.backtosafety.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

// Date of birth (stored as MM/DD/YYYY) and whole-year age. Port of utils/age.ts;
// held to spec/vectors/age.json.

private val DOB = Regex("""^(\d{1,2})/(\d{1,2})/(\d{4})$""")

fun parseDob(value: String): LocalDate? {
    val match = DOB.find(value.trim()) ?: return null
    val (month, day, year) = match.destructured
    // Impossible dates (02/30, month 13) are rejected rather than rolled over.
    return runCatching { LocalDate(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
}

/** Whole years between [dob] and [today]; null if the DOB is unparseable or in the future. */
fun ageOn(dob: String, today: LocalDate): Int? {
    val birth = parseDob(dob) ?: return null
    var age = today.year - birth.year
    val hadBirthday = today.month > birth.month ||
        (today.month == birth.month && today.day >= birth.day)
    if (!hadBirthday) age -= 1
    return age.takeIf { it >= 0 }
}

/** The stored MM/DD/YYYY form of a date. */
fun formatDob(date: LocalDate): String =
    "${date.month.number.toString().padStart(2, '0')}/${date.day.toString().padStart(2, '0')}/${date.year}"
