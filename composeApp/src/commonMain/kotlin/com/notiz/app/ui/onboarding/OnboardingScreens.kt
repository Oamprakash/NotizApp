@file:OptIn(ExperimentalMaterial3Api::class)

package com.notiz.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.notiz.app.i18n.AppLanguage
import com.notiz.app.i18n.Language
import com.notiz.app.i18n.currentLanguage
import com.notiz.app.ui.components.SegmentedTabs
import com.notiz.app.ui.components.softShadow
import com.notiz.app.ui.theme.LightStatusBarIcons
import com.notiz.app.ui.theme.NotizIcons
import com.notiz.app.ui.theme.Palette
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notiz.app.AppContainer
import com.notiz.app.LocalAppContainer
import com.notiz.app.data.Membership
import com.notiz.app.ui.BaseViewModel
import com.notiz.app.ui.components.ErrorBanner
import com.notiz.app.ui.components.LoadingButton
import com.notiz.app.util.Upi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import com.notiz.app.resources.*
import org.jetbrains.compose.resources.stringResource
import com.notiz.app.i18n.UiText
import com.notiz.app.i18n.asString
import com.notiz.app.i18n.uiText
import com.notiz.app.ui.components.LanguageToggle
import androidx.compose.ui.Alignment

data class FormState(val loading: Boolean = false, val error: UiText? = null)

class OnboardingViewModel(private val container: AppContainer) : BaseViewModel() {
    val state = MutableStateFlow(FormState())

    fun dismissError() = state.update { it.copy(error = null) }

    private fun submit(onSuccess: () -> Unit, block: suspend () -> Unit) {
        state.value = FormState(loading = true)
        launchSafe(onError = { msg -> state.value = FormState(error = msg) }) {
            block()
            state.value = FormState()
            onSuccess()
        }
    }

    fun saveProfile(name: String, onDone: () -> Unit) = submit(onDone) {
        container.auth.saveProfile(name)
    }

    fun join(code: String, flat: String, onDone: () -> Unit) = submit(onDone) {
        container.societies.joinSociety(code, flat)
    }

    fun register(name: String, city: String, flat: String, vpa: String, payee: String, onDone: () -> Unit) {
        if (vpa.isNotBlank() && !Upi.isValidVpa(vpa)) {
            state.value = FormState(error = uiText(Res.string.setup_upi_invalid))
            return
        }
        submit(onDone) { container.societies.createSociety(name, city, flat, vpa, payee) }
    }
}

/** Moves the next element up by [by] so the white card overlaps the teal band. */
private fun Modifier.pullUp(by: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val px = by.roundToPx()
    layout(placeable.width, (placeable.height - px).coerceAtLeast(0)) { placeable.place(0, -px) }
}

/**
 * Onboarding frame matching Login and Home: teal band with the logo, language switch, an icon and
 * the step's title, then a white card with the form overlapping it.
 */
