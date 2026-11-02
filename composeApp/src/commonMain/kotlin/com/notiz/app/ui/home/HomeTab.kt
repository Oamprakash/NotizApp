package com.notiz.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notiz.app.data.DueStatus
import com.notiz.app.data.Notice
import com.notiz.app.i18n.asString
import com.notiz.app.i18n.localDate
import com.notiz.app.i18n.localizedMonths
import com.notiz.app.resources.*
import com.notiz.app.ui.SessionContext
import com.notiz.app.ui.components.ErrorBanner
import com.notiz.app.ui.components.LoadingBox
import com.notiz.app.ui.components.softShadow
import com.notiz.app.ui.theme.LightStatusBarIcons
import com.notiz.app.ui.theme.NotizIcons
import com.notiz.app.ui.theme.Palette
import com.notiz.app.util.formatRupees
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Where Home's buttons lead; MainNavigation / HomeScreen wire them up. */
class HomeActions(
    val openTab: (HomeTab) -> Unit,
    val openPolls: () -> Unit,
    val openNotice: (String) -> Unit,
    val openMembers: () -> Unit,
    val openSettings: () -> Unit,
)

private val Shadowed = RoundedCornerShape(18.dp)

/**
 * Home, v2 (design canvas "v2 — bolder Home"): a teal band with the greeting and the key number,
 * a white card overlapping it, then round quick actions and soft-shadow cards — no outlines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTabContent(
    session: SessionContext,
    state: HomeUiState,
    onRefresh: () -> Unit,
    onVote: (String) -> Unit,
    onDismissError: () -> Unit,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    LightStatusBarIcons()
    if (state.loading) {
        Column(modifier.fillMaxSize()) {
            Band(session, state.myName, actions.openSettings, bottomPadding = 24.dp) {}
            LoadingBox()
        }
        return
    }
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (session.isAdmin) CommitteeHome(session, state, actions) else ResidentHome(session, state, onVote, actions)
            ErrorBanner(state.error?.asString(), onDismiss = onDismissError)
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Pulls the next element up by [by] so it overlaps the teal band. */
private fun Modifier.overlapUp(by: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val px = by.roundToPx()
    layout(placeable.width, (placeable.height - px).coerceAtLeast(0)) { placeable.place(0, -px) }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun Band(
    session: SessionContext,
    myName: String,
    onOpenSettings: () -> Unit,
    bottomPadding: Dp,
    content: @Composable () -> Unit,
) {
    val first = myName.trim().substringBefore(' ').ifBlank { session.flatNo }
    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    val greeting = when {
        session.isAdmin -> stringResource(Res.string.home_hello, first)
        hour < 12 -> stringResource(Res.string.home_greeting_morning, first)
        hour < 17 -> stringResource(Res.string.home_greeting_afternoon, first)
        else -> stringResource(Res.string.home_greeting_evening, first)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.teal, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${session.society.name} · ${session.flatNo}" + if (session.isAdmin) " · " + stringResource(Res.string.home_committee) else "",
                    color = Palette.tealOnDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(greeting, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Palette.tealMid).border(2.dp, Color(0xFF5FA18F), CircleShape)
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Text(first.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        content()
    }
}

// ───────────────────────────── Committee ─────────────────────────────

@Composable
private fun CommitteeHome(session: SessionContext, state: HomeUiState, actions: HomeActions) {
    Band(session, state.myName, actions.openSettings, bottomPadding = 64.dp) {
        CollectionSummary(state, onDetails = { actions.openTab(HomeTab.Dues) })
    }
    // Floating tray of the four committee actions
    Row(
        Modifier
            .overlapUp(44.dp)
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .softShadow(RoundedCornerShape(22.dp), elevation = 8.dp)
            .background(Color.White, RoundedCornerShape(22.dp))
            .padding(vertical = 14.dp, horizontal = 4.dp),
    ) {
        RoundAction(NotizIcons.Notice, Color.White, Palette.teal, stringResource(Res.string.home_tray_notice), Modifier.weight(1f)) {
            actions.openTab(HomeTab.Notices)
        }
        RoundAction(NotizIcons.Rupee, Palette.amberText, Palette.amberSoft, stringResource(Res.string.home_raise_bill), Modifier.weight(1f)) {
            actions.openTab(HomeTab.Dues)
        }
        RoundAction(NotizIcons.Poll, Palette.purple, Palette.purpleSoft, stringResource(Res.string.home_tray_poll), Modifier.weight(1f), onClick = actions.openPolls)
        RoundAction(NotizIcons.UserPlus, Palette.blue, Palette.blueSoft, stringResource(Res.string.home_tray_invite), Modifier.weight(1f), onClick = actions.openMembers)
    }

    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.myDue?.takeIf { it.status == DueStatus.PENDING }?.let { due ->
            Row(
                Modifier.fillMaxWidth().softShadow(Shadowed).clip(Shadowed).background(Palette.amberSoft)
                    .clickable { actions.openTab(HomeTab.Dues) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(NotizIcons.Rupee, contentDescription = null, tint = Palette.amberText, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(Res.string.home_your_flat_due, formatRupees(due.amountPaise), localDate(due.dueDate)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(Res.string.home_pay_now), color = Palette.teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        SectionTitle(stringResource(Res.string.home_attention))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AttentionTile(
                    count = state.openRequests,
                    icon = NotizIcons.Wrench,
                    title = stringResource(Res.string.home_att_requests),
                    subtitle = state.oldestOpenRequest?.let { stringResource(Res.string.home_att_requests_sub, it.category) }
                        ?: stringResource(Res.string.home_calm_requests),
                    fg = Palette.red,
                    bg = Palette.redSoft,
                    modifier = Modifier.weight(1f),
                ) { actions.openTab(HomeTab.HelpDesk) }
                AttentionTile(
                    count = state.toVerify,
                    icon = NotizIcons.Rupee,
                    title = stringResource(Res.string.home_att_verify),
                    subtitle = stringResource(if (state.toVerify > 0) Res.string.home_att_verify_sub else Res.string.home_calm_verify),
                    fg = Palette.amberText,
                    bg = Palette.amberSoft,
                    modifier = Modifier.weight(1f),
                ) { actions.openTab(HomeTab.Dues) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AttentionTile(
                    count = state.joinRequests,
                    icon = NotizIcons.UserPlus,
                    title = stringResource(Res.string.home_att_join),
                    subtitle = if (state.joinRequests > 0) state.joinNames.take(2).joinToString(", ") else stringResource(Res.string.home_calm_join),
                    fg = Palette.blue,
                    bg = Palette.blueSoft,
                    modifier = Modifier.weight(1f),
                    onClick = actions.openMembers,
                )
                AttentionTile(
                    count = state.reportedMessages,
                    icon = NotizIcons.Flag,
                    title = stringResource(Res.string.home_att_reports),
                    subtitle = stringResource(if (state.reportedMessages > 0) Res.string.home_att_reports_sub else Res.string.home_calm_chat),
                    fg = Palette.purple,
                    bg = Palette.purpleSoft,
                    modifier = Modifier.weight(1f),
                ) { actions.openTab(HomeTab.Chat) }
            }
        }

        LatestNotice(state, actions)
    }
}

@Composable
private fun CollectionSummary(state: HomeUiState, onDetails: () -> Unit) {
    val bill = state.bill
    if (bill == null) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.home_no_bills), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(stringResource(Res.string.home_no_bills_body), color = Palette.tealOnDark, fontSize = 13.sp)
        }
        return
    }
    val total = bill.totalPaise.coerceAtLeast(1)
    val share = (bill.collectedPaise.toFloat() / total).coerceIn(0f, 1f)
    Column(Modifier.clickable(onClick = onDetails), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(Res.string.home_collected, bill.title),
            color = Palette.tealOnDark,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatRupees(bill.collectedPaise), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            Text(
                stringResource(Res.string.home_of_total, formatRupees(bill.totalPaise)) + " · ${(share * 100).toInt()}%",
                color = Palette.tealOnDark,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.tealMid)) {
            if (share > 0f) Box(Modifier.fillMaxWidth(share).fillMaxHeight().background(Palette.marigold))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BandStat(bill.paidFlats, stringResource(Res.string.home_stat_paid))
            BandStat(bill.verifyFlats, stringResource(Res.string.home_to_verify))
            BandStat(bill.unpaidFlats, stringResource(Res.string.home_unpaid))
        }
    }
}

@Composable
private fun BandStat(value: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(label, color = Palette.tealOnDark, fontSize = 13.sp)
    }
}

