@file:OptIn(ExperimentalMaterial3Api::class)

package com.notiz.app.ui.notices

import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import com.notiz.app.ui.components.PrimaryButton
import com.notiz.app.ui.components.NotizSwitch
import com.notiz.app.ui.components.NotizSheet
import com.notiz.app.ui.components.NotizCheckbox
import com.notiz.app.ui.components.ProgressBar
import com.notiz.app.ui.components.NotizTextButton
import com.notiz.app.ui.components.NotizTextField
import com.notiz.app.ui.components.NotizDialog
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.notiz.app.push.OnPushUpdate
import com.notiz.app.push.PushTypes
import com.notiz.app.ui.BaseViewModel
import com.notiz.app.ui.SessionContext
import com.notiz.app.ui.components.ErrorBanner
import com.notiz.app.ui.components.ErrorBox
import com.notiz.app.ui.components.LoadingBox
import com.notiz.app.ui.components.RefreshableEmptyState
import com.notiz.app.ui.components.StatusPill
import com.notiz.app.ui.components.UnreadDot
import com.notiz.app.i18n.localTimestamp
import kotlinx.coroutines.flow.MutableStateFlow
import com.notiz.app.data.AttachmentRepository
import com.notiz.app.photos.PhotoPickerRow
import com.notiz.app.photos.RemotePhotoStrip
import com.notiz.app.ui.components.NotizCard
import com.notiz.app.ui.components.NotizFab
import com.notiz.app.ui.components.ScreenTitle
import com.notiz.app.ui.components.SegmentedTabs
import com.notiz.app.ui.components.Tag
import com.notiz.app.ui.polls.PollsTab
import com.notiz.app.ui.theme.Palette
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import com.notiz.app.ui.components.DetailTopBar
import com.notiz.app.ui.components.IconTile
import com.notiz.app.ui.components.SecondaryButton
import com.notiz.app.ui.theme.NotizIcons
import kotlinx.coroutines.flow.update
import com.notiz.app.resources.*
import org.jetbrains.compose.resources.stringResource
import com.notiz.app.i18n.UiText
import com.notiz.app.i18n.asString

// ───────────────────────────── List ─────────────────────────────

data class NoticesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val notices: List<Notice> = emptyList(),
    val readIds: Set<String> = emptySet(),
    val readCounts: Map<String, NoticeReadCount> = emptyMap(),
    val loadError: UiText? = null,
    val actionError: UiText? = null,
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

    fun post(title: String, body: String, pinned: Boolean, photos: List<ByteArray>, onDone: () -> Unit) {
        state.update { it.copy(posting = true, actionError = null) }
        launchSafe(onError = { msg -> state.update { it.copy(posting = false, actionError = msg) } }) {
            val paths = mutableListOf<String>()
            try {
                photos.forEach { paths += container.attachments.upload(session.societyId, AttachmentRepository.Kind.NOTICE, it) }
                container.notices.post(session.societyId, title, body, pinned, paths)
            } catch (e: Throwable) {
                container.attachments.delete(paths) // don't leave orphan photos behind
                throw e
            }
            state.update { it.copy(posting = false) }
            onDone()
            load()
        }
    }

    fun dismissError() = state.update { it.copy(actionError = null) }
}

/** The Notices tab: notices and polls behind one segmented control. */
@Composable
fun NoticesHub(
    session: SessionContext,
    showPolls: Boolean,
    onShowPolls: (Boolean) -> Unit,
    unreadNotices: Int,
    onOpenNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenTitle(stringResource(Res.string.tab_notices))
            SegmentedTabs(
                options = listOf(
                    stringResource(Res.string.tab_notices) + if (unreadNotices > 0) " · " + stringResource(Res.string.notices_new_count, unreadNotices) else "",
                    stringResource(Res.string.tab_polls),
                ),
                selected = if (showPolls) 1 else 0,
                onSelect = { onShowPolls(it == 1) },
            )
        }
        if (showPolls) {
            PollsTab(session, Modifier.weight(1f))
        } else {
            NoticesTab(session, onOpenNotice, Modifier.weight(1f))
        }
    }
}

