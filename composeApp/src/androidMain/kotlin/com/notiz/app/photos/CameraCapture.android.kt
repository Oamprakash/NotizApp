package com.notiz.app.photos

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Opens the phone's camera app (no camera permission needed: the app doesn't declare it, so the
 * camera app takes the photo). The full-size photo goes to cache/camera through our FileProvider.
 */
@Composable
actual fun rememberCameraCapture(onCaptured: (ByteArray) -> Unit, onError: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val captured by rememberUpdatedState(onCaptured)
    val failed by rememberUpdatedState(onError)
    // Survives the activity being recreated while the camera app is open
    var pending by rememberSaveable { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = pending ?: return@rememberLauncherForActivityResult
        pending = null
        val file = File(path)
        if (!saved) {
            file.delete()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val jpeg = withContext(Dispatchers.IO) {
                runCatching { shrinkToJpeg(context, Uri.fromFile(file)) }.getOrNull().also { file.delete() }
            }
            if (jpeg != null) captured(jpeg) else failed()
        }
    }
    return remember(launcher) {
        {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "visitor-${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pending = file.absolutePath
            try {
                launcher.launch(uri)
            } catch (e: ActivityNotFoundException) {
                pending = null
                failed()
            } catch (e: SecurityException) {
                pending = null
                failed()
            }
        }
    }
}
