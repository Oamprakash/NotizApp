package com.notiz.app.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.notiz.app.MainActivity
import com.notiz.app.R

object AndroidNotifications {
    /** Must match ANDROID_CHANNEL_ID in supabase/functions/push/index.ts */
    const val CHANNEL_ID = "notiz_general"

    /** Intent extra carrying the notification type; FCM uses the same key for background taps. */
    const val EXTRA_TYPE = "type"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Society updates", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Notices, dues, complaints, polls and chat"
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Shows a notification while the app is in the foreground. (In the background, Android shows
     * FCM notifications itself using the channel and icon declared in the manifest.)
     */
    fun show(context: Context, title: String, body: String, type: String?, tag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TYPE, type)
        }
        val requestCode = (tag ?: type ?: title).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context).setPriority(Notification.PRIORITY_HIGH)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Chat uses a fixed tag so new messages replace the previous one instead of piling up.
        val id = if (tag != null) 1 else (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        context.getSystemService(NotificationManager::class.java).notify(tag, id, notification)
    }
}
