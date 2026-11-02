package com.notiz.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontStyle
import com.notiz.app.ui.components.InfoBanner
import com.notiz.app.i18n.uiText
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import com.notiz.app.resources.*
import org.jetbrains.compose.resources.stringResource
import com.notiz.app.i18n.UiText
import com.notiz.app.i18n.asString

data class ChatUiState(
    val loading: Boolean = true,
    val messages: List<Message> = emptyList(),
    val loadError: UiText? = null,
    val sendError: UiText? = null,
    val info: UiText? = null,
    val sending: Boolean = false,
    /** The message being answered (set by swiping it right or "Reply" in its menu). */
    val replyingTo: Message? = null,
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
            container.chat.liveMessages(session.societyId)
                .catch { /* realtime dropped — pull-to-load still works */ }
                .collect { msg -> upsertMessage(msg) }
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

    /** New message, or a change to one we have (e.g. deleted). */
    private fun upsertMessage(msg: Message) = state.update { s ->
        if (s.messages.any { it.id == msg.id }) {
            s.copy(messages = s.messages.map { if (it.id == msg.id) msg else it })
        } else {
            s.copy(messages = s.messages + msg)
        }
    }

    private fun moderate(block: suspend () -> UiText?) {
        state.update { it.copy(sendError = null, info = null) }
        launchSafe(onError = { msg -> state.update { it.copy(sendError = msg) } }) {
            val info = block()
            state.update { it.copy(info = info) }
        }
    }

    fun delete(message: Message) = moderate {
        container.chat.deleteMessage(message.id)
        // Show the stub now even if realtime is slow
        state.update { s ->
            s.copy(
                messages = s.messages.map {
                    when {
                        it.id == message.id -> it.copy(body = "", deletedAt = "now", deletedBy = session.userId)
                        it.replyToId == message.id -> it.copy(replyBody = "")
                        else -> it
                    }
                },
                replyingTo = s.replyingTo?.takeIf { it.id != message.id },
            )
        }
        null
    }

    fun report(message: Message) = moderate {
        val first = container.chat.reportMessage(message.id)
        uiText(if (first) Res.string.chat_reported else Res.string.chat_already_reported)
    }

    fun mute(message: Message) = moderate {
        container.chat.setMuted(session.societyId, message.senderId, true)
        uiText(Res.string.chat_muted_member, message.senderName.ifBlank { message.flatNo })
    }

    fun replyTo(message: Message?) = state.update { it.copy(replyingTo = message) }

    fun send(text: String, onSent: () -> Unit) {
        if (text.isBlank()) return
        val replyTo = state.value.replyingTo
        state.update { it.copy(sending = true, sendError = null) }
        launchSafe(onError = { msg -> state.update { it.copy(sending = false, sendError = msg) } }) {
            val sent = container.chat.sendMessage(session.societyId, text, replyTo?.id)
            addMessage(sent) // shows instantly even if realtime is slow
            state.update { it.copy(sending = false, replyingTo = null) }
            onSent()
        }
    }

    fun dismissError() = state.update { it.copy(sendError = null, info = null) }
}

