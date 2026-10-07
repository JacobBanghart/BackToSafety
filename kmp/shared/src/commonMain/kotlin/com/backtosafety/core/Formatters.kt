package com.backtosafety.core

// As-you-type formatters for profile fields. Port of utils/formatters.ts;
// held to spec/vectors/formatters.json.

private val NON_DIGIT = Regex("""\D""")
private val NON_ALNUM = Regex("""[^a-zA-Z0-9]""")
private val HEIGHT_WITHOUT_CLOSING_QUOTE = Regex("""^\d'\d{1,2}$""")

/** Feet and inches from up to three digits: 5 → 5, 56 → 5'6", 511 → 5'11". */
fun formatHeightInput(value: String): String {
    var digits = value.replace(NON_DIGIT, "").take(3)
    // `5'6` (closing quote gone) means backspace just removed the quote; remove the
    // last digit too, or the quote comes straight back (F-11).
    if (HEIGHT_WITHOUT_CLOSING_QUOTE.matches(value)) digits = digits.dropLast(1)
    return when (digits.length) {
        0 -> ""
        1 -> digits
        2 -> "${digits[0]}'${digits[1]}\""
        else -> "${digits[0]}'${digits.substring(1)}\""
    }
}

/** Up to four digits with a thousands separator: 150 → 150, 1500 → 1,500. */
fun formatWeightInput(value: String): String {
    val digits = value.replace(NON_DIGIT, "").take(4)
    if (digits.isEmpty()) return ""
    return if (digits.length > 3) "${digits.dropLast(3)},${digits.takeLast(3)}" else digits
}

/** Up to 16 letters/digits, upper-cased, in dash-separated groups of four. */
fun formatMedicAlertIdInput(value: String): String =
    value.replace(NON_ALNUM, "").uppercase().take(16).chunked(4).joinToString("-")

/** MM/DD/YYYY from up to eight typed digits. */
fun formatDobInput(value: String): String {
    val digits = value.replace(NON_DIGIT, "").take(8)
    return when {
        digits.length <= 2 -> digits
        digits.length <= 4 -> "${digits.take(2)}/${digits.substring(2)}"
        else -> "${digits.take(2)}/${digits.substring(2, 4)}/${digits.substring(4)}"
    }
}
