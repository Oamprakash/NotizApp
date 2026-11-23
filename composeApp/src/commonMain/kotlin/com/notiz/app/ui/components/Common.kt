package com.notiz.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.notiz.app.ui.theme.NotizIcons
import com.notiz.app.ui.theme.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.notiz.app.i18n.AppLanguage
import com.notiz.app.i18n.Language
import com.notiz.app.i18n.currentLanguage
import com.notiz.app.resources.*
import org.jetbrains.compose.resources.stringResource

/** Loading placeholder shaped like the screen's content (see [SkeletonKind]). */
@Composable
fun LoadingBox(modifier: Modifier = Modifier, kind: SkeletonKind = SkeletonKind.List) {
    Skeleton(kind, modifier)
}

/** Icon in a soft rounded tile, title, one line, and an optional button. */
@Composable
private fun StateMessage(
    icon: ImageVector,
    tint: Color,
    tileColor: Color,
    title: String,
    body: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).background(tileColor, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 14.sp, color = Palette.muted, textAlign = TextAlign.Center, lineHeight = 20.sp)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton(actionLabel, onClick = onAction)
        }
    }
}

/** Couldn't load: what went wrong (already translated) and Try again. */
@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    StateMessage(
        icon = NotizIcons.CloudOff,
        tint = Palette.red,
        tileColor = Palette.redSoft,
        title = stringResource(Res.string.error_load_title),
        body = message,
        actionLabel = stringResource(Res.string.action_try_again),
        onAction = onRetry,
        modifier = modifier,
    )
}

/** Nothing here yet, with an optional next step (e.g. "Post a notice"). */
@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = NotizIcons.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    StateMessage(icon, Palette.teal, Palette.tealSoft, title, subtitle, actionLabel, onAction, modifier)
}

/**
 * Empty state for use inside PullToRefreshBox. Pull-to-refresh only reacts to scrollable
 * content, so the message sits in a LazyColumn item that fills the screen.
 */
@Composable
fun RefreshableEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = NotizIcons.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    LazyColumn(modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillParentMaxSize()) {
                EmptyState(title, subtitle, icon = icon, actionLabel = actionLabel, onAction = onAction)
            }
        }
    }
}

/** Inline error message with a dismiss action. */
@Composable
fun ErrorBanner(message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier) =
    Banner(message, onDismiss, isError = true, modifier = modifier)

/** Inline confirmation message with a dismiss action. */
@Composable
fun InfoBanner(message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier) =
    Banner(message, onDismiss, isError = false, modifier = modifier)

@Composable
private fun Banner(message: String?, onDismiss: () -> Unit, isError: Boolean, modifier: Modifier) {
    if (message == null) return
    val container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        color = container,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                message,
                color = content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            NotizTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_dismiss)) }
        }
    }
}

/** Small rounded label, e.g. "Paid", "Pinned". */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) = Chip(text, color, modifier = modifier)

@Composable
fun UnreadDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .background(com.notiz.app.ui.theme.Palette.orange, RoundedCornerShape(50))
    )
}

/** Primary button that shows a spinner while [loading]. */
@Composable
fun LoadingButton(
    text: String,
    loading: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val active = enabled && !loading
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(if (enabled || loading) com.notiz.app.ui.theme.Palette.teal else Color(0xFFB9C5C0), shape)
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            NotizSpinner(color = Color.White)
        } else {
            Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Switches between English and Tamil. Shows the *other* language's name so it's readable by
 * someone who doesn't understand the current one.
 */
@Composable
fun LanguageToggle(modifier: Modifier = Modifier) {
    val current = currentLanguage()
    val other = if (current == Language.TAMIL) Language.ENGLISH else Language.TAMIL
    NotizTextButton(onClick = { AppLanguage.select(other) }, modifier = modifier) {
        Text(other.nativeName, fontWeight = FontWeight.SemiBold)
    }
}
