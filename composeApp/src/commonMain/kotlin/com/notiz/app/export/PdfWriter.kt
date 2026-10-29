package com.notiz.app.export

/**
 * A tiny single-page PDF writer: text in the built-in Helvetica fonts, lines and filled
 * rectangles. Enough for receipts, with no platform code, so Android and iOS produce the
 * same file.
 *
 * Coordinates are in points from the TOP-left (converted to PDF's bottom-left internally).
 * The built-in fonts only cover Latin-1, so other characters (e.g. Tamil, ₹) are replaced;
 * callers should pass English text and "Rs." for amounts.
 */
class PdfPage(val width: Double, val height: Double) {
    private val ops = StringBuilder()

    enum class Font(val resource: String) { REGULAR("F1"), BOLD("F2") }

    fun text(x: Double, y: Double, text: String, size: Double = 11.0, font: Font = Font.REGULAR, color: Rgb = Rgb.BLACK) {
        ops.append("BT /${font.resource} ${fmt(size)} Tf ${color.fill()} ${fmt(x)} ${fmt(height - y)} Td (${escape(text)}) Tj ET\n")
    }

    /** Right-aligned text ending at [right] (uses Helvetica widths for an accurate fit). */
    fun textRight(right: Double, y: Double, text: String, size: Double = 11.0, font: Font = Font.REGULAR, color: Rgb = Rgb.BLACK) {
        text(right - textWidth(text, size, font), y, text, size, font, color)
    }

    fun line(x1: Double, y1: Double, x2: Double, y2: Double, width: Double = 0.75, color: Rgb = Rgb.GREY) {
        ops.append("${color.stroke()} ${fmt(width)} w ${fmt(x1)} ${fmt(height - y1)} m ${fmt(x2)} ${fmt(height - y2)} l S\n")
    }

    fun fillRect(x: Double, y: Double, w: Double, h: Double, color: Rgb) {
        ops.append("${color.fill()} ${fmt(x)} ${fmt(height - y - h)} ${fmt(w)} ${fmt(h)} re f\n")
    }

    /** Wraps [text] to [maxWidth] and draws it; returns the y below the last line. */
    fun paragraph(x: Double, y: Double, maxWidth: Double, text: String, size: Double = 11.0, font: Font = Font.REGULAR, color: Rgb = Rgb.BLACK, leading: Double = size * 1.35): Double {
        var cy = y
        for (line in wrap(text, maxWidth, size, font)) {
            text(x, cy, line, size, font, color)
            cy += leading
        }
        return cy
    }

    internal fun content(): String = ops.toString()

    companion object {
        fun wrap(text: String, maxWidth: Double, size: Double, font: Font): List<String> {
            val lines = mutableListOf<String>()
            var current = ""
            for (word in text.split(" ")) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (current.isNotEmpty() && textWidth(candidate, size, font) > maxWidth) {
                    lines += current
                    current = word
                } else {
                    current = candidate
                }
            }
            if (current.isNotEmpty()) lines += current
            return lines
        }

        fun textWidth(text: String, size: Double, font: Font): Double =
            toLatin1(text).sumOf { c -> charWidth(c, font).toDouble() } * size / 1000.0
    }
}

data class Rgb(val r: Double, val g: Double, val b: Double) {
    internal fun fill() = "${fmt(r)} ${fmt(g)} ${fmt(b)} rg"
    internal fun stroke() = "${fmt(r)} ${fmt(g)} ${fmt(b)} RG"

    companion object {
        val BLACK = Rgb(0.0, 0.0, 0.0)
        val WHITE = Rgb(1.0, 1.0, 1.0)
        val GREY = Rgb(0.75, 0.78, 0.77)
        val MUTED = Rgb(0.33, 0.38, 0.37)
        /** Notiz teal #0F5C57 */
        val BRAND = Rgb(15 / 255.0, 92 / 255.0, 87 / 255.0)
        val BRAND_LIGHT = Rgb(0.90, 0.95, 0.94)
    }
}

/** Builds the PDF file bytes for one page. */
fun buildPdf(page: PdfPage, title: String): ByteArray {
    val content = page.content()
    val objects = listOf(
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${fmt(page.width)} ${fmt(page.height)}] " +
            "/Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>",
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>",
        "<< /Length ${latin1Bytes(content).size} >>\nstream\n$content\nendstream",
        "<< /Title (${escape(title)}) /Producer (Notiz) >>",
    )
    val out = ByteBuffer()
    out.write("%PDF-1.4\n%âãÏÓ\n")
    val offsets = mutableListOf<Int>()
    objects.forEachIndexed { i, body ->
        offsets += out.size
        out.write("${i + 1} 0 obj\n$body\nendobj\n")
    }
    val xrefStart = out.size
    out.write("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
    for (o in offsets) out.write("${o.toString().padStart(10, '0')} 00000 n \n")
    out.write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R /Info ${objects.size} 0 R >>\nstartxref\n$xrefStart\n%%EOF\n")
    return out.toByteArray()
}

// ─────────────────────────── helpers ───────────────────────────

private class ByteBuffer {
    private val bytes = ArrayList<Byte>(4096)
    val size get() = bytes.size
    fun write(s: String) { for (b in latin1Bytes(s)) bytes += b }
    fun toByteArray() = bytes.toByteArray()
}

private fun latin1Bytes(s: String): ByteArray = ByteArray(s.length) { i ->
    val c = s[i].code
    (if (c <= 0xFF) c else '?'.code).toByte()
}

/** Characters Helvetica/WinAnsi can't show become '?'; ₹ becomes "Rs." */
internal fun toLatin1(s: String): String = buildString {
    for (ch in s) {
        when {
            ch == '₹' -> append("Rs. ")
            ch == '–' || ch == '—' -> append('-')
            ch == '‘' || ch == '’' -> append('\'')
            ch == '“' || ch == '”' -> append('"')
            ch == '…' -> append("...")
            ch.code in 0x20..0x7E || ch.code in 0xA0..0xFF -> append(ch)
            else -> append('?')
        }
    }
}

private fun escape(s: String): String =
    toLatin1(s).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

private fun fmt(v: Double): String {
    val r = kotlin.math.round(v * 100) / 100
    return if (r == kotlin.math.floor(r)) r.toLong().toString() else r.toString()
}

/** Helvetica / Helvetica-Bold advance widths (1/1000 em) for ASCII 32..126; others use an average. */
private fun charWidth(c: Char, font: PdfPage.Font): Int {
    val table = if (font == PdfPage.Font.BOLD) HELVETICA_BOLD else HELVETICA
    val i = c.code - 32
    return if (i in table.indices) table[i] else 556
}

private val HELVETICA = intArrayOf(
    278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
    556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
    1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
    667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
    333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
    556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584,
)
private val HELVETICA_BOLD = intArrayOf(
    278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278, 278,
    556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 333, 333, 584, 584, 584, 611,
    975, 722, 722, 722, 722, 667, 611, 778, 722, 278, 556, 722, 611, 833, 722, 778,
    667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 333, 278, 333, 584, 556,
    333, 556, 611, 556, 611, 556, 333, 611, 611, 278, 278, 556, 278, 889, 611, 611,
    611, 611, 389, 556, 333, 611, 556, 778, 556, 556, 500, 389, 280, 389, 584,
)
