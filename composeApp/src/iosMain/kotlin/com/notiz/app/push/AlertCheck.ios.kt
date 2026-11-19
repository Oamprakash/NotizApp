package com.notiz.app.push

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIBackgroundRefreshStatus
import platform.UserNotifications.UNAuthorizationStatus
import platform.UserNotifications.UNNotificationSetting
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private class IosAlertCheck : AlertCheck {
    override var health by mutableStateOf(AlertHealth(null, null, null, "apple"))

    fun refresh() {
        val background = UIApplication.sharedApplication.backgroundRefreshStatus ==
            UIBackgroundRefreshStatus.UIBackgroundRefreshStatusAvailable
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            val on = status == UNAuthorizationStatus.UNAuthorizationStatusAuthorized ||
                status == UNAuthorizationStatus.UNAuthorizationStatusProvisional ||
                status == UNAuthorizationStatus.UNAuthorizationStatusEphemeral
            // Time Sensitive lets visitor alerts through Focus modes; "not supported" counts as fine
            val timeSensitive = settings?.timeSensitiveSetting != UNNotificationSetting.UNNotificationSettingDisabled
            dispatch_async(dispatch_get_main_queue()) {
                health = AlertHealth(on, timeSensitive, background, "apple")
            }
        }
    }

    private fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>(), null)
    }

    override fun openNotificationSettings() = openSettings()
    override fun openVisitorAlertSettings() = openSettings()
    override fun openBackgroundSettings() = openSettings()
}

@Composable
actual fun rememberAlertCheck(): AlertCheck {
    val check = remember { IosAlertCheck() }
    LifecycleResumeEffect(check) {
        check.refresh()
        onPauseOrDispose { }
    }
    return check
}
