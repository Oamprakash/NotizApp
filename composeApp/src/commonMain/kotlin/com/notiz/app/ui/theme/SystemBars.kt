package com.notiz.app.ui.theme

import androidx.compose.runtime.Composable

/**
 * While shown, makes the status-bar icons white (for screens with a teal band behind the status
 * bar, like Home and Login). Reverts to dark icons when it leaves the composition.
 */
@Composable
expect fun LightStatusBarIcons()
