package com.notiz.app.push

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter

actual object PushPlatform {
    actual val name: String = "ios"

    /**
     * The FCM token arrives from Swift (AppDelegate → PushEvents.shared.onNewToken) once Firebase
     * has the APNs token. Wait briefly for it; null if Firebase isn't configured.
     */
    actual suspend fun currentToken(): String? =
        withTimeoutOrNull(15_000) { PushEvents.tokens.first() }
}

@Composable
actual fun NotificationPermissionRequest() {
    LaunchedEffect(Unit) {
        // iOS only shows the system prompt the first time; later calls return the saved answer.
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound,
        ) { _, _ -> }
    }
}
