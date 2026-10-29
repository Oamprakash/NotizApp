@file:OptIn(ExperimentalMaterial3Api::class)

package com.notiz.app.ui.polls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.notiz.app.data.Poll
import com.notiz.app.data.PollOption
import com.notiz.app.push.OnPushUpdate
import com.notiz.app.push.PushTypes
import com.notiz.app.ui.BaseViewModel
import com.notiz.app.ui.SessionContext
import com.notiz.app.ui.components.ErrorBanner
import com.notiz.app.ui.components.ErrorBox
import com.notiz.app.ui.components.LoadingBox
import com.notiz.app.ui.components.RefreshableEmptyState
import com.notiz.app.i18n.localTimestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import com.notiz.app.resources.*
import org.jetbrains.compose.resources.stringResource
import com.notiz.app.i18n.UiText
import com.notiz.app.i18n.asString
import org.jetbrains.compose.resources.pluralStringResource

data class PollsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val polls: List<Poll> = emptyList(),
    val results: Map<String, Long> = emptyMap(), // optionId → votes
    val myVotes: Map<String, String> = emptyMap(), // pollId → optionId
    val loadError: UiText? = null,
    val actionError: UiText? = null,
    val busy: Boolean = false,
)

class PollsViewModel(
    private val container: AppContainer,
    private val session: SessionContext,
) : BaseViewModel() {
    val state = MutableStateFlow(PollsUiState())

    init { load() }

    fun load(pullToRefresh: Boolean = false) {
        state.update { it.copy(refreshing = pullToRefresh, loadError = null) }
        launchSafe(onError = { msg -> state.update { it.copy(loading = false, refreshing = false, loadError = msg) } }) {
            val polls = container.polls.polls(session.societyId)
            val results = container.polls.results(session.societyId)
            val mine = container.polls.myFlatVotes()
            state.update {
                it.copy(loading = false, refreshing = false, polls = polls, results = results, myVotes = mine)
            }
        }
    }

    fun vote(poll: Poll, option: PollOption) {
        if (state.value.myVotes[poll.id] == option.id) return
        state.update { it.copy(busy = true, actionError = null) }
        launchSafe(onError = { msg -> state.update { it.copy(busy = false, actionError = msg) } }) {
            container.polls.vote(poll.id, option.id)
            state.update { it.copy(busy = false) }
            load()
        }
    }

    fun create(question: String, options: List<String>, onDone: () -> Unit) {
        state.update { it.copy(busy = true, actionError = null) }
        launchSafe(onError = { msg -> state.update { it.copy(busy = false, actionError = msg) } }) {
            container.polls.create(session.societyId, question, options)
            state.update { it.copy(busy = false) }
            onDone()
            load()
        }
    }

    fun dismissError() = state.update { it.copy(actionError = null) }
}

@Composable
fun PollsTab(session: SessionContext, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val vm = viewModel { PollsViewModel(container, session) }
    val state by vm.state.collectAsState()
    OnPushUpdate(PushTypes.POLL) { vm.load() }
    var creating by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingBox()
            state.loadError != null && state.polls.isEmpty() -> ErrorBox(state.loadError!!.asString(), onRetry = { vm.load() })
            else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(pullToRefresh = true) }) {
                if (state.polls.isEmpty()) {
                    RefreshableEmptyState(
                        stringResource(Res.string.polls_empty_title),
                        if (session.isAdmin) {
                            stringResource(Res.string.polls_empty_admin)
                        } else {
                            stringResource(Res.string.polls_empty_resident)
                        },
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.polls, key = { it.id }) { poll ->
                            PollCard(
                                poll = poll,
                                results = state.results,
                                myOptionId = state.myVotes[poll.id],
                                enabled = !state.busy,
                                onVote = { vm.vote(poll, it) },
                            )
                        }
                    }
                }
            }
        }

        if (session.isAdmin) {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(Res.string.polls_new)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
        ErrorBanner(state.actionError?.asString(), onDismiss = vm::dismissError, modifier = Modifier.align(Alignment.TopCenter))
    }

    if (creating) {
        NewPollDialog(
            busy = state.busy,
            onDismiss = { creating = false },
            onCreate = { q, options -> vm.create(q, options) { creating = false } },
        )
    }
}

@Composable
private fun PollCard(
    poll: Poll,
    results: Map<String, Long>,
    myOptionId: String?,
    enabled: Boolean,
    onVote: (PollOption) -> Unit,
) {
    val total = poll.options.sumOf { results[it.id] ?: 0L }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(poll.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                pluralStringResource(Res.plurals.polls_flats_voted, total.toInt(), total) + " · " + localTimestamp(poll.createdAt, withTime = false),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            poll.options.forEach { option ->
                OptionRow(
                    option = option,
                    votes = results[option.id] ?: 0L,
                    total = total,
                    selected = option.id == myOptionId,
                    enabled = enabled,
                    onClick = { onVote(option) },
                )
            }
            Text(
                stringResource(if (myOptionId == null) Res.string.polls_tap_to_vote else Res.string.polls_voted_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** An option with its result drawn as a filled bar behind the label. */
@Composable
private fun OptionRow(option: PollOption, votes: Long, total: Long, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val fraction = if (total == 0L) 0f else votes.toFloat() / total
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().height(48.dp).clickable(enabled = enabled, onClick = onClick),
    ) {
        Box {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.22f else 0.10f))
            )
            Row(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    (if (selected) "✓ " else "") + option.label,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                Text("${(fraction * 100).toInt()}% · $votes", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun NewPollDialog(busy: Boolean, onDismiss: () -> Unit, onCreate: (String, List<String>) -> Unit) {
    var question by remember { mutableStateOf("") }
    val defaultOptions = stringResource(Res.string.polls_default_options)
    var optionsText by remember { mutableStateOf(defaultOptions) }
    val options = optionsText.lines().map { it.trim() }.filter { it.isNotEmpty() }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.polls_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = question, onValueChange = { question = it.take(300) },
                    label = { Text(stringResource(Res.string.polls_question_field)) }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = optionsText, onValueChange = { optionsText = it.take(1000) },
                    label = { Text(stringResource(Res.string.polls_options_field)) }, minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(0.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(question, options) },
                enabled = question.isNotBlank() && options.size >= 2 && !busy,
            ) { Text(stringResource(if (busy) Res.string.polls_creating else Res.string.polls_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
