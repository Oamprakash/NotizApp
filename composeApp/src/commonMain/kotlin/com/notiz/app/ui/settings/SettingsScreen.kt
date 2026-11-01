@file:OptIn(ExperimentalMaterial3Api::class)

package com.notiz.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notiz.app.AppConfig
import com.notiz.app.AppContainer
import com.notiz.app.LocalAppContainer
import com.notiz.app.i18n.UiText
import com.notiz.app.i18n.asString
import com.notiz.app.i18n.uiText
import com.notiz.app.resources.*
import com.notiz.app.ui.BaseViewModel
import com.notiz.app.ui.SessionContext
import com.notiz.app.ui.components.ErrorBanner
import com.notiz.app.ui.components.InfoBanner
import com.notiz.app.ui.components.LanguageToggle
import com.notiz.app.util.Upi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.stringResource

data class SettingsUiState(
    val name: String = "",
    val societyName: String = "",
    val city: String = "",
    val upiVpa: String = "",
    val upiPayeeName: String = "",
    val inviteCode: String = "",
    val busy: Boolean = false,
    val error: UiText? = null,
    val info: UiText? = null,
)

class SettingsViewModel(
    private val container: AppContainer,
    private val session: SessionContext,
) : BaseViewModel() {
    val state = MutableStateFlow(
        SettingsUiState(
            societyName = session.society.name,
            city = session.society.city,
            upiVpa = session.society.upiVpa,
            upiPayeeName = session.society.upiPayeeName,
            inviteCode = session.society.inviteCode,
        ),
    )

    init {
        launchSafe(onError = {}) {
            container.auth.myProfile()?.let { p -> state.update { it.copy(name = p.fullName) } }
        }
    }

    fun edit(transform: SettingsUiState.() -> SettingsUiState) = state.update { it.transform() }
    fun dismiss() = state.update { it.copy(error = null, info = null) }

    private fun act(block: suspend () -> UiText?) {
        state.update { it.copy(busy = true, error = null, info = null) }
        launchSafe(onError = { msg -> state.update { it.copy(busy = false, error = msg) } }) {
            val info = block()
            state.update { it.copy(busy = false, info = info) }
        }
    }

    fun saveName() = act {
        container.auth.saveProfile(state.value.name)
        uiText(Res.string.settings_saved)
    }

    fun saveSociety(onSaved: () -> Unit) = act {
        val s = state.value
        val saved = container.societies.updateSettings(session.societyId, s.societyName, s.city, s.upiVpa, s.upiPayeeName)
        state.update {
            it.copy(societyName = saved.name, city = saved.city, upiVpa = saved.upiVpa, upiPayeeName = saved.upiPayeeName)
        }
        onSaved()
        uiText(Res.string.settings_saved)
    }

    fun newInviteCode(onSaved: () -> Unit) = act {
        val code = container.societies.regenerateInviteCode(session.societyId)
        state.update { it.copy(inviteCode = code) }
        onSaved()
        uiText(Res.string.settings_new_code_done, code)
    }

    fun leave(onDone: () -> Unit) = act {
        container.societies.leaveSociety(session.societyId)
        onDone()
        null
    }

    fun deleteAccount() = act {
        container.push.onSigningOut()
        try {
            container.auth.deleteAccount() // clears the session → the app returns to login
        } catch (e: Throwable) {
            container.push.onProfileReady() // still signed in: keep getting notifications
            throw e
        }
        null
    }
}

/**
 * Profile, language, society details (admins), invite code, leave society, delete account and
 * sign out. [onSessionChanged] quietly reloads the signed-in state (after leaving or editing the society).
 */
