package com.notiz.app.push

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.notiz.app.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Allow / Deny tapped on a visitor notification. Stops the ringing straight away, then answers
 * the gate in the background (the app doesn't open). Shows a short confirmation, or a "couldn't
 * send" line with a tap to open the app.
 */
class GateActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getStringExtra(EXTRA_ENTRY) ?: return
        val allow = intent.getBooleanExtra(EXTRA_ALLOW, false)
        val okText = intent.getStringExtra(if (allow) EXTRA_ALLOWED_TEXT else EXTRA_DENIED_TEXT).orEmpty()
        val failText = intent.getStringExtra(EXTRA_FAILED_TEXT).orEmpty()
        val app = context.applicationContext

        AndroidNotifications.cancelVisitor(app, entryId)
        val pending = goAsync()
        scope.launch {
            try {
                val ok = runCatching {
                    withTimeout(20_000) { AppContainer.instance.gate.respondInBackground(entryId, allow) }
                }.isSuccess
                AndroidNotifications.showStatus(app, entryId, if (ok) okText else failText)
                if (ok) PushEvents.onNotificationReceived(PushTypes.GATE) // open screens refresh
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION = "com.notiz.app.GATE_ANSWER"
        private const val EXTRA_ENTRY = "entry_id"
        private const val EXTRA_ALLOW = "allow"
        private const val EXTRA_ALLOWED_TEXT = "allowed_text"
        private const val EXTRA_DENIED_TEXT = "denied_text"
        private const val EXTRA_FAILED_TEXT = "failed_text"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun intent(context: Context, entryId: String, allow: Boolean, labels: GateLabels): PendingIntent {
            val intent = Intent(context, GateActionReceiver::class.java).apply {
                action = ACTION
                putExtra(EXTRA_ENTRY, entryId)
                putExtra(EXTRA_ALLOW, allow)
                putExtra(EXTRA_ALLOWED_TEXT, labels.allowed)
                putExtra(EXTRA_DENIED_TEXT, labels.denied)
                putExtra(EXTRA_FAILED_TEXT, labels.failed)
            }
            // Distinct request code per entry and answer, so the two buttons don't share an intent
            val requestCode = entryId.hashCode() * 2 + if (allow) 1 else 0
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
