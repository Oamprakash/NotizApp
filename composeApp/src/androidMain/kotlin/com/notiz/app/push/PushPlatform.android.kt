package com.notiz.app.push

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual object PushPlatform {
    actual val name: String = "android"

    actual suspend fun currentToken(): String? {
        // Throws IllegalStateException when google-services.json wasn't added (Firebase not initialised)
        val messaging = try {
            FirebaseMessaging.getInstance()
        } catch (e: IllegalStateException) {
            return null
        }
        return suspendCancellableCoroutine { cont ->
            messaging.token.addOnCompleteListener { task ->
                cont.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }
}

@Composable
actual fun NotificationPermissionRequest() {
    // Android 12 and below grant notifications at install time.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
