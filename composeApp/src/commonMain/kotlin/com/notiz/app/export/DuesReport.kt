package com.notiz.app.export

import com.notiz.app.data.Due
import com.notiz.app.data.DueStatus
import com.notiz.app.util.formatDate
import com.notiz.app.util.formatTimestamp

/**
 * Excel workbook for the treasurer / auditor (in English, like the receipts):
 *  - "Summary": one row per bill — flats billed, paid, collected, outstanding — plus a total
 *  - "Dues": every flat's dues with status, UTR, paid date and receipt number
 */
fun buildDuesWorkbook(dues: List<Due>): ByteArray {
    val bills = dues.groupBy { it.title to it.dueDate }
        .toList()
        .sortedByDescending { (key, _) -> key.second }

    val summary = XlsxSheet("Summary").apply {
        header("Bill", "Due date", "Flats billed", "Flats paid", "Billed (Rs.)", "Collected (Rs.)", "Outstanding (Rs.)")
        columnWidths(28.0, 14.0, 13.0, 12.0, 15.0, 16.0, 18.0)
        var billed = 0L
        var collected = 0L
        for ((key, items) in bills) {
            val paid = items.filter { it.status == DueStatus.PAID }
            val b = items.sumOf { it.amountPaise }
            val c = paid.sumOf { it.amountPaise }
            billed += b
            collected += c
            row(
                cellText(key.first), cellText(formatDate(key.second)),
                cellNumber(items.size), cellNumber(paid.size),
                cellAmount(b), cellAmount(c), cellAmount(b - c),
            )
        }
        boldRow(
            cellText("Total"), XlsxSheet.Cell.Empty, cellNumber(dues.size),
            cellNumber(dues.count { it.status == DueStatus.PAID }),
            cellAmount(billed), cellAmount(collected), cellAmount(billed - collected),
        )
    }

    val details = XlsxSheet("Dues").apply {
        header("Bill", "Due date", "Flat", "Amount (Rs.)", "Status", "Paid by", "UPI ref (UTR)", "Paid on", "Receipt no.")
        columnWidths(26.0, 13.0, 10.0, 14.0, 20.0, 20.0, 18.0, 13.0, 12.0)
        val ordered = dues.sortedWith(compareByDescending<Due> { it.dueDate }.thenBy { it.title }.thenBy { it.flatNo })
        for (d in ordered) {
            row(
                cellText(d.title),
                cellText(formatDate(d.dueDate)),
                cellText(d.flatNo),
                cellAmount(d.amountPaise),
                cellText(statusLabel(d)),
                cellText(d.payer?.fullName),
                cellText(d.utr),
                cellText(d.paidAt?.let { formatTimestamp(it, withTime = false) }),
                cellText(d.receiptNo?.let { receiptNumberLabel(it) }),
            )
        }
    }

    return buildXlsx(listOf(summary, details))
}

private fun statusLabel(d: Due) = when (d.status) {
    DueStatus.PAID -> if (d.utr.isNullOrBlank()) "Paid (cash/other)" else "Paid (UPI)"
    DueStatus.SUBMITTED -> "Reported, to verify"
    else -> "Unpaid"
}

fun duesFileName(societyName: String, todayIso: String): String =
    "Dues-${safeFilePart(societyName).take(40)}-$todayIso.xlsx"