@Composable
fun ChatTab(session: SessionContext, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val vm = viewModel { ChatViewModel(container, session) }
    val state by vm.state.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    var confirmDelete by remember { mutableStateOf<Message?>(null) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var confirmMute by remember { mutableStateOf<Message?>(null) }

    confirmDelete?.let { message ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            text = { Text(stringResource(Res.string.chat_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; vm.delete(message) }) {
                    Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(Res.string.action_keep)) } },
        )
    }
    confirmMute?.let { message ->
        AlertDialog(
            onDismissRequest = { confirmMute = null },
            text = { Text(stringResource(Res.string.chat_mute_confirm, message.senderName, message.flatNo)) },
            confirmButton = {
                TextButton(onClick = { confirmMute = null; vm.mute(message) }) { Text(stringResource(Res.string.chat_mute)) }
            },
            dismissButton = { TextButton(onClick = { confirmMute = null }) { Text(stringResource(Res.string.action_keep)) } },
        )
    }

    // Stick to the newest message
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.messages.isEmpty() -> LoadingBox()
                state.loadError != null && state.messages.isEmpty() -> ErrorBox(state.loadError!!.asString(), onRetry = vm::load)
                state.messages.isEmpty() -> EmptyState(stringResource(Res.string.chat_empty_title), stringResource(Res.string.chat_empty_body))
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        val mine = message.senderId == session.userId
                        val canReply = !message.isDeleted && !session.membership.chatMuted
                        MessageBubble(
                            message = message,
                            mine = mine,
                            canReply = canReply,
                            onReply = {
                                vm.replyTo(message)
                                focusRequester.requestFocusSafely()
                            },
                            onOpenQuote = { id ->
                                val index = state.messages.indexOfFirst { it.id == id }
                                if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                            },
                            canDelete = !message.isDeleted && (mine || session.isAdmin),
                            canReport = !message.isDeleted && !mine,
                            canMute = !message.isDeleted && !mine && session.isAdmin,
                            onDelete = { confirmDelete = message },
                            onReport = { vm.report(message) },
                            onMute = { confirmMute = message },
                        )
                    }
                }
            }
        }
        ErrorBanner(state.sendError?.asString(), onDismiss = vm::dismissError)
        InfoBanner(state.info?.asString(), onDismiss = vm::dismissError)
        HorizontalDivider()
        if (session.membership.chatMuted) {
            Text(
                stringResource(Res.string.chat_you_are_muted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        } else {
            state.replyingTo?.let { quoted ->
                ReplyBar(
                    senderName = if (quoted.senderId == session.userId) stringResource(Res.string.chat_you) else quoted.senderName,
                    body = quoted.body,
                    onClose = { vm.replyTo(null) },
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(4000) },
                    placeholder = { Text(stringResource(Res.string.chat_message_hint)) },
                    maxLines = 4,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
                FilledIconButton(
                    onClick = { vm.send(draft) { draft = "" } },
                    enabled = draft.isNotBlank() && !state.sending,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(Res.string.chat_send))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    mine: Boolean,
    canReply: Boolean,
    onReply: () -> Unit,
    onOpenQuote: (String) -> Unit,
    canDelete: Boolean,
    canReport: Boolean,
    canMute: Boolean,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onMute: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val hasActions = canReply || canDelete || canReport || canMute
    // Others' messages (left side) swipe right; your own (right side) swipe left — towards the middle
    val swipe = rememberSwipeToReply(enabled = canReply, towardsStart = mine, onReply = onReply)
    Box(Modifier.fillMaxWidth()) {
        // Reply arrow revealed behind the message while it's dragged
        Icon(
            ReplyIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(if (mine) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 8.dp)
                .graphicsLayer { alpha = swipe.progress; scaleX = 0.6f + 0.4f * swipe.progress; scaleY = scaleX },
        )
        Row(
            Modifier.fillMaxWidth().then(swipe.modifier),
            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        ) {
            Box {
                Surface(
                    color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (mine) 16.dp else 4.dp,
                        bottomEnd = if (mine) 4.dp else 16.dp,
                    ),
                    modifier = Modifier.widthIn(max = 300.dp)
                        .combinedClickable(enabled = hasActions, onClick = {}, onLongClick = { menu = true }),
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
                        if (message.replyToId != null && !message.isDeleted) {
                            QuotedMessage(
                                senderName = message.replySenderName.orEmpty(),
                                body = message.replyBody.orEmpty(),
                                mine = mine,
                                onClick = { onOpenQuote(message.replyToId) },
                            )
                        }
                        if (message.isDeleted) {
                            Text(
                                stringResource(
                                    if (message.deletedBy != null && message.deletedBy != message.senderId) {
                                        Res.string.chat_deleted_by_admin
                                    } else {
                                        Res.string.chat_deleted
                                    },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(message.body, style = MaterialTheme.typography.bodyLarge)
                        }
                        Text(
                            formatTime(message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (canReply) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.chat_reply)) }, onClick = { menu = false; onReply() })
                    }
                    if (canDelete) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.action_delete)) }, onClick = { menu = false; onDelete() })
                    }
                    if (canReport) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.chat_report)) }, onClick = { menu = false; onReport() })
                    }
                    if (canMute) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.chat_mute)) }, onClick = { menu = false; onMute() })
                    }
                }
            }
        }
    }
}

