package com.notiz.app.export

import androidx.compose.runtime.Composable

/** Saves a generated file and opens the system share sheet (WhatsApp, email, Drive, Files…). */
interface FileSharer {
    /** Throws if the file can't be written or no app can receive it. */
    fun share(bytes: ByteArray, fileName: String, mimeType: String)
}

@Composable
expect fun rememberFileSharer(): FileSharer

object MimeTypes {
    const val PDF = "application/pdf"
    const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
}
