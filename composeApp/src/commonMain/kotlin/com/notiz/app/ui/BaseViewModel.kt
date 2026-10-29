package com.notiz.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notiz.app.i18n.UiText
import com.notiz.app.i18n.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

abstract class BaseViewModel : ViewModel() {

    /** Runs [block], turning any failure into a translatable message for [onError]. */
    protected fun launchSafe(
        onError: (UiText) -> Unit,
        block: suspend CoroutineScope.() -> Unit,
    ): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            onError(e.toUiText())
        }
    }
}
