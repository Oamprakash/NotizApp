package com.notiz.app.photos

import androidx.compose.runtime.Composable

/** Longest side of uploaded photos, in pixels — sharp on phones, usually 200–500 KB as JPEG. */
const val PHOTO_MAX_SIDE = 1600

/** JPEG quality for uploads (0–100). */
const val PHOTO_JPEG_QUALITY = 80

/**
 * Remembers a launcher for the system photo picker (no storage permission needed).
 * The picked photo is shrunk to [PHOTO_MAX_SIDE] and passed to [onPicked] as JPEG bytes;
 * [onError] runs if it can't be read. Nothing happens if the user cancels.
 */
@Composable
expect fun rememberPhotoPicker(onPicked: (ByteArray) -> Unit, onError: () -> Unit): () -> Unit

/**
 * Remembers a launcher for the camera. The photo is shrunk like [rememberPhotoPicker] and passed to
 * [onCaptured]; [onError] runs if there is no camera or the photo can't be read. Nothing happens if
 * the user cancels.
 */
@Composable
expect fun rememberCameraCapture(onCaptured: (ByteArray) -> Unit, onError: () -> Unit): () -> Unit
