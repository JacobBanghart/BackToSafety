package com.backtosafety.core

/**
 * The file name at the end of a stored photo URI, or null if there isn't one.
 * Readers resolve the photo by this name in the current documents directory, since
 * iOS moves the app container on updates (spec/storage.md, F-21).
 */
fun photoFileName(uri: String): String? {
    val path = uri.split('?', '#').first()
    val name = path.substring(path.lastIndexOf('/') + 1)
    return name.takeIf { it.isNotEmpty() }?.let(::percentDecode)
}

/** decodeURIComponent: %XX escapes are UTF-8 bytes; anything malformed is left as is. */
private fun percentDecode(value: String): String {
    if ('%' !in value) return value
    val bytes = ArrayList<Byte>()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '%' && i + 2 < value.length) {
            val byte = value.substring(i + 1, i + 3).toIntOrNull(16)
            if (byte != null) {
                bytes += byte.toByte()
                i += 3
                continue
            }
        }
        bytes += c.toString().encodeToByteArray().toList()
        i++
    }
    return bytes.toByteArray().decodeToString()
}
