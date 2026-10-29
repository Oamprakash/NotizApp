@file:OptIn(ExperimentalMaterial3Api::class)

package com.notiz.app.ui.onboarding

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

@Composable
private fun OnboardingColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
fun ProfileScreen(generation: Int, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel(key = "onboarding-$generation") { OnboardingViewModel(container) }
    val state by vm.state.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }

    OnboardingColumn {
        LanguageToggle(Modifier.align(Alignment.End))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(Res.string.profile_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            stringResource(Res.string.profile_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

    OnboardingColumn {
        LanguageToggle(Modifier.align(Alignment.End))
        Text(stringResource(Res.string.setup_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(Res.string.setup_tab_join)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(Res.string.setup_tab_register)) })
        }
        if (tab == 0) {
            JoinForm(loading = state.loading) { code, flat -> vm.join(code, flat, onDone) }
        } else {
            RegisterForm(loading = state.loading) { name, city, flat, vpa, payee ->
                vm.register(name, city, flat, vpa, payee, onDone)
            }
        }
        ErrorBanner(state.error?.asString(), onDismiss = vm::dismissError)
        TextButton(onClick = onSignOut) { Text(stringResource(Res.string.action_sign_out)) }
    }
}

@Composable
private fun JoinForm(loading: Boolean, onJoin: (code: String, flat: String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var flat by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(Res.string.setup_join_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(6) },
            label = { Text(stringResource(Res.string.setup_invite_code)) },
            singleLine = true,
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
        Text(
            stringResource(Res.string.setup_register_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    OnboardingColumn {
        Spacer(Modifier.height(64.dp))
        Text(stringResource(Res.string.pending_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            stringResource(
                Res.string.pending_body,
                membership.societies?.name ?: stringResource(Res.string.pending_the_society),
                membership.flatNo,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LoadingButton(stringResource(Res.string.pending_check_again), loading = false, onClick = onRefresh)
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.action_sign_out)) }
    }
}
