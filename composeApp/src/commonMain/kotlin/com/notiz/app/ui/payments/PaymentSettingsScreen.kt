@file:OptIn(ExperimentalMaterial3Api::class)

package com.notiz.app.ui.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notiz.app.AppContainer
import com.notiz.app.LocalAppContainer
import com.notiz.app.data.GatewayStatus
import com.notiz.app.i18n.UiText
import com.notiz.app.i18n.asString
import com.notiz.app.i18n.uiText
import com.notiz.app.resources.*
import com.notiz.app.ui.BaseViewModel
import com.notiz.app.ui.SessionContext
import com.notiz.app.ui.components.ErrorBanner
import com.notiz.app.ui.components.ErrorBox
import com.notiz.app.ui.components.InfoBanner
import com.notiz.app.ui.components.LoadingBox
import com.notiz.app.ui.components.LoadingButton
import com.notiz.app.ui.components.StatusPill
import com.notiz.app.ui.theme.StatusColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.stringResource

data class PaymentSettingsUiState(
    val loading: Boolean = true,
    val status: GatewayStatus = GatewayStatus(),
    val loadError: UiText? = null,
    val error: UiText? = null,
    val info: UiText? = null,
    val busy: Boolean = false,
)

class PaymentSettingsViewModel(
    private val container: AppContainer,
    private val session: SessionContext,
) : BaseViewModel() {
    val state = MutableStateFlow(PaymentSettingsUiState())
    val webhookUrl = container.payments.webhookUrl(session.societyId)

    init { load() }

    fun load() {
        state.update { it.copy(loadError = null) }
        launchSafe(onError = { msg -> state.update { it.copy(loading = false, loadError = msg) } }) {
            val status = container.payments.gatewayStatus(session.societyId)
            state.update { it.copy(loading = false, status = status) }
        }
    }

    private fun act(info: UiText?, block: suspend () -> Unit) {
        state.update { it.copy(busy = true, error = null, info = null) }
        launchSafe(onError = { msg -> state.update { it.copy(busy = false, error = msg) } }) {
            block()
            val status = container.payments.gatewayStatus(session.societyId)
            state.update { it.copy(busy = false, status = status, info = info) }
        }
    }

    fun saveKeys(keyId: String, keySecret: String, onSaved: () -> Unit) =
        act(uiText(Res.string.pay_keys_saved)) {
            container.payments.saveKeys(session.societyId, keyId, keySecret)
            onSaved()
        }

    fun setOnline(enabled: Boolean) =
        act(uiText(if (enabled) Res.string.pay_now_on else Res.string.pay_now_off)) {
            container.payments.setOnlineEnabled(session.societyId, enabled)
        }

    fun removeKeys() = act(uiText(Res.string.pay_keys_removed)) {
        container.payments.removeKeys(session.societyId)
    }

    fun showInfo(text: UiText) = state.update { it.copy(info = text) }
    fun dismiss() = state.update { it.copy(error = null, info = null) }
}

@Composable
fun PaymentSettingsScreen(session: SessionContext, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { PaymentSettingsViewModel(container, session) }
    val state by vm.state.collectAsState()
    var confirmRemove by remember { mutableStateOf(false) }

    @Suppress("DEPRECATION") // LocalClipboard's suspend API isn't needed for a one-off copy
    val clipboard = LocalClipboardManager.current
    val copied = uiText(Res.string.pay_copied)
    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        vm.showInfo(copied)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.pay_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.loadError != null -> ErrorBox(state.loadError!!.asString(), onRetry = vm::load, modifier = Modifier.padding(padding))
            !session.isAdmin -> ErrorBox(stringResource(Res.string.error_admins_only), onRetry = onBack, modifier = Modifier.padding(padding))
            else -> Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(Res.string.pay_intro), style = MaterialTheme.typography.bodyMedium)
                ErrorBanner(state.error?.asString(), onDismiss = vm::dismiss)
                InfoBanner(state.info?.asString(), onDismiss = vm::dismiss)

                KeysStep(state.status, state.busy) { keyId, secret, onSaved -> vm.saveKeys(keyId, secret, onSaved) }

                if (state.status.configured) {
                    WebhookStep(
                        url = vm.webhookUrl,
                        secret = state.status.webhookSecret.orEmpty(),
                        onCopy = ::copy,
                    )
                    TurnOnStep(state.status, state.busy, onToggle = vm::setOnline)
                    TextButton(onClick = { confirmRemove = true }, enabled = !state.busy) {
                        Text(stringResource(Res.string.pay_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(Res.string.pay_remove_title)) },
            text = { Text(stringResource(Res.string.pay_remove_body)) },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; vm.removeKeys() }) { Text(stringResource(Res.string.pay_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@Composable
private fun StepCard(number: Int, title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(Res.string.pay_step, number, title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun KeysStep(status: GatewayStatus, busy: Boolean, onSave: (keyId: String, secret: String, onSaved: () -> Unit) -> Unit) {
    var keyId by remember(status.keyId) { mutableStateOf(status.keyId.orEmpty()) }
    var secret by remember { mutableStateOf("") }
    StepCard(1, stringResource(Res.string.pay_step_keys)) {
        Text(stringResource(Res.string.pay_keys_help), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = keyId,
            onValueChange = { keyId = it.trim().take(40) },
            label = { Text(stringResource(Res.string.pay_key_id)) },
            placeholder = { Text("rzp_live_…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = secret,
            onValueChange = { secret = it.trim().take(80) },
            label = { Text(stringResource(Res.string.pay_key_secret)) },
            placeholder = { if (status.configured) Text(stringResource(Res.string.pay_secret_saved)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        LoadingButton(
            stringResource(Res.string.pay_save_keys),
            loading = busy,
            enabled = keyId.startsWith("rzp_") && (secret.isNotBlank() || status.configured),
        ) {
            onSave(keyId, secret) { secret = "" }
        }
    }
}

@Composable
private fun WebhookStep(url: String, secret: String, onCopy: (String) -> Unit) {
    StepCard(2, stringResource(Res.string.pay_step_webhook)) {
        Text(stringResource(Res.string.pay_webhook_help), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        CopyField(stringResource(Res.string.pay_webhook_url), url, onCopy)
        CopyField(stringResource(Res.string.pay_webhook_secret), secret, onCopy)
        CopyField(stringResource(Res.string.pay_webhook_event), "payment_link.paid", onCopy)
    }
}

@Composable
private fun CopyField(label: String, value: String, onCopy: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { onCopy(value) }) { Text(stringResource(Res.string.pay_copy)) }
        }
    }
}

@Composable
private fun TurnOnStep(status: GatewayStatus, busy: Boolean, onToggle: (Boolean) -> Unit) {
    StepCard(3, stringResource(Res.string.pay_step_turn_on)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.pay_accept_online), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(Res.string.pay_accept_online_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = status.onlineEnabled, onCheckedChange = onToggle, enabled = !busy)
        }
        if (status.testMode) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(12.dp)) {
                    StatusPill(stringResource(Res.string.pay_test_mode), StatusColors.warning)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(Res.string.pay_test_mode_help), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(
            stringResource(Res.string.pay_fees_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
