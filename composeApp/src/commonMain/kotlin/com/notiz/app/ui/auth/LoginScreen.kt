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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.notiz.app.i18n.AppLanguage
import com.notiz.app.i18n.Language
import com.notiz.app.i18n.currentLanguage
import com.notiz.app.ui.theme.NotizIcons
import com.notiz.app.ui.theme.Palette
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
            .background(Palette.ground)
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        BrandPanel()
        Column(Modifier.padding(24.dp)) {
            Text(stringResource(Res.string.login_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(Res.string.login_subtitle), color = Palette.muted, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))

            if (!state.otpSent) {
                OutlinedTextField(
                    value = state.phoneDigits,
                    onValueChange = vm::onPhoneChange,
                    label = { Text(stringResource(Res.string.login_mobile_number)) },
                    prefix = { Text("${AppConfig.DEFAULT_COUNTRY_CODE} ", fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
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
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.login_new_here), color = Palette.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Teal header with the logo, tagline and what Notiz does (design: Login). */
@Composable
private fun BrandPanel() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.teal, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .safeDrawingPadding()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).background(Color.White, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Text("N", color = Palette.teal, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            }
            Text(stringResource(Res.string.app_name), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            val current = currentLanguage()
            val other = if (current == Language.TAMIL) Language.ENGLISH else Language.TAMIL
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, Color(0xFF5FA18F), RoundedCornerShape(18.dp))
                    .clickable { AppLanguage.select(other) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(other.nativeName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(Res.string.login_hero), color = Color.White, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
            Text(stringResource(Res.string.login_tagline), color = Palette.tealOnDark, fontSize = 15.sp, lineHeight = 22.sp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeatureChip(NotizIcons.Notice, stringResource(Res.string.tab_notices))
            FeatureChip(NotizIcons.Rupee, stringResource(Res.string.tab_dues))
            FeatureChip(NotizIcons.Wrench, stringResource(Res.string.tab_help))
            FeatureChip(NotizIcons.Poll, stringResource(Res.string.tab_polls))
        }
    }
}

@Composable
private fun FeatureChip(icon: ImageVector, label: String) {
    Row(
        Modifier.background(Palette.tealMid, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
