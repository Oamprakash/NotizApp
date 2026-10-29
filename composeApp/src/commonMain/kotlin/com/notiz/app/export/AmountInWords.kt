package com.notiz.app.export

private val ONES = listOf(
    "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
    "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen",
)
private val TENS = listOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

/** 0..99 → words */
private fun twoDigits(n: Int): String = when {
    n < 20 -> ONES[n]
    else -> TENS[n / 10] + if (n % 10 != 0) " " + ONES[n % 10] else ""
}

/** 0..999 → words */
private fun threeDigits(n: Int): String {
    val hundred = n / 100
    val rest = n % 100
    return listOfNotNull(
        if (hundred > 0) "${ONES[hundred]} Hundred" else null,
        if (rest > 0) twoDigits(rest) else null,
    ).joinToString(" ")
}

/** Whole number in the Indian system: crore, lakh, thousand. */
fun numberInIndianWords(value: Long): String {
    if (value == 0L) return "Zero"
    val parts = mutableListOf<String>()
    var n = value
    val crore = n / 10_000_000; n %= 10_000_000
    val lakh = n / 100_000; n %= 100_000
    val thousand = n / 1_000; n %= 1_000
    if (crore > 0) parts += "${numberInIndianWords(crore)} Crore"
    if (lakh > 0) parts += "${twoDigits(lakh.toInt())} Lakh"
    if (thousand > 0) parts += "${twoDigits(thousand.toInt())} Thousand"
    if (n > 0) parts += threeDigits(n.toInt())
    return parts.joinToString(" ")
}

/** 250050 paise → "Rupees Two Thousand Five Hundred and Fifty Paise Only" (as written on Indian receipts). */
fun amountInWords(paise: Long): String {
    val rupees = paise / 100
    val p = (paise % 100).toInt()
    val rupeePart = "Rupees ${numberInIndianWords(rupees)}"
    return if (p == 0) "$rupeePart Only" else "$rupeePart and ${twoDigits(p)} Paise Only"
}
