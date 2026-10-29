package com.notiz.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notiz.app.AppContainer
import com.notiz.app.LocalAppContainer
import com.notiz.app.data.Message
import com.notiz.app.ui.BaseViewModel
import com.notiz.app.ui.SessionContext
import com.notiz.app.ui.components.EmptyState
import com.notiz.app.ui.components.ErrorBanner
import com.notiz.app.ui.components.ErrorBox
import com.notiz.app.ui.components.LoadingBox
import com.notiz.app.util.formatTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val loading: Boolean = true,
    val messages: List<Message> = emptyList(),
    val loadError: String? = null,
    val sendError: String? = null,
    val sending: Boolean = false,
)

class ChatViewModel(
    private val container: AppContainer,
    private val session: SessionContext,
) : BaseViewModel() {
    val state = MutableStateFlow(ChatUiState())

    init {
        load()
        // Live updates; RLS ensures only this society's members receive them.
        viewModelScope.launch {
            container.chat.newMessages(session.societyId)
                .catch { /* realtime dropped — pull-to-load still works */ }
                .collect { msg -> addMessage(msg) }
        }
    }

    fun load() {
        state.update { it.copy(loading = true, loadError = null) }
        launchSafe(onError = { msg -> state.update { it.copy(loading = false, loadError = msg) } }) {
            val recent = container.chat.recent(session.societyId)
            state.update { s ->
                // Keep any live messages that arrived while loading
                val merged = (recent + s.messages).distinctBy { it.id }.sortedBy { it.createdAt }
                s.copy(loading = false, messages = merged)
            }
        }
    }

    private fun addMessage(msg: Message) = state.update { s ->
        if (s.messages.any { it.id == msg.id }) s else s.copy(messages = s.messages + msg)
    }

    fun send(text: String, onSent: () -> Unit) {
        if (text.isBlank()) return
        state.update { it.copy(sending = true, sendError = null) }
        launchSafe(onError = { msg -> state.update { it.copy(sending = false, sendError = msg) } }) {
            val sent = container.chat.sendMessage(session.societyId, text)
            addMessage(sent) // shows instantly even if realtime is slow
            state.update { it.copy(sending = false) }
            onSent()
        }
    }

    fun dismissError() = state.update { it.copy(sendError = null) }
}

@Composable
fun ChatTab(session: SessionContext, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val vm = viewModel { ChatViewModel(container, session) }
    val state by vm.state.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Stick to the newest message
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.messages.isEmpty() -> LoadingBox()
                state.loadError != null && state.messages.isEmpty() -> ErrorBox(state.loadError!!, onRetry = vm::load)
                state.messages.isEmpty() -> EmptyState("Say hello 👋", "Messages here are visible to every approved member.")
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(message, mine = message.senderId == session.userId)
                    }
                }
            }
        }
        ErrorBanner(state.sendError, onDismiss = vm::dismissError)
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(4000) },
                placeholder = { Text("Message") },
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(
                onClick = { vm.send(draft) { draft = "" } },
                enabled = draft.isNotBlank() && !state.sending,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (mine) 16.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 16.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!mine) {
                    Text(
                        "${message.senderName} · ${message.flatNo}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(message.body, style = MaterialTheme.typography.bodyLarge)
                Text(
                    formatTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}
