@file:OptIn(ExperimentalMaterial3Api::class)

package com.notiz.app.ui.complaints

import com.notiz.app.ui.components.NotizTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.notiz.app.ui.components.Chip
import com.notiz.app.ui.components.EmptyState
import com.notiz.app.ui.components.FilterPill
import com.notiz.app.ui.components.IconTile
import com.notiz.app.ui.components.NotizCard
import com.notiz.app.ui.components.NotizFab
import com.notiz.app.ui.components.PrimaryButton
import com.notiz.app.ui.components.ScreenTitle
import com.notiz.app.ui.theme.NotizIcons
import com.notiz.app.ui.theme.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notiz.app.AppContainer
import com.notiz.app.LocalAppContainer
import com.notiz.app.data.Complaint
import com.notiz.app.data.ComplaintRepository
import com.notiz.app.data.ComplaintStatus
import com.notiz.app.push.OnPushUpdate
import com.notiz.app.push.PushTypes
import com.notiz.app.ui.BaseViewModel
import com.notiz.app.ui.SessionContext
import com.notiz.app.ui.components.ErrorBanner
import com.notiz.app.ui.components.ErrorBox
import com.notiz.app.ui.components.LoadingBox
import com.notiz.app.ui.theme.StatusColors
import com.notiz.app.i18n.localTimestamp
import kotlinx.coroutines.flow.MutableStateFlow
import com.notiz.app.data.AttachmentRepository
import com.notiz.app.photos.PhotoPickerRow
import com.notiz.app.photos.RemotePhotoStrip
import kotlinx.coroutines.flow.update
import com.notiz.app.resources.*
import org.jetbrains.compose.resources.stringResource
import com.notiz.app.i18n.UiText
import com.notiz.app.i18n.asString

data class ComplaintsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val complaints: List<Complaint> = emptyList(),
    val loadError: UiText? = null,
    val actionError: UiText? = null,
    val busy: Boolean = false,
)

class ComplaintsViewModel(
    private val container: AppContainer,
    private val session: SessionContext,
) : BaseViewModel() {
    val state = MutableStateFlow(ComplaintsUiState())

    init { load() }

    fun load(pullToRefresh: Boolean = false) {
        state.update { it.copy(refreshing = pullToRefresh, loadError = null) }
        launchSafe(onError = { msg -> state.update { it.copy(loading = false, refreshing = false, loadError = msg) } }) {
            val list = container.complaints.complaints(session.societyId)
            state.update { it.copy(loading = false, refreshing = false, complaints = list) }
        }
    }

    fun raise(category: String, description: String, photos: List<ByteArray>, onDone: () -> Unit) {
        state.update { it.copy(busy = true, actionError = null) }
        launchSafe(onError = { msg -> state.update { it.copy(busy = false, actionError = msg) } }) {
            val paths = mutableListOf<String>()
            try {
                photos.forEach { paths += container.attachments.upload(session.societyId, AttachmentRepository.Kind.COMPLAINT, it) }
                container.complaints.raise(session.societyId, category, description, paths)
            } catch (e: Throwable) {
                container.attachments.delete(paths) // don't leave orphan photos behind
                throw e
            }
            state.update { it.copy(busy = false) }
            onDone()
            load()
        }
    }

    fun setStatus(complaint: Complaint, status: String) {
        if (complaint.status == status) return
        // Optimistic update so the chip responds instantly
        state.update { s -> s.copy(complaints = s.complaints.map { if (it.id == complaint.id) it.copy(status = status) else it }) }
        launchSafe(onError = { msg -> state.update { it.copy(actionError = msg) }; load() }) {
            container.complaints.setStatus(complaint.id, status)
        }
    }

    fun dismissError() = state.update { it.copy(actionError = null) }
}

private fun statusLabel(status: String) = when (status) {
    ComplaintStatus.IN_PROGRESS -> Res.string.complaint_in_progress
    ComplaintStatus.RESOLVED -> Res.string.complaint_resolved
    else -> Res.string.complaint_open
}

