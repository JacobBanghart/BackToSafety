package com.backtosafety.core

// Phone display formatting, as-you-type formatting and SMS recipient normalization.
// Port of utils/phone.ts; held to spec/vectors/phone.json.

private val NON_DIGIT = Regex("""\D""")
private val EXTENSION = Regex("""\s*(?:ext\.?|x|#)\s*(\d+)\s*$""", RegexOption.IGNORE_CASE)

fun formatPhoneNumber(phone: String): String {
    // Keep an extension readable instead of folding it into the number (F-4).
    val ext = EXTENSION.find(phone)
    if (ext != null && ext.range.first > 0) {
        return "${formatPhoneNumber(phone.substring(0, ext.range.first))} ext. ${ext.groupValues[1]}"
    }

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

fun normalizeSmsRecipient(phone: String): String {
    val trimmed = phone.trim()
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
