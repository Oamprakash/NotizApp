package com.notiz.app.push

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

private fun read(context: Context): AlertHealth {
    AndroidNotifications.ensureChannels(context)
    val manager = context.getSystemService(NotificationManager::class.java)
    val notificationsOn = manager.areNotificationsEnabled()
    val channelOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = manager.getNotificationChannel(AndroidNotifications.VISITOR_CHANNEL_ID)
        channel != null && channel.importance >= NotificationManager.IMPORTANCE_HIGH
    } else {
        null
    }
    val power = context.getSystemService(PowerManager::class.java)
    return AlertHealth(
        notificationsOn = notificationsOn,
        visitorAlertsOn = channelOn,
        backgroundOn = power.isIgnoringBatteryOptimizations(context.packageName),
        brand = Build.MANUFACTURER.orEmpty().lowercase(),
    )
}

private class AndroidAlertCheck(private val context: Context) : AlertCheck {
    override var health by mutableStateOf(read(context))

    fun refresh() {
        health = read(context)
    }

    private fun start(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { appDetails() }
    }

    private fun appDetails() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    override fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            start(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        } else {
            appDetails()
        }
    }

    override fun openVisitorAlertSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            start(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, AndroidNotifications.VISITOR_CHANNEL_ID),
            )
        } else {
            appDetails()
        }
    }

    // The list of battery-optimised apps (no special permission needed). Some phone makers keep
    // their own "autostart" switches, which the screen explains in words.
    override fun openBackgroundSettings() = start(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
}

@Composable
actual fun rememberAlertCheck(): AlertCheck {
    val context = LocalContext.current.applicationContext
    val check = remember { AndroidAlertCheck(context) }
    LifecycleResumeEffect(check) {
        check.refresh()
        onPauseOrDispose { }
    }
    return check
}
