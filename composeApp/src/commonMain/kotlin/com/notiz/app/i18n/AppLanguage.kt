package com.notiz.app.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Languages the app is translated into. Strings: composeResources/values (en), values-ta (ta). */
enum class Language(val code: String, val nativeName: String) {
    ENGLISH("en", "English"),
    TAMIL("ta", "தமிழ்"),
}

/**
 * The language the user picked inside the app, or null to follow the phone's language.
 * Saved on the device so it survives restarts.
 */
object AppLanguage {
    var selected: Language? by mutableStateOf(
        LanguagePrefs.load()?.let { code -> Language.entries.firstOrNull { it.code == code } },
    )
        private set

    fun select(language: Language?) {
        LanguagePrefs.save(language?.code)
        selected = language
    }
}

/** The language actually shown: the in-app choice, else the phone's language if supported, else English. */
@Composable
fun currentLanguage(): Language =
    AppLanguage.selected
        ?: Language.entries.firstOrNull { LocalAppLocale.current.startsWith(it.code) }
        ?: Language.ENGLISH

/** Device storage for the chosen language code. */
expect object LanguagePrefs {
    fun load(): String?
    fun save(code: String?)
}

/**
 * Overrides the locale Compose resources use, so switching language in the app takes effect
 * immediately (JetBrains' recommended approach until a common API exists).
 */
expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

/** Wrap the whole UI in this; it re-composes everything in the new language when it changes. */
@Composable
fun AppEnvironment(content: @Composable () -> Unit) {
    val code = AppLanguage.selected?.code
    CompositionLocalProvider(LocalAppLocale provides code) {
        key(code) {
            content()
        }
    }
}
