@file:OptIn(ExperimentalMaterial3Api::class)

package com.notiz.app.ui.members

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notiz.app.AppContainer
import com.notiz.app.LocalAppContainer
import com.notiz.app.data.Membership
import com.notiz.app.push.OnPushUpdate
import com.notiz.app.push.PushTypes
import com.notiz.app.ui.BaseViewModel
import com.notiz.app.ui.SessionContext
import com.notiz.app.ui.components.ErrorBanner
import com.notiz.app.ui.components.ErrorBox
import com.notiz.app.ui.components.InfoBanner
import com.notiz.app.ui.components.LoadingBox
import com.notiz.app.ui.components.StatusPill
import com.notiz.app.util.urlEncode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import com.notiz.app.resources.*
import org.jetbrains.compose.resources.stringResource
import com.notiz.app.i18n.UiText
import com.notiz.app.i18n.asString
import com.notiz.app.i18n.uiText

data class MembersUiState(
    val loading: Boolean = true,
    val members: List<Membership> = emptyList(),
    val loadError: UiText? = null,
    val actionError: UiText? = null,
    val info: UiText? = null,
    val busy: Boolean = false,
) {
    val pending get() = members.filter { it.isPending }
    val approved get() = members.filter { it.isApproved }
}

class MembersViewModel(
    private val container: AppContainer,
    private val session: SessionContext,
) : BaseViewModel() {
    val state = MutableStateFlow(MembersUiState())

    init { load() }

    fun load() {
        state.update { it.copy(loadError = null) }
        launchSafe(onError = { msg -> state.update { it.copy(loading = false, loadError = msg) } }) {
            val members = container.societies.members(session.societyId)
            state.update { it.copy(loading = false, members = members) }
        }
    }

    private fun act(block: suspend () -> Unit) {
        state.update { it.copy(busy = true, actionError = null) }
        launchSafe(onError = { msg -> state.update { it.copy(busy = false, actionError = msg) } }) {
            block()
            state.update { it.copy(busy = false) }
            load()
        }
    }

    fun approve(m: Membership) = act { container.societies.setMembershipStatus(m.id, "approved") }
    fun reject(m: Membership) = act { container.societies.setMembershipStatus(m.id, "rejected") }
    fun toggleAdmin(m: Membership) = act {
        container.societies.setMembershipRole(m.id, if (m.isAdmin) "resident" else "admin")
    }

    fun showInfo(text: UiText) = state.update { it.copy(info = text) }
    fun dismiss() = state.update { it.copy(actionError = null, info = null) }
}

@Composable
fun MembersScreen(session: SessionContext, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { MembersViewModel(container, session) }
    val state by vm.state.collectAsState()
    OnPushUpdate(PushTypes.MEMBER) { vm.load() }
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (session.isAdmin) Res.string.members_title else Res.string.members_neighbours)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back)) }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.loadError != null && state.members.isEmpty() ->
                ErrorBox(state.loadError!!.asString(), onRetry = vm::load, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                item {
                    ErrorBanner(state.actionError?.asString(), onDismiss = vm::dismiss)
                    InfoBanner(state.info?.asString(), onDismiss = vm::dismiss)
                }
                if (session.isAdmin) {
                    item {
                        InviteCard(code = session.society.inviteCode, societyName = session.society.name) { text ->
                            // wa.me opens WhatsApp with the message ready to send to the society group
                            runCatching { uriHandler.openUri("https://wa.me/?text=${urlEncode(text)}") }
                                .onFailure { vm.showInfo(uiText(Res.string.members_whatsapp_failed, session.society.inviteCode)) }
                        }
                    }
                    if (state.pending.isNotEmpty()) {
                        item { SectionTitle(stringResource(Res.string.members_waiting, state.pending.size)) }
                        items(state.pending, key = { it.id }) { m ->
                            MemberRow(m) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { vm.approve(m) }, enabled = !state.busy) { Text(stringResource(Res.string.members_approve)) }
                                    OutlinedButton(onClick = { vm.reject(m) }, enabled = !state.busy) { Text(stringResource(Res.string.members_reject)) }
                                }
                            }
                        }
                    }
                }
                item { SectionTitle(stringResource(Res.string.members_count, state.approved.size)) }
                items(state.approved, key = { it.id }) { m ->
                    MemberRow(m) {
                        if (session.isAdmin && m.userId != session.userId) {
                            TextButton(onClick = { vm.toggleAdmin(m) }, enabled = !state.busy) {
                                Text(stringResource(if (m.isAdmin) Res.string.members_remove_admin else Res.string.members_make_admin))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun InviteCard(code: String, societyName: String, onShare: (String) -> Unit) {
    val message = stringResource(Res.string.members_invite_message, societyName, code)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(Res.string.setup_invite_code), style = MaterialTheme.typography.labelLarge)
            Text(code, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.members_invite_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onShare(message) }) { Text(stringResource(Res.string.members_share_whatsapp)) }
        }
    }
}

@Composable
private fun MemberRow(m: Membership, actions: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.profiles?.fullName ?: stringResource(Res.string.members_resident), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(Res.string.home_flat, m.flatNo),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (m.isAdmin) StatusPill(stringResource(Res.string.members_admin), MaterialTheme.colorScheme.primary)
            }
            actions()
        }
    }
}