/** Categories are stored in English (the server's push text uses them); show them translated. */
@Composable
private fun categoryLabel(category: String): String = when (category) {
    "Plumbing" -> stringResource(Res.string.category_plumbing)
    "Electrical" -> stringResource(Res.string.category_electrical)
    "Lift" -> stringResource(Res.string.category_lift)
    "Security" -> stringResource(Res.string.category_security)
    "Cleaning" -> stringResource(Res.string.category_cleaning)
    "Parking" -> stringResource(Res.string.category_parking)
    "Other" -> stringResource(Res.string.category_other)
    else -> category
}

private fun statusColor(status: String) = when (status) {
    ComplaintStatus.IN_PROGRESS -> StatusColors.info
    ComplaintStatus.RESOLVED -> StatusColors.success
    else -> StatusColors.warning
}

private fun categoryIcon(category: String): Triple<ImageVector, Color, Color> = when (category) {
    "Plumbing" -> Triple(NotizIcons.Drop, Palette.teal, Palette.tealSoft)
    "Electrical" -> Triple(NotizIcons.Bolt, Palette.amberText, Palette.amberSoft)
    "Lift" -> Triple(NotizIcons.Lift, Palette.blue, Palette.blueSoft)
    "Security" -> Triple(NotizIcons.Shield, Palette.purple, Palette.purpleSoft)
    "Cleaning" -> Triple(NotizIcons.Sparkle, Palette.teal, Palette.tealSoft)
    "Parking" -> Triple(NotizIcons.Car, Palette.blue, Palette.blueSoft)
    else -> Triple(NotizIcons.More, Palette.inkSoft, Palette.track)
}

@Composable
fun ComplaintsTab(session: SessionContext, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val vm = viewModel { ComplaintsViewModel(container, session) }
    val state by vm.state.collectAsState()
    OnPushUpdate(PushTypes.COMPLAINT) { vm.load() }
    var raising by remember { mutableStateOf(false) }
    var showResolved by remember { mutableStateOf(false) }
    val open = state.complaints.filter { it.status != ComplaintStatus.RESOLVED }
    val resolved = state.complaints.filter { it.status == ComplaintStatus.RESOLVED }
    val shown = if (showResolved) resolved else open

    Box(modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingBox()
            state.loadError != null && state.complaints.isEmpty() -> ErrorBox(state.loadError!!.asString(), onRetry = { vm.load() })
            else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(pullToRefresh = true) }) {
                LazyColumn(
                    contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            ScreenTitle(stringResource(Res.string.tab_help), subtitle = stringResource(Res.string.help_subtitle))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterPill(stringResource(Res.string.help_open, open.size), selected = !showResolved, onClick = { showResolved = false })
                                FilterPill(stringResource(Res.string.help_resolved, resolved.size), selected = showResolved, onClick = { showResolved = true })
                            }
                        }
                    }
                    if (shown.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 40.dp)) {
                                EmptyState(
                                    stringResource(if (showResolved) Res.string.help_none_resolved else Res.string.complaints_empty_title),
                                    if (showResolved) {
                                        ""
                                    } else if (session.isAdmin) {
                                        stringResource(Res.string.complaints_empty_admin)
                                    } else {
                                        stringResource(Res.string.complaints_empty_resident)
                                    },
                                )
                            }
                        }
                    }
                    items(shown, key = { it.id }) { complaint ->
                        ComplaintCard(
                            complaint = complaint,
                            isAdmin = session.isAdmin,
                            onStatus = { vm.setStatus(complaint, it) },
                        )
                    }
                }
            }
        }

        NotizFab(stringResource(Res.string.complaints_raise), onClick = { raising = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
        ErrorBanner(state.actionError?.asString(), onDismiss = vm::dismissError, modifier = Modifier.align(Alignment.TopCenter))
    }

    if (raising) {
        RaiseComplaintSheet(
            busy = state.busy,
            onDismiss = { raising = false },
            onRaise = { category, description, photos -> vm.raise(category, description, photos) { raising = false } },
        )
    }
}

