@file:OptIn(ExperimentalMaterial3Api::class)

package com.notiz.app.ui.onboarding

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

data class FormState(val loading: Boolean = false, val error: String? = null)

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
            state.value = FormState(error = "UPI ID should look like society@okaxis")
            return
        }
        submit(onDone) { container.societies.createSociety(name, city, flat, vpa, payee) }
    }
}

@Composable
private fun OnboardingColumn(content: @Composable () -> Unit) {
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
        Spacer(Modifier.height(48.dp))
        Text("What's your name?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Neighbours and the committee will see this name.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(80) },
            label = { Text("Full name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        LoadingButton("Continue", loading = state.loading, enabled = name.isNotBlank()) {
            vm.saveProfile(name, onDone)
        }
        ErrorBanner(state.error, onDismiss = vm::dismissError)
    }
}

@Composable
fun SocietySetupScreen(generation: Int, onDone: () -> Unit, onSignOut: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel(key = "onboarding-$generation") { OnboardingViewModel(container) }
    val state by vm.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    OnboardingColumn {
        Spacer(Modifier.height(24.dp))
        Text("Set up your society", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Join with code") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Register society") })
        }
        if (tab == 0) {
            JoinForm(loading = state.loading) { code, flat -> vm.join(code, flat, onDone) }
        } else {
            RegisterForm(loading = state.loading) { name, city, flat, vpa, payee ->
                vm.register(name, city, flat, vpa, payee, onDone)
            }
        }
        ErrorBanner(state.error, onDismiss = vm::dismissError)
        TextButton(onClick = onSignOut) { Text("Sign out") }
    }
}

@Composable
private fun JoinForm(loading: Boolean, onJoin: (code: String, flat: String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var flat by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Ask your secretary for the 6-character invite code.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(6) },
            label = { Text("Invite code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        FlatField(flat) { flat = it }
        LoadingButton("Request to join", loading = loading, enabled = code.length == 6 && flat.isNotBlank()) {
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
            "You'll be the admin. Residents join with the invite code you get next.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(120) },
            label = { Text("Society name") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = city, onValueChange = { city = it.take(60) },
            label = { Text("City") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        FlatField(flat) { flat = it }
        OutlinedTextField(
            value = vpa, onValueChange = { vpa = it.trim().take(100) },
            label = { Text("Society UPI ID (for dues)") }, singleLine = true,
            placeholder = { Text("greenpark@okaxis") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = payee, onValueChange = { payee = it.take(60) },
            label = { Text("Account name on UPI") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LoadingButton("Register society", loading = loading, enabled = name.length >= 2 && flat.isNotBlank()) {
            onRegister(name, city, flat, vpa, payee)
        }
    }
}

@Composable
private fun FlatField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.uppercase().take(20)) },
        label = { Text("Your flat / door number") },
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
        Text("Waiting for approval", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Your request to join ${membership.societies?.name ?: "the society"} as flat ${membership.flatNo} " +
                "has been sent. The secretary or a committee member will approve it soon.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LoadingButton("Check again", loading = false, onClick = onRefresh)
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
    }
}
