package com.notiz.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Receives FCM token updates, visitor alerts, silent pushes and foreground messages. */
class NotizMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushEvents.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"]

        // Silent: someone already answered this visitor, so stop the ringing here too
        if (data["silent"] == "1") {
            if (type == PushTypes.GATE_CANCEL) data["id"]?.let { AndroidNotifications.cancelVisitor(this, it) }
            PushEvents.onNotificationReceived(PushTypes.GATE)
            return
        }

        // Visitor alerts arrive as data messages (even in the background) so we can add Allow / Deny
        val isVisitorAlert = (type == PushTypes.GATE && data["gate_action"] == "ask") || type == PushTypes.GATE_TEST
        if (isVisitorAlert) {
            PushEvents.onNotificationReceived(PushTypes.GATE)
            AndroidNotifications.showVisitor(
                context = this,
                entryId = data["id"] ?: return,
                title = data["title"].orEmpty(),
                body = data["body"].orEmpty(),
                labels = GateLabels.from(data),
                actionable = type == PushTypes.GATE,
            )
            return
        }

        PushEvents.onNotificationReceived(type) // open screens reload their data
        val title = message.notification?.title ?: data["title"] ?: return
        val body = message.notification?.body ?: data["body"].orEmpty()
        val societyId = data["society_id"]
        val tag = if (type == PushTypes.CHAT && societyId != null) "chat-$societyId" else null
        AndroidNotifications.show(this, title, body, type, tag)
    }
}
