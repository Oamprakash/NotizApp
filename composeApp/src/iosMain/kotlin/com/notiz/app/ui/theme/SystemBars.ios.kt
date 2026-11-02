package com.notiz.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent

/**
 * White status-bar icons while a teal band is shown. Works because project.yml sets
 * UIViewControllerBasedStatusBarAppearance = NO, so the app (not SwiftUI's hosting controller)
 * decides the style.
 */
@Suppress("DEPRECATION")
@Composable
actual fun LightStatusBarIcons() {
    DisposableEffect(Unit) {
        UIApplication.sharedApplication.setStatusBarStyle(UIStatusBarStyleLightContent, animated = true)
        onDispose {
            UIApplication.sharedApplication.setStatusBarStyle(UIStatusBarStyleDarkContent, animated = true)
        }
    }
}
