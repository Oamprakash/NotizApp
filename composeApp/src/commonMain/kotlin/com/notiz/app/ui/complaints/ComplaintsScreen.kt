@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.notiz.app.ui.complaints

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.notiz.app.ui.components.RefreshableEmptyState
import com.notiz.app.ui.components.StatusPill
import com.notiz.app.ui.theme.StatusColors
import com.notiz.app.i18n.localTimestamp
import kotlinx.coroutines.flow.MutableStateFlow
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

    fun raise(category: String, description: String, onDone: () -> Unit) {
        state.update { it.copy(busy = true, actionError = null) }
        launchSafe(onError = { msg -> state.update { it.copy(busy = false, actionError = msg) } }) {
            container.complaints.raise(session.societyId, category, description)
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

@Composable
fun ComplaintsTab(session: SessionContext, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val vm = viewModel { ComplaintsViewModel(container, session) }
    val state by vm.state.collectAsState()
    OnPushUpdate(PushTypes.COMPLAINT) { vm.load() }
    var raising by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingBox()
            state.loadError != null && state.complaints.isEmpty() -> ErrorBox(state.loadError!!.asString(), onRetry = { vm.load() })
            else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(pullToRefresh = true) }) {
                if (state.complaints.isEmpty()) {
                    RefreshableEmptyState(
                        stringResource(Res.string.complaints_empty_title),
                        if (session.isAdmin) {
                            stringResource(Res.string.complaints_empty_admin)
                        } else {
                            stringResource(Res.string.complaints_empty_resident)
                        },
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.complaints, key = { it.id }) { complaint ->
                            ComplaintCard(
                                complaint = complaint,
                                isAdmin = session.isAdmin,
                                onStatus = { vm.setStatus(complaint, it) },
                            )
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { raising = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(Res.string.complaints_raise)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
        ErrorBanner(state.actionError?.asString(), onDismiss = vm::dismissError, modifier = Modifier.align(Alignment.TopCenter))
    }

    if (raising) {
        RaiseComplaintDialog(
            busy = state.busy,
            onDismiss = { raising = false },
            onRaise = { category, description -> vm.raise(category, description) { raising = false } },
        )
    }
}

@Composable
private fun ComplaintCard(complaint: Complaint, isAdmin: Boolean, onStatus: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    categoryLabel(complaint.category) +
                        if (isAdmin && complaint.flatNo.isNotBlank()) {
                            " · " + stringResource(Res.string.home_flat, complaint.flatNo)
                        } else {
                            ""
                        },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(stringResource(statusLabel(complaint.status)), statusColor(complaint.status))
            }
            Spacer(Modifier.height(6.dp))
            Text(complaint.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                localTimestamp(complaint.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isAdmin) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComplaintStatus.all.forEach { status ->
                        FilterChip(
                            selected = complaint.status == status,
                            onClick = { onStatus(status) },
                            label = { Text(stringResource(statusLabel(status))) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RaiseComplaintDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onRaise: (category: String, description: String) -> Unit,
) {
    var category by remember { mutableStateOf(ComplaintRepository.categories.first()) }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.complaints_raise_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComplaintRepository.categories.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(categoryLabel(c)) })
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(2000) },
                    label = { Text(stringResource(Res.string.complaints_problem_field)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onRaise(category, description) }, enabled = description.isNotBlank() && !busy) {
                Text(stringResource(if (busy) Res.string.action_sending else Res.string.action_submit))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