@Composable
private fun ComplaintCard(complaint: Complaint, isAdmin: Boolean, onStatus: (String) -> Unit) {
    val (icon, tint, bg) = categoryIcon(complaint.category)
    NotizCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(icon, tint, bg, size = 40.dp, iconSize = 20.dp, radius = 12.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    categoryLabel(complaint.category) +
                        if (isAdmin && complaint.flatNo.isNotBlank()) " · " + stringResource(Res.string.home_flat, complaint.flatNo) else "",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(localTimestamp(complaint.createdAt, withTime = false), color = Palette.muted, fontSize = 12.sp)
            }
            Chip(stringResource(statusLabel(complaint.status)), statusColor(complaint.status))
        }
        Text(complaint.description, color = Palette.inkSoft, fontSize = 14.sp, lineHeight = 21.sp)
        if (complaint.photoPaths.isNotEmpty()) RemotePhotoStrip(complaint.photoPaths, size = 72.dp)
        StatusTracker(complaint.status)
        if (isAdmin) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComplaintStatus.all.forEach { status ->
                    FilterPill(stringResource(statusLabel(status)), selected = complaint.status == status, onClick = { onStatus(status) })
                }
            }
        }
    }
}

/** Raised → In progress → Fixed, so residents see where their request is. */
@Composable
private fun StatusTracker(status: String) {
    val step = when (status) {
        ComplaintStatus.RESOLVED -> 2
        ComplaintStatus.IN_PROGRESS -> 1
        else -> 0
    }
    val labels = listOf(
        stringResource(Res.string.help_step_raised),
        stringResource(statusLabel(ComplaintStatus.IN_PROGRESS)),
        stringResource(Res.string.help_step_fixed),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, label ->
            val done = i < step || (i == step && i == 2)
            val current = i == step && i != 2
            Box(
                Modifier.size(20.dp).background(
                    when {
                        done -> Palette.teal
                        current -> Palette.blue
                        else -> Palette.track
                    },
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(NotizIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
            Text(label, fontSize = 12.sp, fontWeight = if (i <= step) FontWeight.SemiBold else FontWeight.Normal, color = if (i <= step) Palette.ink else Palette.muted)
            if (i < labels.lastIndex) {
                Box(Modifier.weight(1f).height(2.dp).background(if (i < step) Palette.teal else Color(0xFFD5DDD9)))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RaiseComplaintSheet(
    busy: Boolean,
    onDismiss: () -> Unit,
    onRaise: (category: String, description: String, photos: List<ByteArray>) -> Unit,
) {
    var category by remember { mutableStateOf(ComplaintRepository.categories.first()) }
    var description by remember { mutableStateOf("") }
    var photos by remember { mutableStateOf(listOf<ByteArray>()) }
    var pickFailed by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 8.dp).size(width = 40.dp, height = 4.dp).background(Color(0xFFD5DDD9), RoundedCornerShape(2.dp))) },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(Res.string.complaints_raise_title), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            // 4 per row, large targets
            ComplaintRepository.categories.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { c ->
                        val (icon, _, _) = categoryIcon(c)
                        val selected = category == c
                        Column(
                            Modifier
                                .weight(1f)
                                .height(68.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) Palette.tealSoft else Color.White)
                                .border(if (selected) 1.5.dp else 1.dp, if (selected) Palette.teal else Palette.line, RoundedCornerShape(14.dp))
                                .clickable { category = c },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(icon, contentDescription = null, tint = if (selected) Palette.teal else Palette.ink, modifier = Modifier.size(20.dp))
                            Text(categoryLabel(c), fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (selected) Palette.teal else Palette.ink, maxLines = 1)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            NotizTextField(
                value = description,
                onValueChange = { description = it.take(2000) },
                label = { Text(stringResource(Res.string.complaints_problem_field)) },
                minLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(Res.string.photos_complaint_hint), color = Palette.muted, fontSize = 12.sp)
            PhotoPickerRow(
                photos = photos,
                enabled = !busy,
                onAdd = { photos = photos + it; pickFailed = false },
                onRemove = { i -> photos = photos.filterIndexed { index, _ -> index != i } },
                onPickFailed = { pickFailed = true },
            )
            if (pickFailed) {
                Text(stringResource(Res.string.photos_pick_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            PrimaryButton(
                stringResource(if (busy) Res.string.action_sending else Res.string.help_send),
                onClick = { onRaise(category, description, photos) },
                enabled = description.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
