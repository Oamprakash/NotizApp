package com.notiz.app.export

import com.notiz.app.util.formatDate
import com.notiz.app.util.formatRupees
import com.notiz.app.util.formatTimestamp

/** Everything printed on a payment receipt. Dates are ISO ("2026-11-05" or a timestamp). */
data class ReceiptData(
    val societyName: String,
    val city: String,
    val receiptNo: Int,
    val paidOn: String,
    val flatNo: String,
    val payerName: String?,
    val billTitle: String,
    val dueDate: String,
    val amountPaise: Long,
    val utr: String?,
    /** "upi", "cash" or "online" (null for older payments: inferred from [utr]) */
    val paymentMode: String? = null,
)

fun receiptNumberLabel(no: Int): String = "R-" + no.toString().padStart(5, '0')

fun receiptFileName(data: ReceiptData): String = "Receipt-${receiptNumberLabel(data.receiptNo)}-${safeFilePart(data.flatNo)}.pdf"

/** Builds a one-page A5 receipt (always in English: it's an accounting record). */
fun buildReceiptPdf(data: ReceiptData): ByteArray {
    val page = PdfPage(width = 420.0, height = 595.0) // A5 portrait
    val left = 32.0
    val right = page.width - 32.0
    val labelWidth = 118.0

    // Header band with the society's name (names in Tamil script can't be drawn by the PDF fonts)
    val societyName = data.societyName.takeIf { isPrintable(it) } ?: "Housing Society"
    page.fillRect(0.0, 0.0, page.width, 92.0, Rgb.BRAND)
    var y = page.paragraph(left, 40.0, right - left, societyName.uppercase(), size = 17.0, font = PdfPage.Font.BOLD, color = Rgb.WHITE, leading = 21.0)
    if (data.city.isNotBlank() && isPrintable(data.city)) page.text(left, y - 2.0, data.city, size = 10.0, color = Rgb.BRAND_LIGHT)

    // Title + number/date
    y = 128.0
    page.text(left, y, "PAYMENT RECEIPT", size = 15.0, font = PdfPage.Font.BOLD, color = Rgb.BRAND)
    page.textRight(right, y - 6.0, "Receipt No.  ${receiptNumberLabel(data.receiptNo)}", size = 10.0, font = PdfPage.Font.BOLD)
    page.textRight(right, y + 8.0, "Date  ${formatTimestamp(data.paidOn, withTime = false)}", size = 10.0, color = Rgb.MUTED)
    page.line(left, y + 20.0, right, y + 20.0)

    // Details
    y += 50.0
    val received = data.payerName?.takeIf { isPrintable(it) }?.let { "$it (Flat ${data.flatNo})" } ?: "Flat ${data.flatNo}"
    val rows = listOf(
        "Received from" to received,
        "Towards" to "${data.billTitle} (due ${formatDate(data.dueDate)})",
        "Payment mode" to paymentModeLabel(data.paymentMode, data.utr),
    ) + (
        if (data.utr.isNullOrBlank()) {
            emptyList()
        } else {
            listOf((if (data.paymentMode == "online") "Bank reference" else "UPI reference (UTR)") to data.utr)
        }
        )
    for ((label, value) in rows) {
        page.text(left, y, label, size = 10.0, color = Rgb.MUTED)
        y = page.paragraph(left + labelWidth, y, right - left - labelWidth, value, size = 11.0) + 8.0
    }

    // Amount box
    y += 6.0
    page.fillRect(left, y, right - left, 74.0, Rgb.BRAND_LIGHT)
    page.text(left + 14.0, y + 24.0, "Amount received", size = 10.0, color = Rgb.MUTED)
    page.textRight(right - 14.0, y + 26.0, formatRupees(data.amountPaise), size = 20.0, font = PdfPage.Font.BOLD, color = Rgb.BRAND)
    page.paragraph(left + 14.0, y + 52.0, right - left - 28.0, amountInWords(data.amountPaise), size = 9.5, color = Rgb.MUTED, leading = 12.0)

    // Footer
    val footerY = page.height - 56.0
    page.line(left, footerY, right, footerY)
    page.text(left, footerY + 18.0, "This is a computer-generated receipt and does not need a signature.", size = 8.5, color = Rgb.MUTED)
    page.text(left, footerY + 31.0, "Issued for $societyName via Notiz", size = 8.5, color = Rgb.MUTED)

    return buildPdf(page, title = "Receipt ${receiptNumberLabel(data.receiptNo)}")
}

internal fun paymentModeLabel(mode: String?, utr: String?): String = when (mode) {
    "online" -> "Online (Razorpay)"
    "cash" -> "Cash / other"
    "upi" -> "UPI"
    else -> if (utr.isNullOrBlank()) "Cash / other" else "UPI"
}

/** True when every character can be drawn with the built-in PDF fonts (e.g. not Tamil script). */
private fun isPrintable(s: String): Boolean = s.all { it.code in 0x20..0x7E || it.code in 0xA0..0xFF }

internal fun safeFilePart(s: String): String =
    s.map { if (it.isLetterOrDigit() || it == '-') it else '_' }.joinToString("").trim('_').ifEmpty { "x" }
