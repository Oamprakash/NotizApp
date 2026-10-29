package com.notiz.app.push

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter

/** Values of `data.type` sent by the server (see the push migration). */
object PushTypes {
    const val NOTICE = "notice"
    const val DUES = "dues"
    const val COMPLAINT = "complaint"
    const val POLL = "poll"
    const val CHAT = "chat"
    const val MEMBER = "member"
    const val APPROVED = "approved"
}

/**
 * Bridge from the platform push layer (Android FirebaseMessagingService, iOS AppDelegate)
 * into shared code. Swift calls these as `PushEvents.shared.onNewToken(token:)` etc.
 */
object PushEvents {
    private val _tokens = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 4)

    /** Latest FCM token (replayed to late subscribers). */
    val tokens: SharedFlow<String> = _tokens.asSharedFlow()

    private val _opened = MutableStateFlow<String?>(null)

    /** Type of the notification the user tapped, until the UI consumes it. */
    val opened: StateFlow<String?> = _opened.asStateFlow()

    private val _updates = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Types of notifications received or opened — screens reload when their type arrives. */
    val updates: SharedFlow<String> = _updates.asSharedFlow()

    fun onNewToken(token: String) {
        _tokens.tryEmit(token)
    }

    fun onNotificationReceived(type: String?) {
        if (!type.isNullOrBlank()) _updates.tryEmit(type)
    }

    fun onNotificationOpened(type: String?) {
        if (type.isNullOrBlank()) return
        _updates.tryEmit(type)
        _opened.value = type
    }

    fun consumeOpened() {
        _opened.value = null
    }
}

/** Platform push access: Firebase Messaging on Android, Firebase + APNs on iOS. */
expect object PushPlatform {
    /** "android" or "ios" — stored with the token so the server knows the platform. */
    val name: String

    /** The device's FCM token, or null if push isn't set up (no Firebase config) or unavailable. */
    suspend fun currentToken(): String?
}

/** Asks the user for notification permission the first time it is shown (Android 13+ / iOS). */
@Composable
expect fun NotificationPermissionRequest()

/** Calls [onUpdate] whenever a push of one of [types] is received or opened. */
@Composable
fun OnPushUpdate(vararg types: String, onUpdate: () -> Unit) {
    val callback = rememberUpdatedState(onUpdate)
    val wanted = types.toSet()
    LaunchedEffect(wanted) {
        PushEvents.updates.filter { it in wanted }.collect { callback.value() }
    }
}
