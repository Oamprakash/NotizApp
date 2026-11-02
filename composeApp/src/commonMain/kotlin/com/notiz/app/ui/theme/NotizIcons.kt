package com.notiz.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Line icons drawn for the redesign (24×24, 2 px rounded strokes), so the app doesn't depend on
 * the large material-icons-extended library. Tint them with Icon(tint = …) as usual.
 */
object NotizIcons {
    val Home by lazy { icon("M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z") }
    val Notice by lazy { icon("M3 11v2a1 1 0 0 0 1 1h2l5 4V6L6 10H4a1 1 0 0 0-1 1z", "M15 9a4 4 0 0 1 0 6", "M18 6.5a8 8 0 0 1 0 11") }
    val Rupee by lazy { icon("M7 4h10M7 9h10M7 4c5 0 7 2 7 5s-3 5-7 5l7 6") }
    val Wrench by lazy { icon("M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.6 2.6-2.4-.6-.6-2.4z") }
    val Chat by lazy { icon("M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z") }
    val Bell by lazy { icon("M6 9a6 6 0 0 1 12 0c0 6 2.5 7.5 2.5 7.5h-17S6 15 6 9z", "M10 20a2 2 0 0 0 4 0") }
    val Poll by lazy { icon("M5 20V10M12 20V4M19 20v-7") }
    val Users by lazy {
        icon("M12.5 8a3.5 3.5 0 1 1-7 0a3.5 3.5 0 1 1 7 0", "M2.5 20a6.5 6.5 0 0 1 13 0", "M16 4.5a3.5 3.5 0 0 1 0 7M21.5 20a6.5 6.5 0 0 0-4-6")
    }
    val UserPlus by lazy { icon("M12.5 8a3.5 3.5 0 1 1-7 0a3.5 3.5 0 1 1 7 0", "M2.5 20a6.5 6.5 0 0 1 13 0", "M19 8v6M16 11h6") }
    val Download by lazy { icon("M12 4v11m0 0-4-4m4 4 4-4M5 20h14") }
    val ChevronRight by lazy { icon("m9 6 6 6-6 6") }
    val ChevronLeft by lazy { icon("M15 6l-6 6 6 6") }
    val Check by lazy { icon("m5 12 5 5 9-10") }
    val Clock by lazy { icon("M20 12a8 8 0 1 1-16 0a8 8 0 1 1 16 0", "M12 8v4l3 2") }
    val Flag by lazy { icon("M5 21V4h11l-1.5 4L16 12H5") }
    val Share by lazy { icon("M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7", "m16 6-4-4-4 4M12 2v13") }
    val Plus by lazy { icon("M12 5v14M5 12h14") }
    val Drop by lazy { icon("M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11z") }
    val Bolt by lazy { icon("M13 2 4 14h7l-1 8 9-12h-7z") }
    val Lift by lazy { icon("M7 3h10a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "m9 9 3-3 3 3M9 15l3 3 3-3") }
    val Shield by lazy { icon("M12 3 5 6v6c0 4.5 3 7.5 7 9 4-1.5 7-4.5 7-9V6z") }
    val Sparkle by lazy { icon("M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M6 18l2.5-2.5M15.5 8.5 18 6") }
    val Car by lazy { icon("M4 17v-6l2.5-5h11L20 11v6z", "M7 17v2M17 17v2", "M4 11h16") }
    val More by lazy { icon("M5 12h.01M12 12h.01M19 12h.01", width = 3f) }
    val Camera by lazy {
        icon("M4 8h3l2-3h6l2 3h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z", "M15.5 13.5a3.5 3.5 0 1 1-7 0a3.5 3.5 0 1 1 7 0")
    }
    val Send by lazy { icon("M4 12 20 4l-6 16-3-7z") }
    val Reply by lazy { icon("M9 7 4 12l5 5", "M4 12h10a6 6 0 0 1 6 6v1") }
    val Close by lazy { icon("M6 6l12 12M18 6 6 18") }
    val Pin by lazy { icon("M12 21s7-6.2 7-12a7 7 0 0 0-14 0c0 5.8 7 12 7 12z", "M14.5 9a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0") }
    val Image by lazy {
        icon("M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M11 10a2 2 0 1 1-4 0a2 2 0 1 1 4 0", "m21 16-5-5-9 8")
    }
    val Logout by lazy { icon("M15 4h4a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-4", "M10 16l4-4-4-4M14 12H4") }
    val Trash by lazy { icon("M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13") }
    val Globe by lazy { icon("M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0", "M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18") }

    private fun icon(vararg paths: String, width: Float = 2f): ImageVector {
        val builder = ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        paths.forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
