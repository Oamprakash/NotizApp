package com.notiz.app.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.UIKit.UIImage
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT

/** Opens the camera (asks for camera access the first time; see NSCameraUsageDescription). */
@Composable
actual fun rememberCameraCapture(onCaptured: (ByteArray) -> Unit, onError: () -> Unit): () -> Unit {
    val captured by rememberUpdatedState(onCaptured)
    val failed by rememberUpdatedState(onError)
    // Kept here because the picker only holds its delegate weakly
    val delegate = remember { CameraDelegate() }
    delegate.onResult = { jpeg -> if (jpeg != null) captured(jpeg) else failed() }
    return remember {
        {
            val camera = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            if (!UIImagePickerController.isSourceTypeAvailable(camera)) {
                delegate.onResult(null) // simulator or no camera
            } else {
                val picker = UIImagePickerController().apply {
                    sourceType = camera
                    allowsEditing = false
                }
                picker.delegate = delegate
                topViewController()?.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}

private class CameraDelegate :
    NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {
    var onResult: (ByteArray?) -> Unit = {}

    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        if (image == null) {
            onResult(null)
            return
        }
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            val jpeg = runCatching { shrinkToJpeg(image) }.getOrNull()
            dispatch_async(dispatch_get_main_queue()) { onResult(jpeg) }
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}
