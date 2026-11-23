@file:OptIn(ExperimentalMaterial3Api::class)

package com.notiz.app.ui.directory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notiz.app.AppContainer
import com.notiz.app.LocalAppContainer
import com.notiz.app.data.DirectoryEntry
import com.notiz.app.data.Roles
import com.notiz.app.i18n.UiText
import com.notiz.app.i18n.asString
import com.notiz.app.push.OnPushUpdate
import com.notiz.app.push.PushTypes
import com.notiz.app.resources.*
import com.notiz.app.ui.BaseViewModel
import com.notiz.app.ui.SessionContext
import com.notiz.app.ui.components.ErrorBanner
import com.notiz.app.ui.components.ErrorBox
import com.notiz.app.ui.components.GroupCard
import com.notiz.app.ui.components.LoadingBox
import com.notiz.app.ui.components.SkeletonKind
import com.notiz.app.ui.components.NotizTextField
import com.notiz.app.ui.components.RefreshableEmptyState
import com.notiz.app.ui.components.RoleChip
import com.notiz.app.ui.components.ScreenTitle
import com.notiz.app.ui.components.roleLabel
import com.notiz.app.ui.theme.NotizIcons
import com.notiz.app.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.stringResource

data class DirectoryUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val entries: List<DirectoryEntry> = emptyList(),
    val loadError: UiText? = null,
    val query: String = "",
) {
    /** Matches flat number or name; flats in natural order (A-2 before A-10). */
    val visible: List<DirectoryEntry>
        get() {
            val q = query.trim().lowercase()
            return entries
                .filter { q.isEmpty() || it.flatNo.lowercase().contains(q) || it.fullName.lowercase().contains(q) }
                .sortedWith(compareBy<DirectoryEntry> { it.role == Roles.SECURITY }.thenBy { flatKey(it.flatNo) }.thenBy { it.fullName })
        }

    private fun flatKey(flat: String): String =
        Regex("\\d+").replace(flat.uppercase()) { it.value.padStart(6, '0') }
}

class DirectoryViewModel(
    private val container: AppContainer,
    private val session: SessionContext,
) : BaseViewModel() {
    val state = MutableStateFlow(DirectoryUiState())

    init { load() }

    fun load(pullToRefresh: Boolean = false) {
        state.update { it.copy(loadError = null, refreshing = pullToRefresh) }
        launchSafe(onError = { msg -> state.update { it.copy(loading = false, refreshing = false, loadError = msg) } }) {
            val entries = container.societies.directory(session.societyId)
            state.update { it.copy(loading = false, refreshing = false, entries = entries) }
        }
    }

    fun search(text: String) = state.update { it.copy(query = text.take(40)) }
}

/**
 * The security desk's home: every flat with who lives there, searchable, with a call button
 * (phone numbers are only sent to security staff and the committee).
 */
@Composable
fun DirectoryTab(session: SessionContext, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val vm = viewModel { DirectoryViewModel(container, session) }
    val state by vm.state.collectAsState()
    OnPushUpdate(PushTypes.MEMBER, PushTypes.SOCIETY) { vm.load() }
    val uriHandler = LocalUriHandler.current
    var callError by remember { mutableStateOf(false) }

    Column(modifier) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenTitle(
                stringResource(Res.string.directory_title),
                subtitle = session.society.name + " · " + roleLabel(session.role),
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Palette.chip).clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(NotizIcons.Settings, contentDescription = stringResource(Res.string.settings_title), tint = Palette.teal, modifier = Modifier.size(20.dp))
                }
            }
            NotizTextField(
                value = state.query,
                onValueChange = vm::search,
                placeholder = { Text(stringResource(Res.string.directory_search)) },
                prefix = { Icon(NotizIcons.Search, contentDescription = null, tint = Palette.muted, modifier = Modifier.size(18.dp).padding(end = 2.dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ErrorBanner(if (callError) stringResource(Res.string.directory_call_failed) else null, onDismiss = { callError = false })
        Box(Modifier.weight(1f)) {
            when {
                state.loading -> LoadingBox(kind = SkeletonKind.List)
                state.loadError != null && state.entries.isEmpty() -> ErrorBox(state.loadError!!.asString(), onRetry = { vm.load() })
                else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(pullToRefresh = true) }) {
                    val list = state.visible
                    if (list.isEmpty()) {
                        RefreshableEmptyState(
                            stringResource(Res.string.directory_empty_title),
                            stringResource(if (state.query.isBlank()) Res.string.directory_empty else Res.string.directory_no_match),
                            icon = if (state.query.isBlank()) NotizIcons.Users else NotizIcons.Search,
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            item {
                                GroupCard {
                                    list.forEachIndexed { i, entry ->
                                        DirectoryRow(entry, divider = i < list.lastIndex) { phone ->
                                            runCatching { uriHandler.openUri("tel:+$phone") }.onFailure { callError = true }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryRow(entry: DirectoryEntry, divider: Boolean, onCall: (String) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Flat number in a tile — what the guard looks for first
            Box(
                Modifier.size(width = 64.dp, height = 44.dp).background(Palette.tealSoft, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (entry.role == Roles.SECURITY) {
                    Icon(NotizIcons.Shield, contentDescription = null, tint = Palette.blueText, modifier = Modifier.size(20.dp))
                } else {
                    Text(entry.flatNo, color = Palette.teal, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(entry.fullName.ifBlank { stringResource(Res.string.members_resident) }, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entry.role != Roles.RESIDENT) Row(Modifier.padding(top = 2.dp)) { RoleChip(entry.role) }
            }
            val phone = entry.phone?.filter { it.isDigit() }.orEmpty()
            if (phone.isNotEmpty()) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Palette.teal).clickable { onCall(phone) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(NotizIcons.Phone, contentDescription = stringResource(Res.string.directory_call), tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (divider) Box(Modifier.fillMaxWidth().padding(start = 90.dp).height(1.dp).background(Palette.lineSoft))
    }
}