@Composable
private fun OnboardingFrame(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    LightStatusBarIcons()
    Column(
        Modifier.fillMaxSize().background(Palette.ground).imePadding().verticalScroll(rememberScrollState()),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Palette.teal, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(34.dp).background(Color.White, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Text("N", color = Palette.teal, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                }
                Text(stringResource(Res.string.app_name), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, modifier = Modifier.weight(1f))
                BandLanguageToggle()
            }
            Box(Modifier.size(52.dp).background(Palette.tealMid, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = Color.White, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
                Text(subtitle, color = Palette.tealOnDark, fontSize = 15.sp, lineHeight = 22.sp)
            }
        }
        Column(
            Modifier
                .pullUp(40.dp)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .softShadow(RoundedCornerShape(24.dp), elevation = 8.dp)
                .background(Color.White, RoundedCornerShape(24.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}

@Composable
private fun BandLanguageToggle() {
    val current = currentLanguage()
    val other = if (current == Language.TAMIL) Language.ENGLISH else Language.TAMIL
    Box(
        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).border(1.dp, Color(0xFF5FA18F), RoundedCornerShape(17.dp))
            .clickable { AppLanguage.select(other) }.padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(other.nativeName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
}

@Composable
fun ProfileScreen(generation: Int, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel(key = "onboarding-$generation") { OnboardingViewModel(container) }
    val state by vm.state.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }

    OnboardingFrame(NotizIcons.Users, stringResource(Res.string.profile_title), stringResource(Res.string.profile_subtitle)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(80) },
            label = { Text(stringResource(Res.string.profile_full_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        LoadingButton(stringResource(Res.string.action_continue), loading = state.loading, enabled = name.isNotBlank()) {
            vm.saveProfile(name, onDone)
        }
        ErrorBanner(state.error?.asString(), onDismiss = vm::dismissError)
    }
}

@Composable
fun SocietySetupScreen(generation: Int, onDone: () -> Unit, onSignOut: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel(key = "onboarding-$generation") { OnboardingViewModel(container) }
    val state by vm.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    OnboardingFrame(NotizIcons.Home, stringResource(Res.string.setup_title), stringResource(Res.string.setup_subtitle)) {
        SegmentedTabs(
            options = listOf(stringResource(Res.string.setup_tab_join), stringResource(Res.string.setup_tab_register)),
            selected = tab,
            onSelect = { tab = it },
        )
        if (tab == 0) {
            JoinForm(loading = state.loading) { code, flat -> vm.join(code, flat, onDone) }
        } else {
            RegisterForm(loading = state.loading) { name, city, flat, vpa, payee ->
                vm.register(name, city, flat, vpa, payee, onDone)
            }
        }
        ErrorBanner(state.error?.asString(), onDismiss = vm::dismissError)
        TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(Res.string.action_sign_out), color = Palette.muted)
        }
    }
}

@Composable
private fun JoinForm(loading: Boolean, onJoin: (code: String, flat: String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var flat by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.setup_join_hint), color = Palette.muted, fontSize = 14.sp)
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(6) },
            label = { Text(stringResource(Res.string.setup_invite_code)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(letterSpacing = 6.sp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        FlatField(flat) { flat = it }
        LoadingButton(stringResource(Res.string.setup_request_join), loading = loading, enabled = code.length == 6 && flat.isNotBlank()) {
            onJoin(code, flat)
        }
    }
}
@Composable
private fun RegisterForm(
    loading: Boolean,
    onRegister: (name: String, city: String, flat: String, vpa: String, payee: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var flat by rememberSaveable { mutableStateOf("") }
    var vpa by rememberSaveable { mutableStateOf("") }
    var payee by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.setup_register_hint), color = Palette.muted, fontSize = 14.sp)
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(120) },
            label = { Text(stringResource(Res.string.setup_society_name)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = city, onValueChange = { city = it.take(60) },
            label = { Text(stringResource(Res.string.setup_city)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        FlatField(flat) { flat = it }
        OutlinedTextField(
            value = vpa, onValueChange = { vpa = it.trim().take(100) },
            label = { Text(stringResource(Res.string.setup_upi_id)) }, singleLine = true,
            placeholder = { Text("greenpark@okaxis") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = payee, onValueChange = { payee = it.take(60) },
            label = { Text(stringResource(Res.string.setup_upi_name)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LoadingButton(stringResource(Res.string.setup_tab_register), loading = loading, enabled = name.length >= 2 && flat.isNotBlank()) {
            onRegister(name, city, flat, vpa, payee)
        }
    }
}

@Composable
private fun FlatField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.uppercase().take(20)) },
        label = { Text(stringResource(Res.string.setup_flat_number)) },
        placeholder = { Text("B-202") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun PendingApprovalScreen(membership: Membership, onRefresh: () -> Unit, onSignOut: () -> Unit) {
    OnboardingFrame(
        NotizIcons.Clock,
        stringResource(Res.string.pending_title),
        stringResource(
            Res.string.pending_body,
            membership.societies?.name ?: stringResource(Res.string.pending_the_society),
            membership.flatNo,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(Palette.amberSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(NotizIcons.Bell, contentDescription = null, tint = Palette.amberText, modifier = Modifier.size(20.dp))
            }
            Text(stringResource(Res.string.pending_notify_hint), color = Palette.inkSoft, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
        }
        LoadingButton(stringResource(Res.string.pending_check_again), loading = false, onClick = onRefresh)
        TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(Res.string.action_sign_out), color = Palette.muted)
        }
    }
}
