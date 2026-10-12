package com.backtosafety.core

// Phone display formatting, as-you-type formatting, dialing and SMS recipient normalization.
// Port of utils/phone.ts (dialString is new); held to spec/vectors/phone.json.

private val EXTENSION = Regex("""\s*(?:extension|ext\.?|x|#)\s*(\d+)\s*$""", RegexOption.IGNORE_CASE)

/** [phone] split into the number and its trailing extension ("ext. 9", "x9", "#9"), if it has one. */
private fun splitExtension(phone: String): Pair<String, String?> {
    val ext = EXTENSION.find(phone)
    if (ext == null || ext.range.first == 0) return phone to null
    return phone.substring(0, ext.range.first) to ext.groupValues[1]
}

fun formatPhoneNumber(phone: String): String {
    // Keep an extension readable instead of folding it into the number (F-4).
    val (number, extension) = splitExtension(phone)
    if (extension != null) return "${formatPhoneNumber(number)} ext. $extension"

    val digits = phone.replace(NON_DIGIT, "")

    // Non-US international numbers have their own grouping; show them as entered (F-1).
    if (phone.trimStart().startsWith("+") && !digits.startsWith("1")) return phone.trim()

    if (digits.length == 10) {
        return "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
    }
    if (digits.length == 11 && digits.startsWith("1")) {
        return "+1 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7)}"
    }
    if (digits.length > 6) {
        return "${digits.substring(0, 3)}-${digits.substring(3, 6)}-${digits.substring(6)}"
    }
    return phone
}

fun formatPhoneInput(value: String): String {
    val hasCountryCode = value.trimStart().startsWith("+")
    val digits = value.replace(NON_DIGIT, "")

    // A + prefix (e.g. an imported contact) keeps +1 visible.
    if (hasCountryCode && digits.startsWith("1")) {
        val local = digits.substring(1)
        return when {
            local.length > 10 -> "+$digits"
            local.isEmpty() -> "+1 "
            local.length <= 3 -> "+1 $local"
            local.length <= 6 -> "+1 (${local.substring(0, 3)}) ${local.substring(3)}"
            else -> "+1 (${local.substring(0, 3)}) ${local.substring(3, 6)}-${local.substring(6, minOf(10, local.length))}"
        }
    }

    // Other country codes: keep every digit, unformatted (F-1).
    if (hasCountryCode) return "+$digits"

    // Standard 10-digit US format (a leading 1 without + is dropped).
    val local = if (digits.length == 11 && digits.startsWith("1")) digits.substring(1) else digits

    // Too long to be a US number: never drop digits (F-2).
    if (local.length > 10) return digits

    return when {
        local.length <= 3 -> local
        local.length <= 6 -> "(${local.substring(0, 3)}) ${local.substring(3)}"
        else -> "(${local.substring(0, 3)}) ${local.substring(3, 6)}-${local.substring(6, minOf(10, local.length))}"
    }
}

fun stripPhoneFormatting(phone: String): String = phone.replace(NON_DIGIT, "")

/**
 * What to hand the system dialer for a stored [phone]: its digits, a leading `+`, and `*` / `#`
 * only where the number starts with one (a service code such as `*67`; anywhere else they're
 * noise). An extension ("ext. 12", "extension 12", "x12", "#12") becomes a `,` pause then its
 * digits, so `555-123-4567 #12` dials `5551234567,12`. Both the Android dialer and iOS's honor
 * `,` (a two-second pause) in a `tel:` number and tone out the digits after it once the call
 * connects; `;` (wait for the user to confirm) isn't uniformly supported, and a raw `#` would
 * start the URI's fragment. Callers still have to percent-encode the result into the `tel:` URI
 * (`Uri.fromParts` on Android, URLComponents on iOS), since it can hold `#`, `*` and `+`.
 * Returns "" for a number with no digits.
 */
fun dialString(phone: String): String {
    val (number, extension) = splitExtension(phone)
    val trimmed = number.trim()
    val serviceCode = trimmed.startsWith("*") || trimmed.startsWith("#")
    val dialable = trimmed.filterIndexed { i, c ->
        c in '0'..'9' || (c == '+' && i == 0) || (serviceCode && (c == '*' || c == '#'))
    }
    if (dialable.none { it in '0'..'9' }) return ""
    return if (extension != null) "$dialable,$extension" else dialable
}

/**
 * Whether [phone] can be saved as a contact's number: its number (an extension doesn't count)
 * has at least three digits. A "+", a few punctuation marks or a lone digit would be saved and
 * then dropped from the emergency text, and nothing could dial it. Three is the shortest real
 * number, an emergency or service code such as 911 or 112, so the rule stays out of the way of
 * numbers from anywhere; the as-you-type format already does the rest of the shaping.
 */
fun isUsablePhone(phone: String): Boolean = normalizeSmsRecipient(phone).count { it in '0'..'9' } >= 3

fun normalizeSmsRecipient(phone: String): String {
    // A text goes to the number, not the extension (which would otherwise fold into it).
    val trimmed = splitExtension(phone).first.trim()
    if (trimmed.isEmpty()) return ""
    val digits = trimmed.replace(NON_DIGIT, "")
    if (digits.isEmpty()) return ""
    return if (trimmed.startsWith("+")) "+$digits" else digits
}

fun normalizeUniqueSmsRecipients(phones: List<String>): List<String> {
    val byKey = LinkedHashMap<String, String>()
    for (phone in phones.map(::normalizeSmsRecipient).filter { it.isNotEmpty() }) {
        // The same US number with and without its +1 country code is one recipient (F-3).
        val digits = phone.removePrefix("+")
        val key = if (digits.length == 11 && digits.startsWith("1")) digits.substring(1) else digits
        byKey.getOrPut(key) { phone }
    }
    return byKey.values.toList()
}
