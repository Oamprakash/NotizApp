package com.notiz.app.push

import com.notiz.app.AppContainer
import com.notiz.app.i18n.LanguagePrefs
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/**
 * Called from Swift (AppDelegate) for the Allow / Deny buttons on visitor alerts:
 * `GateAlerts.shared.actionLabels()` when registering the notification category, and
 * `GateAlerts.shared.respond(entryId:allow:onDone:)` when a button is tapped.
 */
object GateAlerts {
    private val scope = MainScope()

    private fun tamil(): Boolean {
        val code = LanguagePrefs.load() ?: (NSLocale.preferredLanguages.firstOrNull() as? String).orEmpty()
        return code.startsWith("ta")
    }

    /** [allow, deny] in the app language. */
    fun actionLabels(): List<String> = if (tamil()) listOf("அனுமதி", "மறு") else listOf("Allow", "Deny")

    /** Answers the gate; [onDone] gets true on success. Always calls back (needed by iOS). */
    fun respond(entryId: String, allow: Boolean, onDone: (Boolean) -> Unit) {
        scope.launch {
            val ok = runCatching {
                withTimeout(20_000) { AppContainer.instance.gate.respondInBackground(entryId, allow) }
            }.isSuccess
            if (ok) PushEvents.onNotificationReceived(PushTypes.GATE)
            onDone(ok)
        }
    }
}
