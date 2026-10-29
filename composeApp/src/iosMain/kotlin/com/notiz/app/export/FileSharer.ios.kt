package com.notiz.app.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

@Composable
actual fun rememberFileSharer(): FileSharer = remember { IosFileSharer() }

private class IosFileSharer : FileSharer {

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun share(bytes: ByteArray, fileName: String, mimeType: String) {
        val path = NSTemporaryDirectory() + fileName
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        check(data.writeToFile(path, atomically = true)) { "Couldn't write $fileName" }

        val presenter = topViewController() ?: error("No screen to share from")
        val sheet = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(path)), applicationActivities = null)
        sheet.popoverPresentationController?.sourceView = presenter.view // required on iPad
        presenter.presentViewController(sheet, animated = true, completion = null)
    }

    @Suppress("DEPRECATION") // keyWindow is fine for a single-window app
    private fun topViewController(): UIViewController? {
        var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (vc?.presentedViewController != null) vc = vc.presentedViewController
        return vc
    }
}
