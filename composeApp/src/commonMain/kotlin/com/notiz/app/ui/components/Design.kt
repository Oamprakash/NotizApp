package com.notiz.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notiz.app.ui.theme.Palette

// Building blocks of the "Neighbourly" redesign, shared by every screen.

/** Barely-there shadow that lifts white cards off the grey ground (instead of outlines). */
fun Modifier.softShadow(shape: androidx.compose.ui.graphics.Shape, elevation: Dp = 3.dp): Modifier =
    shadow(elevation, shape, clip = false, ambientColor = Color(0x2214201C), spotColor = Color(0x2214201C))

/** White rounded card with a soft shadow. Clickable when [onClick] is set. */
@Composable
fun NotizCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    radius: Dp = 20.dp,
    color: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .fillMaxWidth()
            .then(if (color == Color.White) Modifier.softShadow(shape) else Modifier)
            .clip(shape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Rounded square with a tinted background holding an icon (quick actions, list rows). */
@Composable
fun IconTile(icon: ImageVector, tint: Color, background: Color, size: Dp = 44.dp, iconSize: Dp = 22.dp, radius: Dp = 14.dp) {
    Box(
        Modifier.size(size).background(background, RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Small uppercase label like "PINNED" or "OPEN POLL". */
@Composable
fun Tag(text: String, color: Color = Palette.amberText, background: Color = Palette.amberSoft) {
    Text(
        text.uppercase(),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.3.sp,
        modifier = Modifier.background(background, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Rounded status chip ("Paid", "In progress"). */
@Composable
fun Chip(text: String, color: Color, background: Color = color.copy(alpha = 0.12f), modifier: Modifier = Modifier) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Orange count bubble for tabs and list rows. Hidden when [count] is 0. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier.heightIn(min = 18.dp).background(Palette.orange, RoundedCornerShape(9.dp)).padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else count.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** Section title with an optional action on the right ("See all"). */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontSize = 17.sp, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                color = Palette.teal,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(8.dp),
            )
        }
    }
}

/** Small grey caps label above a group of settings rows. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = Palette.muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.3.sp,
        modifier = modifier.padding(start = 4.dp),
    )
}

private val avatarColors = listOf(
    Palette.purple to Palette.purpleSoft,
    Palette.blue to Palette.blueSoft,
    Palette.teal to Palette.tealSoft,
    Palette.amberText to Palette.amberSoft,
    Palette.red to Palette.redSoft,
)

/** The colour pair used for a person, stable per name (avatars, chat names). */
fun personColors(name: String): Pair<Color, Color> =
    avatarColors[(name.fold(0) { acc, c -> acc * 31 + c.code } and Int.MAX_VALUE) % avatarColors.size]

/** Circle with the person's initial. [solid] = filled teal (your own avatar). */
@Composable
fun Avatar(name: String, size: Dp = 44.dp, solid: Boolean = false) {
    val (fg, bg) = if (solid) Color.White to Palette.teal else personColors(name)
    Box(Modifier.size(size).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.4f).sp,
        )
    }
}

/** Two or three options in a pill track, e.g. "Notices | Polls" or "English | தமிழ்". */
@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, height: Dp = 40.dp) {
    Row(
        modifier.fillMaxWidth().background(Palette.track, RoundedCornerShape(14.dp)).padding(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) Color.White else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) Palette.ink else Palette.inkSoft,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Filter pill: dark when selected. */
@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Palette.ink else Color.White)
            .border(1.dp, if (selected) Palette.ink else Color(0xFFD5DDD9), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color.White else Palette.inkSoft, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
    }
}

/** Filled teal action. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 48.dp,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .height(height)
            .clip(shape)
            .background(if (enabled) Palette.teal else Color(0xFFB9C5C0), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Box(Modifier.size(8.dp))
        }
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

/** Outlined secondary action (48 dp). [accent] = teal outline and text. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    icon: ImageVector? = null,
    height: Dp = 48.dp,
) {
    val shape = RoundedCornerShape(14.dp)
    val content = if (accent) Palette.teal else Palette.ink
    Row(
        modifier
            .height(height)
            .clip(shape)
            .background(if (accent) Color.White else Palette.chip, shape)
            .then(if (accent) Modifier.border(1.5.dp, Palette.teal, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Box(Modifier.size(6.dp))
        }
        Text(text, color = content, fontSize = 14.sp, fontWeight = if (accent) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A tappable row inside a grouped card: optional leading tile, title, subtitle, trailing slot. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    titleColor: Color = Palette.ink,
    showChevron: Boolean = false,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(title, color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (subtitle != null) Text(subtitle, color = Palette.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            trailing()
            if (showChevron) Icon(com.notiz.app.ui.theme.NotizIcons.ChevronRight, contentDescription = null, tint = Color(0xFF8A968F), modifier = Modifier.size(20.dp))
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.lineSoft))
    }
}

/** White grouped container for [ListRow]s. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        modifier = modifier.fillMaxWidth().softShadow(RoundedCornerShape(18.dp)),
    ) {
        Column(content = content)
    }
}

/** Screen title block used at the top of each tab (no app bar). */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) Text(subtitle, color = Palette.muted, fontSize = 14.sp)
        }
        trailing()
    }
}

/** Floating action for the main "create" action of a tab (New notice, Raise bill…). */
@Composable
fun NotizFab(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector = com.notiz.app.ui.theme.NotizIcons.Plus) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .height(54.dp)
            .shadow(10.dp, shape, ambientColor = Palette.teal, spotColor = Palette.teal)
            .clip(shape)
            .background(Palette.teal)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Back arrow + title for pushed screens (Members, Payments, Notice, Settings). */
@Composable
fun DetailTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(Palette.ground).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
            .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.IconButton(onClick = onBack) {
            Icon(com.notiz.app.ui.theme.NotizIcons.ChevronLeft, contentDescription = org.jetbrains.compose.resources.stringResource(com.notiz.app.resources.Res.string.action_back))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
    }
}
