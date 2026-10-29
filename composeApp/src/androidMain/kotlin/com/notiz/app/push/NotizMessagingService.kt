package com.notiz.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Receives FCM token updates and foreground messages. */
class NotizMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushEvents.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"]
        PushEvents.onNotificationReceived(type) // open screens reload their data

        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        val societyId = message.data["society_id"]
        val tag = if (type == PushTypes.CHAT && societyId != null) "chat-$societyId" else null
        AndroidNotifications.show(this, title, body, type, tag)
    }
}
