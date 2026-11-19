package com.notiz.app.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.notiz.app.MainActivity
import com.notiz.app.R
import com.notiz.app.i18n.LanguagePrefs
import java.util.Locale

object AndroidNotifications {
    /** Notices, dues, help desk, polls, membership. Must match ANDROID_CHANNEL_ID in the push function. */
    const val CHANNEL_ID = "notiz_general"

    /** Society chat (quieter). Must match ANDROID_CHAT_CHANNEL_ID in the push function. */
    const val CHAT_CHANNEL_ID = "notiz_chat"

    /** Visitors at the gate: loud, rings until answered (or 10 minutes). The app shows these itself. */
    const val VISITOR_CHANNEL_ID = "notiz_visitors"

    /** Short silent confirmations ("Allowed. The gate has been told."). */
    private const val STATUS_CHANNEL_ID = "notiz_status"

    /** Intent extra carrying the notification type; FCM uses the same key for background taps. */
    const val EXTRA_TYPE = "type"

    private const val GATE_TAG = "gate"
    private const val VISITOR_TIMEOUT_MS = 10 * 60 * 1000L

    private fun tamil(): Boolean =
        (LanguagePrefs.load() ?: Locale.getDefault().language) == "ta"

    /** Creates (or renames, after a language change) every channel. Safe to call often. */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        LanguagePrefs.init(context)
        val ta = tamil()
        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                VISITOR_CHANNEL_ID,
                if (ta) "வாயிலில் பார்வையாளர்கள்" else "Visitors at the gate",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = if (ta) "அனுமதி / மறு கேட்கும்போது ஒலிக்கும்" else "Rings when someone at the gate needs your Allow or Deny"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                if (ta) "சங்க அறிவிப்புகள்" else "Society updates",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = if (ta) "அறிவிப்புகள், கட்டணம், உதவி மையம், வாக்கெடுப்பு" else "Notices, dues, help desk and polls"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHAT_CHANNEL_ID,
                if (ta) "சங்க அரட்டை" else "Society chat",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                STATUS_CHANNEL_ID,
                if (ta) "உறுதிப்படுத்தல்கள்" else "Confirmations",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    /** Kept for callers from before the channel split. */
    fun ensureChannel(context: Context) = ensureChannels(context)

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun builder(context: Context, channel: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channel)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context).setPriority(Notification.PRIORITY_HIGH)
        }

    private fun openAppIntent(context: Context, type: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TYPE, type)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Shows a notification while the app is in the foreground. (In the background, Android shows
     * FCM notifications itself using the channel sent by the server.)
     */
    fun show(context: Context, title: String, body: String, type: String?, tag: String?) {
        if (!canPost(context)) return
        ensureChannels(context)
        val channel = if (type == PushTypes.CHAT) CHAT_CHANNEL_ID else CHANNEL_ID
        val notification = builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, type, (tag ?: type ?: title).hashCode()))
            .build()

        // Chat uses a fixed tag so new messages replace the previous one instead of piling up.
        val id = if (tag != null) 1 else (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        context.getSystemService(NotificationManager::class.java).notify(tag, id, notification)
    }

    private fun visitorId(entryId: String) = entryId.hashCode()

    /**
     * A visitor waiting at the gate: rings (repeating) until someone in the flat answers, the guard
     * acts, or 10 minutes pass. Allow / Deny answer straight from the notification; tapping it
     * opens the app. [labels] come from the server in the person's language.
     */
    fun showVisitor(
        context: Context,
        entryId: String,
        title: String,
        body: String,
        labels: GateLabels,
        actionable: Boolean,
    ) {
        if (!canPost(context)) return
        ensureChannels(context)
        val id = visitorId(entryId)
        val b = builder(context, VISITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, PushTypes.GATE, id))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) b.setTimeoutAfter(VISITOR_TIMEOUT_MS)
        if (actionable) {
            b.addAction(
                Notification.Action.Builder(null as Icon?, labels.deny, GateActionReceiver.intent(context, entryId, allow = false, labels)).build(),
            )
            b.addAction(
                Notification.Action.Builder(null as Icon?, labels.allow, GateActionReceiver.intent(context, entryId, allow = true, labels)).build(),
            )
        }
        val notification = b.build()
        // Repeat the sound until the notification is answered or dismissed
        if (actionable) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        context.getSystemService(NotificationManager::class.java).notify(GATE_TAG, id, notification)
    }

    /** Stops the ringing for a visitor (answered here, on another phone, or by the guard). */
    fun cancelVisitor(context: Context, entryId: String) {
        context.getSystemService(NotificationManager::class.java).cancel(GATE_TAG, visitorId(entryId))
    }

    /** A quiet line that disappears by itself, e.g. "Allowed. The gate has been told." */
    fun showStatus(context: Context, entryId: String, text: String) {
        if (!canPost(context)) return
        ensureChannels(context)
        val b = builder(context, STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, PushTypes.GATE, visitorId(entryId) + 1))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) b.setTimeoutAfter(15_000)
        context.getSystemService(NotificationManager::class.java).notify("$GATE_TAG-status", visitorId(entryId), b.build())
    }
}

/** Button and confirmation texts for a visitor alert, sent by the server in the person's language. */
data class GateLabels(
    val allow: String,
    val deny: String,
    val allowed: String,
    val denied: String,
    val failed: String,
) {
    companion object {
        fun from(data: Map<String, String>) = GateLabels(
            allow = data["l_allow"] ?: "Allow",
            deny = data["l_deny"] ?: "Deny",
            allowed = data["l_allowed"] ?: "Allowed. The gate has been told.",
            denied = data["l_denied"] ?: "Denied. The gate has been told.",
            failed = data["l_failed"] ?: "Couldn't send your answer. Open Notiz to try again.",
        )
    }
}
