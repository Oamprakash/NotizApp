package com.notiz.app.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

@Composable
actual fun rememberPhotoPicker(onPicked: (ByteArray) -> Unit, onError: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val failed by rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val jpeg = withContext(Dispatchers.IO) { runCatching { shrinkToJpeg(context, uri) }.getOrNull() }
                if (jpeg != null) picked(jpeg) else failed()
            }
        }
    }
    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}

/** Decodes at a reduced size (saves memory on big camera photos), fixes rotation, re-encodes. */
private fun shrinkToJpeg(context: Context, uri: Uri): ByteArray {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not an image" }

    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_MAX_SIDE) sample *= 2
    val decoded = resolver.openInputStream(uri)!!.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Couldn't decode")

    val rotation = runCatching {
        resolver.openInputStream(uri)!!.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }
    }.getOrDefault(0f)

    val scale = minOf(1f, PHOTO_MAX_SIDE.toFloat() / max(decoded.width, decoded.height))
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(rotation)
    }
    val output = if (scale < 1f || rotation != 0f) {
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    } else {
        decoded
    }
    return ByteArrayOutputStream().use { out ->
        output.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, out)
        out.toByteArray()
    }.also {
        if (output !== decoded) output.recycle()
        decoded.recycle()
    }
}