@Composable
fun NoticesTab(session: SessionContext, onOpenNotice: (String) -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val vm = viewModel { NoticesViewModel(container, session) }
    val state by vm.state.collectAsState()
    OnPushUpdate(PushTypes.NOTICE) { vm.load() }
    var composing by remember { mutableStateOf(false) }

    // Refresh read markers when coming back from a notice
    LaunchedEffect(Unit) { if (!state.loading) vm.load() }

    Box(modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingBox()
            state.loadError != null && state.notices.isEmpty() -> ErrorBox(state.loadError!!.asString(), onRetry = { vm.load() })
            else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(pullToRefresh = true) }) {
                if (state.notices.isEmpty()) {
                    RefreshableEmptyState(
                        stringResource(Res.string.notices_empty_title),
                        if (session.isAdmin) {
                            stringResource(Res.string.notices_empty_admin)
                        } else {
                            stringResource(Res.string.notices_empty_resident)
                        },
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 96.dp),
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
            NotizFab(stringResource(Res.string.notice_new), onClick = { composing = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
        }
        ErrorBanner(state.actionError?.asString(), onDismiss = vm::dismissError, modifier = Modifier.align(Alignment.TopCenter))
    }

    if (composing) {
        NewNoticeDialog(
            posting = state.posting,
            onDismiss = { composing = false },
            onPost = { title, body, pinned, photos -> vm.post(title, body, pinned, photos) { composing = false } },
        )
    }
}

@Composable
private fun NoticeCard(notice: Notice, unread: Boolean, readCount: NoticeReadCount?, onClick: () -> Unit) {
    NotizCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (unread) UnreadDot()
            if (notice.pinned) Tag(stringResource(Res.string.notice_pinned))
            Text(localTimestamp(notice.createdAt), color = Palette.muted, fontSize = 12.sp)
        }
        Text(
            notice.title,
            fontSize = 17.sp,
            fontWeight = if (unread) FontWeight.ExtraBold else FontWeight.Bold,
            lineHeight = 22.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (notice.body.isNotBlank()) {
            Text(
                notice.body,
                color = Palette.inkSoft,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (notice.photoPaths.isNotEmpty()) {
            RemotePhotoStrip(notice.photoPaths, size = 84.dp)
        }
        if (readCount != null) ReadProgress(readCount)
    }
}

@Composable
private fun ReadProgress(count: NoticeReadCount) {
    Text(
        stringResource(Res.string.notice_read_by, count.reads, count.members),
        style = MaterialTheme.typography.labelMedium,
        color = Palette.teal,
    )
    ProgressBar(if (count.members == 0L) 0f else count.reads.toFloat() / count.members)
}

@Composable
private fun NewNoticeDialog(
    posting: Boolean,
    onDismiss: () -> Unit,
    onPost: (title: String, body: String, pinned: Boolean, photos: List<ByteArray>) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf(listOf<ByteArray>()) }
    var pickFailed by remember { mutableStateOf(false) }
    NotizSheet(stringResource(Res.string.notice_new), onDismissRequest = onDismiss, dismissible = !posting) {
        NotizTextField(
            value = title, onValueChange = { title = it.take(200) },
            label = { Text(stringResource(Res.string.notice_field_title)) },
            placeholder = { Text(stringResource(Res.string.notice_title_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        NotizTextField(
            value = body, onValueChange = { body = it.take(4000) },
            label = { Text(stringResource(Res.string.notice_field_details)) },
            placeholder = { Text(stringResource(Res.string.notice_body_placeholder)) },
            minLines = 4,
            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
        )
        Row(
            Modifier.fillMaxWidth().background(Palette.chip, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(NotizIcons.Pin, contentDescription = null, tint = Palette.amberText, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.notice_pin), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(Res.string.notice_pin_hint), color = Palette.muted, fontSize = 12.sp)
            }
            NotizSwitch(checked = pinned, onCheckedChange = { pinned = it })
        }
        Text(stringResource(Res.string.notice_photos_hint), color = Palette.muted, fontSize = 12.sp)
        PhotoPickerRow(
            photos = photos,
            enabled = !posting,
            onAdd = { photos = photos + it; pickFailed = false },
            onRemove = { i -> photos = photos.filterIndexed { index, _ -> index != i } },
            onPickFailed = { pickFailed = true },
        )
        if (pickFailed) {
            Text(stringResource(Res.string.photos_pick_failed), color = Palette.red, fontSize = 12.sp)
        }
        PrimaryButton(
            stringResource(if (posting) Res.string.notice_posting else Res.string.notice_post),
            onClick = { onPost(title, body, pinned, photos) },
            enabled = title.isNotBlank() && !posting,
            icon = NotizIcons.Send,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            height = 52.dp,
        )
    }
}

// ───────────────────────────── Detail ─────────────────────────────

data class NoticeDetailUiState(
    val loading: Boolean = true,
    val notice: Notice? = null,
    val error: UiText? = null,
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
            container.attachments.delete(notice.photoPaths)
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
        containerColor = Palette.ground,
        topBar = { DetailTopBar(stringResource(Res.string.notice_title), onBack) },
    ) { padding ->
        val notice = state.notice
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            notice == null -> ErrorBox(state.error?.asString() ?: stringResource(Res.string.notice_not_found), onRetry = vm::load, modifier = Modifier.padding(padding))
            else -> Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                NotizCard(radius = 22.dp, padding = PaddingValues(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconTile(NotizIcons.Notice, Palette.teal, Palette.tealSoft, size = 36.dp, iconSize = 18.dp, radius = 12.dp)
                        Text(localTimestamp(notice.createdAt), color = Palette.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (notice.pinned) Tag(stringResource(Res.string.notice_pinned))
                    }
                    Text(notice.title, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
                    if (notice.body.isNotBlank()) {
                        Text(notice.body, color = Palette.inkSoft, fontSize = 16.sp, lineHeight = 25.sp)
                    }
                    if (notice.photoPaths.isNotEmpty()) RemotePhotoStrip(notice.photoPaths, size = 140.dp)
                }

                if (session.isAdmin) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(
                            stringResource(if (notice.pinned) Res.string.notice_unpin else Res.string.notice_pin),
                            onClick = { vm.togglePin {} },
                            icon = NotizIcons.Pin,
                            accent = true,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(stringResource(Res.string.action_delete), onClick = { confirmDelete = true }, icon = NotizIcons.Trash, modifier = Modifier.weight(1f))
                    }
                }
                state.error?.let { Text(it.asString(), color = Palette.red) }
            }
        }
    }

    if (confirmDelete) {
        NotizDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.notice_delete_title)) },
            text = { Text(stringResource(Res.string.notice_delete_body)) },
            confirmButton = {
                NotizTextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = { NotizTextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}
