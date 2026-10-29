package com.notiz.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notiz.app.AppConfig
import com.notiz.app.AppContainer
import com.notiz.app.LocalAppContainer
import com.notiz.app.ui.BaseViewModel
import com.notiz.app.ui.components.ErrorBanner
import com.notiz.app.ui.components.LoadingButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import com.notiz.app.resources.*
import org.jetbrains.compose.resources.stringResource
import com.notiz.app.i18n.UiText
import com.notiz.app.i18n.asString
import com.notiz.app.i18n.uiText
import com.notiz.app.ui.components.LanguageToggle
import androidx.compose.ui.Alignment

data class LoginUiState(
    val phoneDigits: String = "",
    val otp: String = "",
    val otpSent: Boolean = false,
    val loading: Boolean = false,
    val error: UiText? = null,
) {
    val fullPhone get() = AppConfig.DEFAULT_COUNTRY_CODE + phoneDigits
    val canSend get() = phoneDigits.length == 10
    val canVerify get() = otp.length == 6
}

class LoginViewModel(private val container: AppContainer) : BaseViewModel() {
    val state = MutableStateFlow(LoginUiState())

    fun onPhoneChange(value: String) = state.update { it.copy(phoneDigits = value.filter(Char::isDigit).take(10)) }
    fun onOtpChange(value: String) = state.update { it.copy(otp = value.filter(Char::isDigit).take(6)) }
    fun dismissError() = state.update { it.copy(error = null) }
    fun changeNumber() = state.update { it.copy(otpSent = false, otp = "", error = null) }

    fun sendOtp() {
        state.update { it.copy(loading = true, error = null) }
        launchSafe(onError = { msg -> state.update { it.copy(loading = false, error = msg) } }) {
            container.auth.sendOtp(state.value.fullPhone)
            state.update { it.copy(loading = false, otpSent = true) }
        }
    }

    /** On success the session status changes and AppViewModel moves on automatically. */
    fun verifyOtp() {
        state.update { it.copy(loading = true, error = null) }
        // Most common failure is a wrong or expired code — say that plainly.
        launchSafe(onError = {
            state.update { it.copy(loading = false, error = uiText(Res.string.login_otp_failed)) }
        }) {
            container.auth.verifyOtp(state.value.fullPhone, state.value.otp)
        }
    }
}

/** [generation] changes after each sign-out so the form starts empty for the next person. */
@Composable
fun LoginScreen(generation: Int) {
    val container = LocalAppContainer.current
    val vm = viewModel(key = "login-$generation") { LoginViewModel(container) }
    val state by vm.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        LanguageToggle(Modifier.align(Alignment.End))
        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.login_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        if (!state.otpSent) {
            OutlinedTextField(
                value = state.phoneDigits,
                onValueChange = vm::onPhoneChange,
                label = { Text(stringResource(Res.string.login_mobile_number)) },
                prefix = { Text("${AppConfig.DEFAULT_COUNTRY_CODE} ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            LoadingButton(text = stringResource(Res.string.login_send_otp), loading = state.loading, enabled = state.canSend, onClick = vm::sendOtp)
        } else {
            Text(stringResource(Res.string.login_enter_code, state.fullPhone), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.otp,
                onValueChange = vm::onOtpChange,
                label = { Text(stringResource(Res.string.login_otp)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            LoadingButton(text = stringResource(Res.string.login_verify), loading = state.loading, enabled = state.canVerify, onClick = vm::verifyOtp)
            TextButton(onClick = vm::changeNumber) { Text(stringResource(Res.string.login_change_number)) }
            TextButton(onClick = vm::sendOtp, enabled = !state.loading) { Text(stringResource(Res.string.login_resend)) }
        }

        ErrorBanner(state.error?.asString(), onDismiss = vm::dismissError, modifier = Modifier.padding(top = 8.dp))
    }
}
