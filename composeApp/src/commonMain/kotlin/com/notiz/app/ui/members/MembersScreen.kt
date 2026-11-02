@file:OptIn(ExperimentalMaterial3Api::class)

package com.notiz.app.ui.members

import com.notiz.app.ui.components.NotizSheet
import com.notiz.app.ui.components.NotizMenu
import com.notiz.app.ui.components.NotizMenuItem
import com.notiz.app.ui.components.NotizTextButton
import com.notiz.app.ui.components.NotizTextField
import com.notiz.app.ui.components.NotizDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.notiz.app.ui.components.Avatar
import com.notiz.app.ui.components.Chip
import com.notiz.app.ui.components.DetailTopBar
import com.notiz.app.ui.components.GroupCard
import com.notiz.app.ui.components.NotizCard
import com.notiz.app.ui.components.PrimaryButton
import com.notiz.app.ui.components.SecondaryButton
import com.notiz.app.ui.theme.NotizIcons
import com.notiz.app.ui.theme.Palette
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

    /** Changing your own flat also updates the signed-in session via [onOwnFlatChanged]. */
    fun changeFlat(m: Membership, flatNo: String, onOwnFlatChanged: () -> Unit) {
        state.update { it.copy(busy = true, actionError = null, info = null) }
        launchSafe(onError = { msg -> state.update { it.copy(busy = false, actionError = msg) } }) {
            container.societies.setMemberFlat(m.id, flatNo)
            state.update { it.copy(busy = false, info = uiText(Res.string.members_flat_changed, flatNo.trim())) }
            if (m.userId == session.userId) onOwnFlatChanged()
            load()
        }
    }

    fun remove(m: Membership) {
        state.update { it.copy(busy = true, actionError = null, info = null) }
        launchSafe(onError = { msg -> state.update { it.copy(busy = false, actionError = msg) } }) {
            container.societies.removeMember(m.id)
            state.update { it.copy(busy = false, info = uiText(Res.string.members_removed)) }
            load()
        }
    }

    fun toggleMute(m: Membership) = act { container.chat.setMuted(session.societyId, m.userId, !m.chatMuted) }

    fun showInfo(text: UiText) = state.update { it.copy(info = text) }
    fun dismiss() = state.update { it.copy(actionError = null, info = null) }
}

