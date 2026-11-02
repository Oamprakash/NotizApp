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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.notiz.app.ui.components.DetailTopBar
import com.notiz.app.ui.components.NotizCard
import com.notiz.app.ui.theme.NotizIcons
import com.notiz.app.ui.theme.Palette
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
        containerColor = Palette.ground,
        topBar = { DetailTopBar(stringResource(Res.string.pay_title), onBack) },
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
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StatusHero(state.status)
                Text(stringResource(Res.string.pay_intro), color = Palette.inkSoft, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 4.dp))
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
                    TextButton(onClick = { confirmRemove = true }, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(stringResource(Res.string.pay_remove), color = Palette.red, fontWeight = FontWeight.Bold)
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

/** Big status card at the top: are residents able to pay online right now? */
@Composable
private fun StatusHero(status: GatewayStatus) {
    val on = status.configured && status.onlineEnabled
    Row(
        Modifier.fillMaxWidth().background(if (on) Palette.teal else Color.White, RoundedCornerShape(22.dp)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(48.dp).background(if (on) Palette.tealMid else Palette.amberSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (on) NotizIcons.Check else NotizIcons.Rupee, contentDescription = null, tint = if (on) Color.White else Palette.amberText, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (on) Res.string.pay_hero_on else Res.string.pay_hero_off),
                color = if (on) Color.White else Palette.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                stringResource(if (on) Res.string.pay_hero_on_sub else Res.string.pay_hero_off_sub),
                color = if (on) Palette.tealOnDark else Palette.muted,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun StepCard(number: Int, title: String, done: Boolean = false, content: @Composable () -> Unit) {
    NotizCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(28.dp).background(if (done) Palette.teal else Palette.tealSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(NotizIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                } else {
                    Text(number.toString(), color = Palette.teal, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun KeysStep(status: GatewayStatus, busy: Boolean, onSave: (keyId: String, secret: String, onSaved: () -> Unit) -> Unit) {
    var keyId by remember(status.keyId) { mutableStateOf(status.keyId.orEmpty()) }
    var secret by remember { mutableStateOf("") }
    StepCard(1, stringResource(Res.string.pay_step_keys), done = status.configured) {
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Palette.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.fillMaxWidth().background(Palette.chip, RoundedCornerShape(12.dp)).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionContainer(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(value, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Palette.ink)
            }
            TextButton(onClick = { onCopy(value) }) { Text(stringResource(Res.string.pay_copy), color = Palette.teal, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun TurnOnStep(status: GatewayStatus, busy: Boolean, onToggle: (Boolean) -> Unit) {
    StepCard(3, stringResource(Res.string.pay_step_turn_on), done = status.onlineEnabled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.pay_accept_online), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(Res.string.pay_accept_online_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = status.onlineEnabled,
                onCheckedChange = onToggle,
                enabled = !busy,
                colors = SwitchDefaults.colors(checkedTrackColor = Palette.teal),
            )
        }
        if (status.testMode) {
            Column(Modifier.fillMaxWidth().background(Palette.amberSoft, RoundedCornerShape(14.dp))) {
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
