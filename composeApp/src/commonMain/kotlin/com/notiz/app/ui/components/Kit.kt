package com.notiz.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.notiz.app.ui.theme.NotizIcons
import com.notiz.app.ui.theme.Palette

// Notiz's own controls, so no stock Material widget shows in the app. The parameter names match
// the Material ones they replace (OutlinedTextField, AlertDialog, TextButton, DropdownMenu…).

/** Text field: label above, soft filled box, teal ring when focused, red when [isError]. */
@Composable
fun NotizTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = RoundedCornerShape(14.dp),
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val style = textStyle.copy(color = if (textStyle.color == Color.Unspecified) Palette.ink else textStyle.color)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            CompositionLocalProvider(
                LocalContentColor provides if (isError) Palette.red else Palette.inkSoft,
                LocalTextStyle provides TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            ) { Box(Modifier.padding(start = 2.dp)) { label() } }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            textStyle = style,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            interactionSource = interaction,
            cursorBrush = SolidColor(Palette.teal),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .background(if (focused) Color.White else Palette.chip, shape)
                        .border(
                            if (focused || isError) 1.5.dp else 1.dp,
                            when {
                                isError -> Palette.red
                                focused -> Palette.teal
                                else -> Color.Transparent
                            },
                            shape,
                        )
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    if (prefix != null) {
                        CompositionLocalProvider(LocalContentColor provides Palette.ink, LocalTextStyle provides style) { prefix() }
                        Spacer(Modifier.width(4.dp))
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            CompositionLocalProvider(LocalContentColor provides Color(0xFF8A968F), LocalTextStyle provides style) { placeholder() }
                        }
                        inner()
                    }
                }
            },
        )
        if (supportingText != null) {
            CompositionLocalProvider(
                LocalContentColor provides if (isError) Palette.red else Palette.muted,
                LocalTextStyle provides TextStyle(fontSize = 12.sp),
            ) { Box(Modifier.padding(start = 2.dp)) { supportingText() } }
        }
    }
}

/** Dialog card: rounded white sheet, bold title, calm body, actions on the right. */
@Composable
fun NotizDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .softShadow(RoundedCornerShape(26.dp), elevation = 12.dp)
                .background(Color.White, RoundedCornerShape(26.dp))
                .padding(start = 22.dp, end = 12.dp, top = 22.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (title != null) {
                CompositionLocalProvider(
                    LocalContentColor provides Palette.ink,
                    LocalTextStyle provides TextStyle(fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 24.sp),
                ) { Box(Modifier.padding(end = 10.dp)) { title() } }
            }
            if (text != null) {
                CompositionLocalProvider(
                    LocalContentColor provides Palette.inkSoft,
                    LocalTextStyle provides TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
                ) { Box(Modifier.weight(1f, fill = false).padding(end = 10.dp)) { text() } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

/** Text-only action in teal (dialog buttons, "Sign out", "Already paid?"). */
@Composable
fun NotizTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Palette.teal,
            LocalTextStyle provides TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
        ) { content() }
    }
}

/** Places a menu under its anchor, right-aligned, kept inside the window (flips up near the bottom). */
private class MenuPosition(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(0, maxX)
        val below = anchorBounds.bottom + gap
        val y = if (below + popupContentSize.height <= windowSize.height) below else (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

/** Pop-up menu card (replaces DropdownMenu). */
@Composable
fun NotizMenu(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (!expanded) return
    Popup(popupPositionProvider = remember { MenuPosition(gap = 8) }, onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true)) {
        Column(
            modifier
                .padding(6.dp)
                .widthIn(min = 200.dp, max = 280.dp)
                .softShadow(RoundedCornerShape(18.dp), elevation = 10.dp)
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(vertical = 6.dp),
            content = content,
        )
    }
}

/** A row in [NotizMenu]. */
@Composable
fun NotizMenuItem(text: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().heightIn(min = 46.dp).clickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Palette.ink,
            LocalTextStyle provides TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
        ) { text() }
    }
}

/** On/off toggle with a teal track. */
@Composable
fun NotizSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val thumbX by animateDpAsState(if (checked) 22.dp else 2.dp)
    Box(
        modifier
            .size(width = 50.dp, height = 30.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(15.dp))
            .background(if (checked) Palette.teal else Color(0xFFCBD5D1))
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
    ) {
        Box(Modifier.offset(x = thumbX, y = 3.dp).size(24.dp).softShadow(CircleShape, elevation = 2.dp).background(Color.White, CircleShape))
    }
}

/** Square tick box. */
@Composable
fun NotizCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (checked) Palette.teal else Color.White)
            .border(if (checked) 0.dp else 1.5.dp, if (checked) Color.Transparent else Color(0xFFB9C5C0), RoundedCornerShape(7.dp))
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(NotizIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

/** Thin rounded progress bar. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color = Palette.teal, track: Color = Palette.track, height: androidx.compose.ui.unit.Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(track)) {
        if (progress > 0f) Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

/**
 * Bottom sheet used for every "create" form (raise a request, raise a bill, new notice…):
 * white, rounded top, a small grey handle and a bold title; content scrolls above the keyboard.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NotizSheet(
    title: String,
    onDismissRequest: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { if (dismissible) onDismissRequest() },
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 8.dp).size(width = 40.dp, height = 4.dp).background(Color(0xFFD5DDD9), RoundedCornerShape(2.dp)))
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            androidx.compose.material3.Text(title, color = Palette.ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            content()
        }
    }
}

/** Small selectable pill for quick picks (bill names, due dates). */
@Composable
fun QuickPick(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Palette.tealSoft else Palette.chip)
            .border(if (selected) 1.5.dp else 0.dp, if (selected) Palette.teal else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text,
            color = if (selected) Palette.teal else Palette.ink,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}