/** One of the four "Needs your attention" tiles: coloured when something waits, calm grey at zero. */
@Composable
private fun AttentionTile(
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    fg: Color,
    bg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val active = count > 0
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .then(if (active) Modifier else Modifier.softShadow(shape, elevation = 2.dp))
            .clip(shape)
            .background(if (active) bg else Color.White)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                count.toString(),
                color = if (active) fg else Color(0xFF9AAAA3),
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
            Icon(icon, contentDescription = null, tint = if (active) fg else Color(0xFF9AAAA3), modifier = Modifier.size(20.dp))
        }
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, fontSize = 12.sp, color = Palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ───────────────────────────── Resident ─────────────────────────────

@Composable
private fun ResidentHome(session: SessionContext, state: HomeUiState, onVote: (String) -> Unit, actions: HomeActions) {
    Band(session, state.myName, actions.openSettings, bottomPadding = 92.dp) {}
    DueCard(state, Modifier.overlapUp(76.dp).padding(horizontal = 16.dp), onPay = { actions.openTab(HomeTab.Dues) })

    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth()) {
            RoundAction(NotizIcons.Wrench, Palette.amberText, Palette.amberSoft, stringResource(Res.string.home_tray_request), Modifier.weight(1f)) {
                actions.openTab(HomeTab.HelpDesk)
            }
            RoundAction(NotizIcons.Poll, Palette.purple, Palette.purpleSoft, stringResource(Res.string.home_action_polls), Modifier.weight(1f), onClick = actions.openPolls)
            RoundAction(NotizIcons.Users, Palette.blue, Palette.blueSoft, stringResource(Res.string.home_action_neighbours), Modifier.weight(1f), onClick = actions.openMembers)
            RoundAction(NotizIcons.Download, Palette.teal, Palette.tealSoft, stringResource(Res.string.home_action_receipts), Modifier.weight(1f)) {
                actions.openTab(HomeTab.Dues)
            }
        }
        LatestNotice(state, actions)
        PollCard(state, onVote, actions.openPolls)
    }
}

