package com.notiz.app.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UtilTest {

    @Test
    fun rupeesUseIndianGrouping() {
        assertEquals("₹500", formatRupees(50_000))
        assertEquals("₹1,000", formatRupees(100_000))
        assertEquals("₹1,23,456", formatRupees(12_345_600))
        assertEquals("₹1,23,45,678.50", formatRupees(1_234_567_850))
    }

    @Test
    fun parsesRupeeInputToPaise() {
        assertEquals(250_000L, parseRupeesToPaise("2,500"))
        assertEquals(250_050L, parseRupeesToPaise("2500.5"))
        assertNull(parseRupeesToPaise("12.345"))
        assertNull(parseRupeesToPaise("0"))
        assertNull(parseRupeesToPaise("abc"))
    }

    @Test
    fun formatsDates() {
        assertEquals("5 Nov 2026", formatDate("2026-11-05"))
        assertEquals("31 Jan 2026", formatDate("2026-01-31T10:00:00+00:00"))
        assertTrue(isValidIsoDate("2026-11-05"))
        assertFalse(isValidIsoDate("05-11-2026"))
    }

    @Test
    fun buildsUpiLink() {
        assertTrue(Upi.isValidVpa("greenpark@okaxis"))
        assertFalse(Upi.isValidVpa("greenpark"))
        assertEquals(
            "upi://pay?pa=green.park@okaxis&pn=Green%20Park%20RWA&am=2500.50&cu=INR" +
                "&tn=Oct%20maintenance%20B-202&tr=abc123",
            Upi.payUri("green.park@okaxis", "Green Park RWA", 250_050, "Oct maintenance B-202", "abc123"),
        )
    }

    @Test
    fun encodesUnicodeAndReservedCharacters() {
        assertEquals("Oct%20maint%20B-202%20%26%20%E2%82%B9", urlEncode("Oct maint B-202 & ₹"))
    }
}
