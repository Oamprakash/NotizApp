package com.notiz.app.export

/**
 * Minimal Excel (.xlsx) writer in pure Kotlin: text and number cells, a bold header row,
 * an amount format, column widths and a frozen header. Files are stored (uncompressed) in
 * the zip, which Excel, Google Sheets and LibreOffice all accept.
 */
class XlsxSheet(val name: String) {
    sealed interface Cell {
        data class Text(val value: String) : Cell
        data class Number(val value: Double) : Cell

        /** Rupee amount; written as a number with a #,##0.00 format so it can be summed. */
        data class Amount(val paise: Long) : Cell
        data object Empty : Cell
    }

    internal val rows = mutableListOf<Pair<List<Cell>, Boolean>>() // cells, bold
    internal var widths: List<Double> = emptyList()

    fun header(vararg titles: String) { rows += titles.map { Cell.Text(it) } to true }
    fun row(vararg cells: Cell) { rows += cells.toList() to false }
    fun boldRow(vararg cells: Cell) { rows += cells.toList() to true }
    fun columnWidths(vararg chars: Double) { widths = chars.toList() }
}

fun cellText(value: String?) = if (value.isNullOrEmpty()) XlsxSheet.Cell.Empty else XlsxSheet.Cell.Text(value)
fun cellNumber(value: Number) = XlsxSheet.Cell.Number(value.toDouble())
fun cellAmount(paise: Long) = XlsxSheet.Cell.Amount(paise)

