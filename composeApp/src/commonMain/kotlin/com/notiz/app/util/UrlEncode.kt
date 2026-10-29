package com.notiz.app.util

private const val HEX = "0123456789ABCDEF"

/** Percent-encodes everything except RFC 3986 unreserved characters (UTF-8). */
fun urlEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val c = byte.toInt().toChar()
        val unreserved = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-._~@"
        if (unreserved && byte >= 0) {
            append(c)
        } else {
            append('%')
            append(HEX[(byte.toInt() shr 4) and 0xF])
            append(HEX[byte.toInt() and 0xF])
        }
    }
}
