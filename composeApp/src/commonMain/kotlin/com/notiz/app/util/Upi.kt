package com.notiz.app.util

/**
 * Builds a UPI deep link (NPCI "upi://pay" spec). Opening it shows the user's UPI apps
 * (GPay, PhonePe, Paytm, BHIM…) with payee and amount pre-filled.
 *
 * Note: many UPI apps decline intent payments with an amount to *personal* VPAs.
 * Use the society's current-account / merchant VPA. Phase 2 replaces this with a
 * payment gateway (Razorpay / Cashfree) and automatic confirmation via webhook.
 */
object Upi {

    fun payUri(
        vpa: String,
        payeeName: String,
        amountPaise: Long,
        note: String,
        transactionRef: String,
    ): String {
        val amount = "${amountPaise / 100}.${(amountPaise % 100).toString().padStart(2, '0')}"
        return buildString {
            append("upi://pay")
            append("?pa=").append(urlEncode(vpa.trim()))
            append("&pn=").append(urlEncode(payeeName.trim().ifEmpty { "Society" }))
            append("&am=").append(amount)
            append("&cu=INR")
            append("&tn=").append(urlEncode(note.take(50)))
            append("&tr=").append(urlEncode(transactionRef.take(35)))
        }
    }

    /** True if [vpa] looks like name@bank. */
    fun isValidVpa(vpa: String): Boolean = Regex("""^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}$""").matches(vpa.trim())
}
