package com.notiz.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.notiz.app.push.AndroidNotifications
import com.notiz.app.push.PushEvents

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidNotifications.ensureChannel(this)
        if (savedInstanceState == null) handleNotificationTap(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationTap(intent)
    }

    /** Taps on notifications (ours or FCM's background ones) carry the "type" extra. */
    private fun handleNotificationTap(intent: Intent?) {
        val type = intent?.getStringExtra(AndroidNotifications.EXTRA_TYPE) ?: return
        PushEvents.onNotificationOpened(type)
        intent.removeExtra(AndroidNotifications.EXTRA_TYPE)
    }
}
