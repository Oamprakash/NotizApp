package com.notiz.app.i18n

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

actual object LanguagePrefs {
    private const val KEY = "language"
    private var prefs: SharedPreferences? = null

    /** Called from MainActivity before the UI is created. */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("notiz_settings", Context.MODE_PRIVATE)
    }

    actual fun load(): String? = prefs?.getString(KEY, null)

    actual fun save(code: String?) {
        val editor = prefs?.edit() ?: return
        if (code == null) editor.remove(KEY) else editor.putString(KEY, code)
        editor.apply()
    }
}

actual object LocalAppLocale {
    private var default: Locale? = null

    actual val current: String
        @Composable get() = Locale.getDefault().toString()

    @Suppress("DEPRECATION") // Locale(String) / updateConfiguration: what the JetBrains sample uses
    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val configuration = LocalConfiguration.current
        if (default == null) default = Locale.getDefault()
        val new = if (value == null) default!! else Locale(value)
        Locale.setDefault(new)
        configuration.setLocale(new)
        val resources = LocalContext.current.resources
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return LocalConfiguration.provides(configuration)
    }
}