@Composable
fun MembersScreen(session: SessionContext, onBack: () -> Unit, onSessionChanged: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { MembersViewModel(container, session) }
    val state by vm.state.collectAsState()
    OnPushUpdate(PushTypes.MEMBER) { vm.load() }
    val uriHandler = LocalUriHandler.current
    var editingFlat by remember { mutableStateOf<Membership?>(null) }
    var removing by remember { mutableStateOf<Membership?>(null) }

    Scaffold(
        containerColor = Palette.ground,
        topBar = {
            DetailTopBar(stringResource(if (session.isAdmin) Res.string.members_title else Res.string.members_neighbours), onBack)
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.loadError != null && state.members.isEmpty() ->
                ErrorBox(state.loadError!!.asString(), onRetry = vm::load, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
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
                            NotizCard {
                                MemberLine(m)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    PrimaryButton(stringResource(Res.string.members_approve), onClick = { vm.approve(m) }, enabled = !state.busy, height = 44.dp, modifier = Modifier.weight(1f))
                                    SecondaryButton(stringResource(Res.string.members_reject), onClick = { vm.reject(m) }, enabled = !state.busy, height = 44.dp, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                item { SectionTitle(stringResource(Res.string.members_count, state.approved.size)) }
                item {
                    GroupCard {
                        state.approved.forEachIndexed { i, m ->
                            Column {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MemberLine(m, Modifier.weight(1f))
                                    if (session.isAdmin) {
                                        MemberMenu(
                                            m = m,
                                            isMe = m.userId == session.userId,
                                            busy = state.busy,
                                            onToggleAdmin = { vm.toggleAdmin(m) },
                                            onChangeFlat = { editingFlat = m },
                                            onToggleMute = { vm.toggleMute(m) },
                                            onRemove = { removing = m },
                                        )
                                    }
                                }
                                if (i < state.approved.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 66.dp).height(1.dp).background(Palette.lineSoft))
                            }
                        }
                    }
                }
            }
        }
    }

    editingFlat?.let { m ->
        var flat by remember(m.id) { mutableStateOf(m.flatNo) }
        NotizSheet(stringResource(Res.string.members_flat_title), onDismissRequest = { editingFlat = null }) {
            Row(
                Modifier.fillMaxWidth().background(Palette.chip, RoundedCornerShape(16.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { MemberLine(m) }
            NotizTextField(
                value = flat,
                onValueChange = { flat = it.uppercase().take(20) },
                label = { Text(stringResource(Res.string.members_new_flat)) },
                placeholder = { Text("B-202") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(Res.string.members_flat_hint), color = Palette.muted, fontSize = 12.sp)
            PrimaryButton(
                stringResource(Res.string.action_save_changes),
                onClick = {
                    editingFlat = null
                    vm.changeFlat(m, flat, onOwnFlatChanged = onSessionChanged)
                },
                enabled = flat.isNotBlank() && flat.trim() != m.flatNo,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                height = 52.dp,
            )
        }
    }
    removing?.let { m ->
        NotizDialog(
            onDismissRequest = { removing = null },
            text = {
                Text(
                    stringResource(
                        Res.string.members_remove_confirm,
                        m.profiles?.fullName ?: stringResource(Res.string.members_resident),
                        m.flatNo,
                    ),
                )
            },
            confirmButton = {
                NotizTextButton(onClick = { removing = null; vm.remove(m) }) {
                    Text(stringResource(Res.string.members_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { NotizTextButton(onClick = { removing = null }) { Text(stringResource(Res.string.action_keep)) } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

/** Teal card with the invite code and a WhatsApp share button (matches the Home band). */
@Composable
private fun InviteCard(code: String, societyName: String, onShare: (String) -> Unit) {
    val message = stringResource(Res.string.members_invite_message, societyName, code)
    Column(
        Modifier.fillMaxWidth().background(Palette.teal, RoundedCornerShape(22.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(Res.string.setup_invite_code), color = Palette.tealOnDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(code, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp)
        Text(stringResource(Res.string.members_invite_hint), color = Palette.tealOnDark, fontSize = 14.sp, lineHeight = 20.sp)
        Box(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { onShare(message) },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(NotizIcons.Share, contentDescription = null, tint = Palette.teal, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.members_share_whatsapp), color = Palette.teal, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

/** Avatar, name, flat and role/muted chips. */
@Composable
private fun MemberLine(m: Membership, modifier: Modifier = Modifier) {
    val name = m.profiles?.fullName ?: stringResource(Res.string.members_resident)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(name, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(Res.string.home_flat, m.flatNo), color = Palette.muted, fontSize = 13.sp)
        }
        if (m.chatMuted) Chip(stringResource(Res.string.chat_muted_pill), Palette.red)
        if (m.isAdmin) Chip(stringResource(Res.string.members_admin), Palette.teal)
    }
}

/** Admin actions for a member, tucked into a ⋯ menu so the list stays clean. */
@Composable
private fun MemberMenu(
    m: Membership,
    isMe: Boolean,
    busy: Boolean,
    onToggleAdmin: () -> Unit,
    onChangeFlat: () -> Unit,
    onToggleMute: () -> Unit,
    onRemove: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = !busy) {
            Icon(NotizIcons.More, contentDescription = stringResource(Res.string.members_actions), tint = Palette.muted)
        }
        NotizMenu(expanded = open, onDismissRequest = { open = false }) {
            if (!isMe) {
                NotizMenuItem(
                    text = { Text(stringResource(if (m.isAdmin) Res.string.members_remove_admin else Res.string.members_make_admin)) },
                    onClick = { open = false; onToggleAdmin() },
                )
            }
            NotizMenuItem(text = { Text(stringResource(Res.string.members_change_flat)) }, onClick = { open = false; onChangeFlat() })
            if (!isMe) {
                NotizMenuItem(
                    text = { Text(stringResource(if (m.chatMuted) Res.string.chat_unmute else Res.string.chat_mute)) },
                    onClick = { open = false; onToggleMute() },
                )
                NotizMenuItem(
                    text = { Text(stringResource(Res.string.members_remove), color = Palette.red) },
                    onClick = { open = false; onRemove() },
                )
            }
        }
    }
}