fun buildXlsx(sheets: List<XlsxSheet>): ByteArray {
    val zip = ZipWriter()
    zip.add("[Content_Types].xml", contentTypes(sheets.size))
    zip.add("_rels/.rels", ROOT_RELS)
    zip.add("xl/workbook.xml", workbook(sheets))
    zip.add("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
    zip.add("xl/styles.xml", STYLES)
    sheets.forEachIndexed { i, sheet -> zip.add("xl/worksheets/sheet${i + 1}.xml", sheetXml(sheet)) }
    return zip.finish()
}

// Style indexes in STYLES' cellXfs
private const val STYLE_DEFAULT = 0
private const val STYLE_BOLD = 1
private const val STYLE_AMOUNT = 2
private const val STYLE_AMOUNT_BOLD = 3

private fun sheetXml(sheet: XlsxSheet): String = buildString {
    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
    append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
    append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
    if (sheet.widths.isNotEmpty()) {
        append("<cols>")
        sheet.widths.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        append("</cols>")
    }
    append("<sheetData>")
    sheet.rows.forEachIndexed { r, (cells, bold) ->
        append("""<row r="${r + 1}">""")
        cells.forEachIndexed { c, cell ->
            val ref = columnName(c) + (r + 1)
            when (cell) {
                is XlsxSheet.Cell.Text -> append("""<c r="$ref" t="inlineStr" s="${if (bold) STYLE_BOLD else STYLE_DEFAULT}"><is><t xml:space="preserve">${xmlEscape(cell.value)}</t></is></c>""")
                is XlsxSheet.Cell.Number -> append("""<c r="$ref" s="${if (bold) STYLE_BOLD else STYLE_DEFAULT}"><v>${numberText(cell.value)}</v></c>""")
                is XlsxSheet.Cell.Amount -> append("""<c r="$ref" s="${if (bold) STYLE_AMOUNT_BOLD else STYLE_AMOUNT}"><v>${paiseText(cell.paise)}</v></c>""")
                XlsxSheet.Cell.Empty -> Unit
            }
        }
        append("</row>")
    }
    append("</sheetData></worksheet>")
}

/** 0 → A, 25 → Z, 26 → AA */
internal fun columnName(index: Int): String {
    var n = index + 1
    val sb = StringBuilder()
    while (n > 0) {
        val rem = (n - 1) % 26
        sb.insert(0, ('A' + rem))
        n = (n - 1) / 26
    }
    return sb.toString()
}

private fun paiseText(paise: Long): String {
    val sign = if (paise < 0) "-" else ""
    val a = kotlin.math.abs(paise)
    return "$sign${a / 100}.${(a % 100).toString().padStart(2, '0')}"
}

private fun numberText(v: Double): String = if (v == kotlin.math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()

private fun xmlEscape(s: String): String = buildString {
    for (ch in s) {
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> if (ch.code >= 0x20 || ch == '\t' || ch == '\n') append(ch)
        }
    }
}

private fun sheetName(name: String): String =
    name.filterNot { it in "[]:*?/\\" }.take(31).ifEmpty { "Sheet" }

private fun contentTypes(n: Int) = buildString {
    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
    append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
    append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
    append("""<Default Extension="xml" ContentType="application/xml"/>""")
    append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
    append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
    for (i in 1..n) append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
    append("</Types>")
}

private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

private fun workbook(sheets: List<XlsxSheet>) = buildString {
    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
    append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
    sheets.forEachIndexed { i, s -> append("""<sheet name="${xmlEscape(sheetName(s.name))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
    append("</sheets></workbook>")
}

private fun workbookRels(n: Int) = buildString {
    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
    append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
    for (i in 1..n) append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
    append("""<Relationship Id="rId${n + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
    append("</Relationships>")
}

private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.00"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="164" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""

// ─────────────────────────── Zip (stored, no compression) ───────────────────────────

internal class ZipWriter {
    private val out = ArrayList<Byte>(16 * 1024)
    private val central = ArrayList<Byte>(1024)
    private var count = 0

    fun add(name: String, content: String) = add(name, content.encodeToByteArray())

    fun add(name: String, data: ByteArray) {
        val nameBytes = name.encodeToByteArray()
        val crc = crc32(data)
        val offset = out.size
        // Local file header
        out.u32(0x04034b50); out.u16(20); out.u16(0x0800); out.u16(0) // version, flags (UTF-8 names), stored
        out.u16(DOS_TIME); out.u16(DOS_DATE)
        out.u32(crc); out.u32(data.size.toLong()); out.u32(data.size.toLong())
        out.u16(nameBytes.size); out.u16(0)
        out.addAll(nameBytes.toList()); out.addAll(data.toList())
        // Central directory entry
        central.u32(0x02014b50); central.u16(20); central.u16(20); central.u16(0x0800); central.u16(0)
        central.u16(DOS_TIME); central.u16(DOS_DATE)
        central.u32(crc); central.u32(data.size.toLong()); central.u32(data.size.toLong())
        central.u16(nameBytes.size); central.u16(0); central.u16(0); central.u16(0); central.u16(0)
        central.u32(0); central.u32(offset.toLong())
        central.addAll(nameBytes.toList())
        count++
    }

    fun finish(): ByteArray {
        val centralOffset = out.size
        out.addAll(central)
        out.u32(0x06054b50); out.u16(0); out.u16(0); out.u16(count); out.u16(count)
        out.u32(central.size.toLong()); out.u32(centralOffset.toLong()); out.u16(0)
        return out.toByteArray()
    }

    private fun ArrayList<Byte>.u16(v: Int) { add((v and 0xFF).toByte()); add((v shr 8 and 0xFF).toByte()) }
    private fun ArrayList<Byte>.u32(v: Long) { for (i in 0 until 4) add((v shr (8 * i) and 0xFF).toByte()) }
    private fun ArrayList<Byte>.u32(v: Int) = u32(v.toLong() and 0xFFFFFFFFL)

    companion object {
        // 2026-01-01 00:00 in MS-DOS format (timestamps don't matter for these files)
        private const val DOS_TIME = 0
        private const val DOS_DATE = ((2026 - 1980) shl 9) or (1 shl 5) or 1

        private val CRC_TABLE = LongArray(256) { n ->
            var c = n.toLong()
            repeat(8) { c = if (c and 1L != 0L) 0xEDB88320L xor (c ushr 1) else c ushr 1 }
            c
        }

        fun crc32(data: ByteArray): Long {
            var c = 0xFFFFFFFFL
            for (b in data) c = CRC_TABLE[((c xor b.toLong()) and 0xFF).toInt()] xor (c ushr 8)
            return c xor 0xFFFFFFFFL
        }
    }
}