@Composable
private fun DueCard(state: HomeUiState, modifier: Modifier, onPay: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth().softShadow(shape, elevation = 8.dp).background(Color.White, shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val due = state.myDue
        if (due == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(Palette.tealSoft, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(NotizIcons.Check, contentDescription = null, tint = Palette.teal, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text(stringResource(Res.string.home_all_paid), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Text(stringResource(Res.string.home_all_paid_body), color = Palette.muted, fontSize = 13.sp)
                }
            }
            return@Column
        }
        val days = state.myDueDaysLeft
        val verifying = due.status == DueStatus.SUBMITTED
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(due.title, color = Palette.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val overdue = days != null && days < 0 && !verifying
            val chip = when {
                verifying -> stringResource(Res.string.home_verifying)
                days == null -> null
                days < 0 -> stringResource(Res.string.home_overdue)
                days == 0 -> stringResource(Res.string.home_due_today)
                days == 1 -> stringResource(Res.string.home_due_tomorrow)
                else -> stringResource(Res.string.home_days_left, days)
            }
            if (chip != null) {
                Text(
                    chip,
                    color = when {
                        overdue -> Color.White
                        verifying -> Palette.blueText
                        else -> Palette.onMarigold
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(
                            when {
                                overdue -> Palette.red
                                verifying -> Palette.blueSoft
                                else -> Palette.marigold
                            },
                            RoundedCornerShape(50),
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatRupees(due.amountPaise), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            Text(stringResource(Res.string.home_due_on, localDate(due.dueDate)), color = Palette.muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (!verifying) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp)).background(Palette.teal).clickable(onClick = onPay),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(Res.string.home_pay_now), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                Text(
                    stringResource(Res.string.home_already_paid_q),
                    color = Palette.teal,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onPay).padding(horizontal = 10.dp, vertical = 12.dp),
                )
            }
        }
    }
}

// ───────────────────────────── Shared ─────────────────────────────

@Composable
private fun RoundAction(icon: ImageVector, tint: Color, background: Color, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(50.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.ink)
    }
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                color = Palette.teal,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp),
            )
        }
    }
}

