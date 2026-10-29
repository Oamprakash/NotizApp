package com.notiz.app.i18n

import androidx.compose.runtime.Composable
import com.notiz.app.resources.*
import com.notiz.app.util.formatDate
import com.notiz.app.util.formatTimestamp
import org.jetbrains.compose.resources.stringArrayResource

/** Short month names in the current language. */
@Composable
fun localizedMonths(): List<String> = stringArrayResource(Res.array.months_short)

/** "2026-11-05" → "5 Nov 2026" / "5 நவ. 2026" */
@Composable
fun localDate(isoDate: String): String = formatDate(isoDate, localizedMonths())

/** Postgres timestamp → "5 Nov 2026, 4:30 PM" in the current language. */
@Composable
fun localTimestamp(iso: String, withTime: Boolean = true): String = formatTimestamp(iso, withTime, localizedMonths())