/** "Replying to …" strip above the message box. */
@Composable
private fun ReplyBar(senderName: String, body: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(36.dp).background(MaterialTheme.colorScheme.primary))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                stringResource(Res.string.chat_replying_to, senderName),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(body, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.chat_cancel_reply))
        }
    }
}

/** The quoted message at the top of a reply. Tap to jump to it. */
@Composable
private fun QuotedMessage(senderName: String, body: String, mine: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.padding(bottom = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            )
            .clickable(onClick = onClick)
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                senderName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                body.ifEmpty { stringResource(Res.string.chat_deleted) },
                style = MaterialTheme.typography.bodySmall,
                fontStyle = if (body.isEmpty()) FontStyle.Italic else FontStyle.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private class SwipeToReply(val modifier: Modifier, private val offsetPx: () -> Float, private val triggerPx: Float) {
    /** 0 → 1 as the drag approaches the reply point. */
    val progress: Float get() = (offsetPx() / triggerPx).coerceIn(0f, 1f)
}

/**
 * Drag a message sideways to reply, like WhatsApp: to the right for others' messages, to the left
 * ([towardsStart]) for your own. A light vibration when it's far enough, reply on release, and the
 * message springs back.
 */
@Composable
private fun rememberSwipeToReply(enabled: Boolean, towardsStart: Boolean, onReply: () -> Unit): SwipeToReply {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val triggerPx = with(density) { 64.dp.toPx() }
    val maxPx = with(density) { 96.dp.toPx() }
    // Distance dragged in the reply direction (always ≥ 0); drawn as a negative x offset for your own
    val offset = remember { Animatable(0f) }
    val sign = if (towardsStart) -1f else 1f
    val reply by rememberUpdatedState(onReply)
    var armed by remember { mutableStateOf(false) }

    val modifier = Modifier
        .offset { IntOffset((sign * offset.value).roundToInt(), 0) }
        .pointerInput(enabled, towardsStart) {
            if (!enabled) return@pointerInput
            detectHorizontalDragGestures(
                onDragStart = { armed = false },
                onDragEnd = {
                    if (armed) reply()
                    armed = false
                    scope.launch { offset.animateTo(0f) }
                },
                onDragCancel = {
                    armed = false
                    scope.launch { offset.animateTo(0f) }
                },
            ) { change, dragAmount ->
                // Only in the reply direction, resisting once past the reply point
                val delta = sign * dragAmount
                val next = (offset.value + delta * if (offset.value > triggerPx) 0.4f else 1f).coerceIn(0f, maxPx)
                if (next != offset.value) change.consume()
                scope.launch { offset.snapTo(next) }
                if (!armed && next >= triggerPx) {
                    armed = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                } else if (armed && next < triggerPx * 0.8f) {
                    armed = false // dragged back: cancel
                }
            }
        }
    return SwipeToReply(modifier, { offset.value }, triggerPx)
}

private fun FocusRequester.requestFocusSafely() {
    runCatching { requestFocus() }
}

/** Material "reply" arrow (not in the core icon set). */
private val ReplyIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Reply", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = PathParser().parsePathString("M10,9V5l-7,7 7,7v-4.1c5,0 8.5,1.6 11,5.1 -1,-5 -4,-10 -11,-11z").toNodes(),
            fill = SolidColor(Color.Black),
        )
        .build()
}