@Composable
private fun LatestNotice(state: HomeUiState, actions: HomeActions) {
    SectionTitle(stringResource(Res.string.home_latest_notice), action = stringResource(Res.string.home_see_all)) {
        actions.openTab(HomeTab.Notices)
    }
    val notice = state.latestNotice
    if (notice == null) {
        Text(
            stringResource(Res.string.home_no_notices),
            color = Palette.muted,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().softShadow(Shadowed).background(Color.White, Shadowed).padding(16.dp),
        )
        return
    }
    NoticeRow(notice, unread = state.latestNoticeUnread) { actions.openNotice(notice.id) }
}

@Composable
private fun NoticeRow(notice: Notice, unread: Boolean, onClick: () -> Unit) {
    val months = localizedMonths()
    val day = notice.createdAt.take(10).split("-").getOrNull(2) ?: ""
    val month = notice.createdAt.take(10).split("-").getOrNull(1)?.toIntOrNull()?.let { months.getOrNull(it - 1) } ?: ""
    Row(
        Modifier.fillMaxWidth().softShadow(Shadowed).clip(Shadowed).background(Color.White).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.width(52.dp).height(56.dp).background(Palette.tealSoft, RoundedCornerShape(14.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(day, color = Palette.teal, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp)
            Text(month.uppercase(), color = Palette.teal, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (unread) Box(Modifier.size(7.dp).background(Palette.orange, CircleShape))
                Text(notice.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (notice.body.isNotBlank()) {
                Text(notice.body, color = Palette.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(NotizIcons.ChevronRight, contentDescription = null, tint = Color(0xFF9AAAA3), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PollCard(state: HomeUiState, onVote: (String) -> Unit, onOpenPolls: () -> Unit) {
    val poll = state.poll ?: return
    Column(
        Modifier.fillMaxWidth().softShadow(Shadowed).clip(Shadowed).background(Color.White).clickable(onClick = onOpenPolls).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(28.dp).background(Palette.purpleSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(NotizIcons.Poll, contentDescription = null, tint = Palette.purple, modifier = Modifier.size(16.dp))
            }
            Text(poll.question, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        val options = poll.options.take(4)
        // Up to 3 short options side by side, otherwise stacked
        val inRow = options.size <= 3 && options.all { it.label.length <= 12 }
        val pills: @Composable (Modifier) -> Unit = { m ->
            options.forEach { option ->
                val selected = state.myVote == option.id
                Box(
                    m.height(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Palette.teal else Palette.chip)
                        .clickable(enabled = !state.votingBusy) { onVote(option.id) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.label + if (selected) " ✓" else "",
                        color = if (selected) Color.White else Palette.ink,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (inRow) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { pills(Modifier.weight(1f)) }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { pills(Modifier.fillMaxWidth()) }
        }
        Text(
            if (state.myVote != null) stringResource(Res.string.home_voted, state.pollVotes.toInt()) else stringResource(Res.string.home_one_vote),
            color = Palette.muted,
            fontSize = 12.sp,
        )
    }
}
