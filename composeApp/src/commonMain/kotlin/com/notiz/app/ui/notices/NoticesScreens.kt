@file:OptIn(ExperimentalMaterial3Api::class)

package com.notiz.app.ui.notices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notiz.app.AppContainer
import com.notiz.app.LocalAppContainer
import com.notiz.app.data.Notice
import com.notiz.app.data.NoticeReadCount
import com.notiz.app.ui.BaseViewModel
import com.notiz.app.ui.SessionContext
import com.notiz.app.ui.components.EmptyState
import com.notiz.app.ui.components.ErrorBanner
import com.notiz.app.ui.components.ErrorBox
import com.notiz.app.ui.components.LoadingBox
import com.notiz.app.ui.components.StatusPill
import com.notiz.app.ui.components.UnreadDot
import com.notiz.app.util.formatTimestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

// ───────────────────────────── List ─────────────────────────────

data class NoticesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val notices: List<Notice> = emptyList(),
    val readIds: Set<String> = emptySet(),
    val readCounts: Map<String, NoticeReadCount> = emptyMap(),
    val loadError: String? = null,
    val actionError: String? = null,
    val posting: Boolean = false,
)

class NoticesViewModel(
    private val container: AppContainer,
    private val session: SessionContext,
) : BaseViewModel() {
    val state = MutableStateFlow(NoticesUiState())

    init { load() }

    fun load(pullToRefresh: Boolean = false) {
        state.update { it.copy(refreshing = pullToRefresh, loadError = null) }
        launchSafe(onError = { msg -> state.update { it.copy(loading = false, refreshing = false, loadError = msg) } }) {
            val notices = container.notices.notices(session.societyId)
            val readIds = container.notices.myReadNoticeIds()
            val counts = if (session.isAdmin) container.notices.readCounts(session.societyId) else emptyMap()
            state.update {
                it.copy(loading = false, refreshing = false, notices = notices, readIds = readIds, readCounts = counts)
            }
        }
    }

    fun post(title: String, body: String, pinned: Boolean, onDone: () -> Unit) {
        state.update { it.copy(posting = true, actionError = null) }
        launchSafe(onError = { msg -> state.update { it.copy(posting = false, actionError = msg) } }) {
            container.notices.post(session.societyId, title, body, pinned)
            state.update { it.copy(posting = false) }
            onDone()
            load()
        }
    }

    fun dismissError() = state.update { it.copy(actionError = null) }
}

@Composable
fun NoticesTab(session: SessionContext, onOpenNotice: (String) -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val vm = viewModel { NoticesViewModel(container, session) }
    val state by vm.state.collectAsState()
    var composing by remember { mutableStateOf(false) }

    // Refresh read markers when coming back from a notice
    LaunchedEffect(Unit) { if (!state.loading) vm.load() }

    Box(modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingBox()
            state.loadError != null && state.notices.isEmpty() -> ErrorBox(state.loadError!!, onRetry = { vm.load() })
            else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(pullToRefresh = true) }) {
                if (state.notices.isEmpty()) {
                    EmptyState(
                        "No notices yet",
                        if (session.isAdmin) "Post the first notice for your residents." else "Society notices will appear here.",
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.notices, key = { it.id }) { notice ->
                            NoticeCard(
                                notice = notice,
                                unread = notice.id !in state.readIds,
                                readCount = state.readCounts[notice.id],
                                onClick = { onOpenNotice(notice.id) },
                            )
                        }
                    }
                }
            }
        }

        if (session.isAdmin) {
            ExtendedFloatingActionButton(
                onClick = { composing = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New notice") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
        ErrorBanner(state.actionError, onDismiss = vm::dismissError, modifier = Modifier.align(Alignment.TopCenter))
    }

    if (composing) {
        NewNoticeDialog(
            posting = state.posting,
            onDismiss = { composing = false },
            onPost = { title, body, pinned -> vm.post(title, body, pinned) { composing = false } },
        )
    }
}

@Composable
private fun NoticeCard(notice: Notice, unread: Boolean, readCount: NoticeReadCount?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (unread) {
                    UnreadDot()
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    notice.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (notice.pinned) StatusPill("Pinned", MaterialTheme.colorScheme.secondary)
            }
            if (notice.body.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    notice.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                formatTimestamp(notice.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (readCount != null) ReadProgress(readCount)
        }
    }
}

@Composable
private fun ReadProgress(count: NoticeReadCount) {
    Spacer(Modifier.height(8.dp))
    Text(
        "Read by ${count.reads} of ${count.members} members",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
    LinearProgressIndicator(
        progress = { if (count.members == 0L) 0f else count.reads.toFloat() / count.members },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NewNoticeDialog(
    posting: Boolean,
    onDismiss: () -> Unit,
    onPost: (title: String, body: String, pinned: Boolean) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!posting) onDismiss() },
        title = { Text("New notice") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(200) },
                    label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body, onValueChange = { body = it.take(4000) },
                    label = { Text("Details") }, minLines = 4,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = pinned, onCheckedChange = { pinned = it })
                    Text("Pin to top")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPost(title, body, pinned) }, enabled = title.isNotBlank() && !posting) {
                Text(if (posting) "Posting…" else "Post")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !posting) { Text("Cancel") } },
    )
}

// ───────────────────────────── Detail ─────────────────────────────

data class NoticeDetailUiState(
    val loading: Boolean = true,
    val notice: Notice? = null,
    val error: String? = null,
)

class NoticeDetailViewModel(
    private val container: AppContainer,
    private val session: SessionContext,
    private val noticeId: String,
) : BaseViewModel() {
    val state = MutableStateFlow(NoticeDetailUiState())

    init { load() }

    fun load() {
        state.value = NoticeDetailUiState(loading = true)
        launchSafe(onError = { msg -> state.value = NoticeDetailUiState(loading = false, error = msg) }) {
            val notice = container.notices.notice(noticeId)
            state.value = NoticeDetailUiState(loading = false, notice = notice)
            // A failed read receipt shouldn't block reading the notice
            runCatching { container.notices.markRead(noticeId) }
        }
    }

    fun togglePin(onDone: () -> Unit) {
        val notice = state.value.notice ?: return
        launchSafe(onError = { msg -> state.update { it.copy(error = msg) } }) {
            container.notices.setPinned(notice.id, !notice.pinned)
            state.update { it.copy(notice = notice.copy(pinned = !notice.pinned)) }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val notice = state.value.notice ?: return
        if (!session.isAdmin) return
        launchSafe(onError = { msg -> state.update { it.copy(error = msg) } }) {
            container.notices.delete(notice.id)
            onDone()
        }
    }
}

@Composable
fun NoticeDetailScreen(session: SessionContext, noticeId: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel(key = "notice-$noticeId") { NoticeDetailViewModel(container, session, noticeId) }
    val state by vm.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notice") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val notice = state.notice
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            notice == null -> ErrorBox(state.error ?: "Notice not found", onRetry = vm::load, modifier = Modifier.padding(padding))
            else -> Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            ) {
                if (notice.pinned) {
                    StatusPill("Pinned", MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(8.dp))
                }
                Text(notice.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    formatTimestamp(notice.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(notice.body, style = MaterialTheme.typography.bodyLarge)

                if (session.isAdmin) {
                    Spacer(Modifier.height(32.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { vm.togglePin {} }) {
                            Text(if (notice.pinned) "Unpin" else "Pin to top")
                        }
                        OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete") }
                    }
                }
                state.error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this notice?") },
            text = { Text("Residents will no longer see it.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
