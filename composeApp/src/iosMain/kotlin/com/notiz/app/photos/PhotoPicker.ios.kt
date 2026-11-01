package com.notiz.app.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy
import kotlin.math.max
import kotlin.math.min

@Composable
actual fun rememberPhotoPicker(onPicked: (ByteArray) -> Unit, onError: () -> Unit): () -> Unit {
    val picked by rememberUpdatedState(onPicked)
    val failed by rememberUpdatedState(onError)
    // Kept here because the picker only holds its delegate weakly
    val delegate = remember { PickerDelegate() }
    delegate.onResult = { jpeg -> if (jpeg != null) picked(jpeg) else failed() }
    return remember {
        {
            val config = PHPickerConfiguration().apply {
                filter = PHPickerFilter.imagesFilter
                selectionLimit = 1
            }
            val picker = PHPickerViewController(configuration = config)
            picker.delegate = delegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class PickerDelegate : NSObject(), PHPickerViewControllerDelegateProtocol {
    var onResult: (ByteArray?) -> Unit = {}

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return // cancelled
        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            val jpeg = data?.let { runCatching { shrinkToJpeg(it) }.getOrNull() }
            dispatch_async(dispatch_get_main_queue()) { onResult(jpeg) }
        }
    }
}

/** Redraws at most [PHOTO_MAX_SIDE] px (this also applies the photo's orientation) and encodes JPEG. */
@OptIn(ExperimentalForeignApi::class)
private fun shrinkToJpeg(data: NSData): ByteArray? {
    val image = UIImage(data = data)
    val (width, height) = image.size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0) return null
    val scale = min(1.0, PHOTO_MAX_SIDE / max(width, height))
    val w = width * scale
    val h = height * scale
    val format = UIGraphicsImageRendererFormat().apply { this.scale = 1.0 }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(w, h), format = format)
    val resized = renderer.imageWithActions { _ -> image.drawInRect(CGRectMake(0.0, 0.0, w, h)) }
    val jpeg = UIImageJPEGRepresentation(resized, PHOTO_JPEG_QUALITY / 100.0) ?: return null
    return jpeg.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}

@Suppress("DEPRECATION") // keyWindow is fine for a single-window app
private fun topViewController(): UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}
