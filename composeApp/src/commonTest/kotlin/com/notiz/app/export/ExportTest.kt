package com.notiz.app.export

import com.notiz.app.data.Due
import com.notiz.app.data.PayerName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportTest {

    @Test
    fun amountInIndianWords() {
        assertEquals("Rupees Zero Only", amountInWords(0))
        assertEquals("Rupees Two Thousand Five Hundred and Fifty Paise Only", amountInWords(250_050))
        assertEquals("Rupees One Lakh Only", amountInWords(10_000_000))
        assertEquals(
            "Rupees One Crore Twenty Three Lakh Forty Five Thousand Six Hundred Seventy Eight and Five Paise Only",
            amountInWords(1_234_567_805),
        )
    }

    @Test
    fun receiptNumbersAndFileNames() {
        assertEquals("R-00012", receiptNumberLabel(12))
        assertEquals("Dues-Green_Park___Phase_2-2026-10-29.xlsx", duesFileName("Green Park / Phase 2", "2026-10-29"))
    }

    @Test
    fun spreadsheetColumnNames() {
        assertEquals("A", columnName(0))
        assertEquals("Z", columnName(25))
        assertEquals("AA", columnName(26))
        assertEquals("ZZ", columnName(701))
    }

    @Test
    fun zipCrc32MatchesTheStandard() {
        assertEquals(0x414FA339L, ZipWriter.crc32("The quick brown fox jumps over the lazy dog".encodeToByteArray()))
    }

    @Test
    fun receiptIsAPdf() {
        val pdf = buildReceiptPdf(
            ReceiptData(
                "Green Park", "Chennai", 1, "2026-10-29T06:30:00+00:00", "B-202", "Ravi",
                "Oct maintenance", "2026-11-05", 250_000, "412345678901",
            ),
        ).decodeToString()
        assertTrue(pdf.startsWith("%PDF-1.4"))
        assertTrue(pdf.trimEnd().endsWith("%%EOF"))
        assertTrue("R-00001" in pdf)
    }

    @Test
    fun duesWorkbookIsAZip() {
        val xlsx = buildDuesWorkbook(
            listOf(
                Due(
                    "1", "s", "B-202", "Oct", 250_000, "2026-10-05", "paid", "UTR1",
                    receiptNo = 1, payer = PayerName("Ravi"),
                ),
            ),
        )
        assertEquals('P'.code.toByte(), xlsx[0])
        assertEquals('K'.code.toByte(), xlsx[1])
    }
}
