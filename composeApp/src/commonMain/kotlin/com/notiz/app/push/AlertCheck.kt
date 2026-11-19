package com.notiz.app.push

import androidx.compose.runtime.Composable

/**
 * What could stop a visitor alert from reaching this phone. Each value is null when the platform
 * can't tell (then it isn't shown as a problem).
 */
data class AlertHealth(
    /** Notifications allowed for Notiz at all. */
    val notificationsOn: Boolean?,
    /** Android: the "Visitors at the gate" channel is on and allowed to pop up. iOS: Time Sensitive allowed. */
    val visitorAlertsOn: Boolean?,
    /** Android: not battery-optimised. iOS: Background App Refresh on. */
    val backgroundOn: Boolean?,
    /** Lower-case phone maker, e.g. "xiaomi", "oneplus", "samsung", "apple". */
    val brand: String,
) {
    val allGood: Boolean get() = notificationsOn != false && visitorAlertsOn != false && backgroundOn != false
}

/** Live check, refreshed whenever the app comes back to the foreground. */
interface AlertCheck {
    val health: AlertHealth
    fun openNotificationSettings()
    fun openVisitorAlertSettings()
    fun openBackgroundSettings()
}

@Composable
expect fun rememberAlertCheck(): AlertCheck
