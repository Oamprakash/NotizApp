package com.notiz.app.util

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** English short month names; the UI passes localised ones (see ui/i18n/LocalizedFormat.kt). */
val ENGLISH_MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** 250000 paise → "₹2,500"; 250050 → "₹2,500.50" (Indian digit grouping). */
fun formatRupees(paise: Long): String {
    val rupees = paise / 100
    val rest = paise % 100
    val digits = rupees.toString()
    val grouped = if (digits.length <= 3) {
        digits
    } else {
        val last3 = digits.takeLast(3)
        val head = digits.dropLast(3)
        head.reversed().chunked(2).joinToString(",").reversed() + "," + last3
    }
    return if (rest == 0L) "₹$grouped" else "₹$grouped.${rest.toString().padStart(2, '0')}"
}

/** "2500" or "2,500.5" typed by the user → paise, or null if invalid. */
fun parseRupeesToPaise(input: String): Long? {
    val clean = input.replace(",", "").replace("₹", "").trim()
    if (clean.isEmpty()) return null
    val parts = clean.split(".")
    if (parts.size > 2) return null
    val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
    val fraction = parts.getOrNull(1)?.let {
        if (it.length > 2 || it.any { c -> !c.isDigit() }) return null
        it.padEnd(2, '0').toLong()
    } ?: 0L
    val paise = whole * 100 + fraction
    return if (paise > 0) paise else null
}

/** "2026-11-05" → "5 Nov 2026". Returns the input unchanged if it isn't a date. */
fun formatDate(isoDate: String, months: List<String> = ENGLISH_MONTHS): String {
    val parts = isoDate.take(10).split("-")
    if (parts.size != 3) return isoDate
    val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return isoDate
    val day = parts[2].toIntOrNull() ?: return isoDate
    return "$day ${months.getOrElse(month - 1) { ENGLISH_MONTHS[month - 1] }} ${parts[0]}"
}

/** Postgres timestamptz → local "5 Nov 2026, 4:30 PM" (or just the date). */
@OptIn(ExperimentalTime::class)
fun formatTimestamp(iso: String, withTime: Boolean = true, months: List<String> = ENGLISH_MONTHS): String = runCatching {
    val local = Instant.parse(iso).toLocalDateTime(TimeZone.currentSystemDefault())
    val date = formatDate(local.date.toString(), months)
    if (!withTime) return@runCatching date
    "$date, ${formatClock(local.time.toString())}"
}.getOrElse { formatDate(iso, months) }

/** Postgres timestamptz → local "4:30 PM". */
@OptIn(ExperimentalTime::class)
fun formatTime(iso: String): String = runCatching {
    val local = Instant.parse(iso).toLocalDateTime(TimeZone.currentSystemDefault())
    formatClock(local.time.toString())
}.getOrElse { "" }

/** "16:30:05.123" → "4:30 PM" */
private fun formatClock(hhmm: String): String {
    val h = hhmm.take(2).toIntOrNull() ?: return hhmm
    val m = hhmm.drop(3).take(2)
    val suffix = if (h < 12) "AM" else "PM"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "$h12:$m $suffix"
}

/** Basic check for "yyyy-MM-dd". */
fun isValidIsoDate(value: String): Boolean {
    val match = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").matchEntire(value.trim()) ?: return false
    val month = match.groupValues[2].toInt()
    val day = match.groupValues[3].toInt()
    return month in 1..12 && day in 1..31
}