@Composable
fun SettingsScreen(
    session: SessionContext,
    onBack: () -> Unit,
    onSessionChanged: () -> Unit,
    onSignOut: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm = viewModel { SettingsViewModel(container, session) }
    val state by vm.state.collectAsState()
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmNewCode by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ErrorBanner(state.error?.asString(), onDismiss = vm::dismiss)
            InfoBanner(state.info?.asString(), onDismiss = vm::dismiss)

            Section(stringResource(Res.string.settings_profile)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { v -> vm.edit { copy(name = v.take(60)) } },
                    label = { Text(stringResource(Res.string.profile_full_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = vm::saveName, enabled = state.name.isNotBlank() && !state.busy) {
                    Text(stringResource(Res.string.settings_save))
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.settings_language), modifier = Modifier.weight(1f))
                    LanguageToggle()
                }
            }

            if (session.isAdmin) {
                Section(stringResource(Res.string.settings_society)) {
                    Hint(stringResource(Res.string.settings_society_hint))
                    Field(state.societyName, Res.string.setup_society_name) { v -> vm.edit { copy(societyName = v.take(80)) } }
                    Field(state.city, Res.string.setup_city) { v -> vm.edit { copy(city = v.take(60)) } }
                    val vpaOk = state.upiVpa.isBlank() || Upi.isValidVpa(state.upiVpa)
                    OutlinedTextField(
                        value = state.upiVpa,
                        onValueChange = { v -> vm.edit { copy(upiVpa = v.trim().take(100)) } },
                        label = { Text(stringResource(Res.string.setup_upi_id)) },
                        isError = !vpaOk,
                        supportingText = if (vpaOk) null else { { Text(stringResource(Res.string.setup_upi_invalid)) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Field(state.upiPayeeName, Res.string.setup_upi_name) { v -> vm.edit { copy(upiPayeeName = v.take(60)) } }
                    Button(
                        onClick = { vm.saveSociety(onSaved = onSessionChanged) },
                        enabled = state.societyName.trim().length >= 2 && vpaOk && !state.busy,
                    ) { Text(stringResource(Res.string.action_save_changes)) }
                }

                Section(stringResource(Res.string.settings_invite_title)) {
                    Text(state.inviteCode, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Hint(stringResource(Res.string.settings_invite_hint))
                    OutlinedButton(onClick = { confirmNewCode = true }, enabled = !state.busy) {
                        Text(stringResource(Res.string.settings_new_code))
                    }
                }
            }

            Section(stringResource(Res.string.settings_account)) {
                OutlinedButton(onClick = onSignOut, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.action_sign_out))
                }
                OutlinedButton(onClick = { confirmLeave = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.settings_leave))
                }
                HorizontalDivider()
                Hint(stringResource(Res.string.settings_delete_hint))
                Button(
                    onClick = { confirmDelete = true },
                    enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(Res.string.settings_delete)) }
                TextButton(onClick = { runCatching { uriHandler.openUri(AppConfig.PRIVACY_POLICY_URL) } }) {
                    Text(stringResource(Res.string.settings_privacy))
                }
            }
        }
    }

    if (confirmNewCode) {
        ConfirmDialog(
            text = stringResource(Res.string.settings_new_code_confirm, state.inviteCode),
            confirm = stringResource(Res.string.settings_new_code),
            onDismiss = { confirmNewCode = false },
            onConfirm = { confirmNewCode = false; vm.newInviteCode(onSaved = onSessionChanged) },
        )
    }
    if (confirmLeave) {
        ConfirmDialog(
            text = stringResource(Res.string.settings_leave_confirm, state.societyName),
            confirm = stringResource(Res.string.settings_leave),
            onDismiss = { confirmLeave = false },
            onConfirm = { confirmLeave = false; vm.leave(onDone = onSessionChanged) },
        )
    }
    if (confirmDelete) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.settings_delete)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.settings_delete_confirm))
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it.take(10) },
                        label = { Text(stringResource(Res.string.settings_delete_type)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; vm.deleteAccount() },
                    enabled = typed.trim().equals("DELETE", ignoreCase = true),
                ) { Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.action_keep)) } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Field(value: String, label: org.jetbrains.compose.resources.StringResource, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ConfirmDialog(text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_keep)) } },
    )
}
