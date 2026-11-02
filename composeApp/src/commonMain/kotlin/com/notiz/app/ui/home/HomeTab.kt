package com.notiz.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notiz.app.data.DueStatus
import com.notiz.app.i18n.asString
import com.notiz.app.i18n.localDate
import com.notiz.app.i18n.localTimestamp
import com.notiz.app.resources.*
import com.notiz.app.ui.SessionContext
import com.notiz.app.ui.components.Avatar
import com.notiz.app.ui.components.CountBadge
import com.notiz.app.ui.components.ErrorBanner
import com.notiz.app.ui.components.GroupCard
import com.notiz.app.ui.components.IconTile
import com.notiz.app.ui.components.ListRow
import com.notiz.app.ui.components.LoadingBox
import com.notiz.app.ui.components.NotizCard
import com.notiz.app.ui.components.PrimaryButton
import com.notiz.app.ui.components.SecondaryButton
import com.notiz.app.ui.components.SectionHeader
import com.notiz.app.ui.components.Tag
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
    if (state.loading) {
        LoadingBox(modifier)
        return
    }
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Header(session, state.myName, actions.openSettings)
            ErrorBanner(state.error?.asString(), onDismiss = onDismissError)
            if (session.isAdmin) {
                CommitteeHome(state, actions)
            } else {
                ResidentHome(state, onVote, actions)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun Header(session: SessionContext, myName: String, onOpenSettings: () -> Unit) {
    val first = myName.trim().substringBefore(' ').ifBlank { session.flatNo }
    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    val greeting = when {
        session.isAdmin -> stringResource(Res.string.home_hello, first)
        hour < 12 -> stringResource(Res.string.home_greeting_morning, first)
        hour < 17 -> stringResource(Res.string.home_greeting_afternoon, first)
        else -> stringResource(Res.string.home_greeting_evening, first)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${session.society.name} · ${session.flatNo}",
                    color = Palette.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (session.isAdmin) Tag(stringResource(Res.string.home_admin_tag), color = Color.White, background = Palette.ink)
            }
            Text(greeting, style = MaterialTheme.typography.headlineSmall, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.clip(CircleShape).clickable(onClick = onOpenSettings)) {
            Avatar(myName.ifBlank { session.flatNo }, solid = true)
        }
    }
}

// ───────────────────────────── Resident ─────────────────────────────

@Composable
private fun ResidentHome(state: HomeUiState, onVote: (String) -> Unit, actions: HomeActions) {
    DueHero(state, onPay = { actions.openTab(HomeTab.Dues) })

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickAction(NotizIcons.Wrench, Palette.amberText, Palette.amberSoft, stringResource(Res.string.home_action_request), Modifier.weight(1f)) {
            actions.openTab(HomeTab.HelpDesk)
        }
        QuickAction(NotizIcons.Poll, Palette.teal, Palette.tealSoft, stringResource(Res.string.home_action_polls), Modifier.weight(1f), onClick = actions.openPolls)
        QuickAction(NotizIcons.Users, Palette.blue, Palette.blueSoft, stringResource(Res.string.home_action_neighbours), Modifier.weight(1f), onClick = actions.openMembers)
        QuickAction(NotizIcons.Download, Palette.purple, Palette.purpleSoft, stringResource(Res.string.home_action_receipts), Modifier.weight(1f)) {
            actions.openTab(HomeTab.Dues)
        }
    }

    LatestNotice(state, actions)
    PollCard(state, onVote, actions.openPolls)
}

@Composable
private fun DueHero(state: HomeUiState, onPay: () -> Unit) {
    val due = state.myDue
    if (due == null) {
        NotizCard(color = Palette.tealSoft, radius = 24.dp, padding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(NotizIcons.Check, Color.White, Palette.teal, size = 40.dp, iconSize = 20.dp, radius = 20.dp)
                Column {
                    Text(stringResource(Res.string.home_all_paid), color = Palette.tealDark, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text(stringResource(Res.string.home_all_paid_body), color = Palette.inkSoft, fontSize = 13.sp)
                }
            }
        }
        return
    }
    val days = state.myDueDaysLeft
    val verifying = due.status == DueStatus.SUBMITTED
    Column(
        Modifier.fillMaxWidth().background(Palette.teal, RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(due.title, color = Palette.tealOnDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    color = if (days != null && days < 0 && !verifying) Color.White else Palette.onMarigold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(if (days != null && days < 0 && !verifying) Palette.red else Palette.marigold, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(formatRupees(due.amountPaise), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            Text(stringResource(Res.string.home_due_on, localDate(due.dueDate)), color = Palette.tealOnDark, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (!verifying) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable(onClick = onPay),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(Res.string.home_pay_now), color = Palette.teal, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                Box(
                    Modifier.height(48.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, Color(0xFF5FA18F), RoundedCornerShape(14.dp))
                        .clickable(onClick = onPay).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(Res.string.home_already_paid), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, tint: Color, background: Color, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconTile(icon, tint, background, size = 56.dp, iconSize = 24.dp, radius = 18.dp)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, color = Palette.ink)
    }
}

@Composable
private fun LatestNotice(state: HomeUiState, actions: HomeActions) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(stringResource(Res.string.home_latest_notice), action = stringResource(Res.string.home_see_all)) {
            actions.openTab(HomeTab.Notices)
        }
        val notice = state.latestNotice
        if (notice == null) {
            NotizCard { Text(stringResource(Res.string.home_no_notices), color = Palette.muted, fontSize = 14.sp) }
            return@Column
        }
        NotizCard(onClick = { actions.openNotice(notice.id) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(NotizIcons.Notice, Palette.teal, Palette.tealSoft)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.latestNoticeUnread) Box(Modifier.size(8.dp).background(Palette.orange, CircleShape))
                        if (notice.pinned) Tag(stringResource(Res.string.notice_pinned))
                        Text(localTimestamp(notice.createdAt), color = Palette.muted, fontSize = 12.sp)
                    }
                    Text(notice.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (notice.body.isNotBlank()) {
                        Text(notice.body, color = Palette.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun PollCard(state: HomeUiState, onVote: (String) -> Unit, onOpenPolls: () -> Unit) {
    val poll = state.poll ?: return
    NotizCard(onClick = onOpenPolls) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag(stringResource(Res.string.home_open_poll), color = Palette.teal, background = Palette.tealSoft)
            Text(
                if (state.myVote != null) stringResource(Res.string.home_voted, state.pollVotes.toInt()) else stringResource(Res.string.home_one_vote),
                color = Palette.muted,
                fontSize = 12.sp,
            )
        }
        Text(poll.question, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            poll.options.take(4).forEach { option ->
                val selected = state.myVote == option.id
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Palette.tealSoft else Color.White)
                        .border(if (selected) 1.5.dp else 1.dp, if (selected) Palette.teal else Palette.line, RoundedCornerShape(12.dp))
                        .clickable(enabled = !state.votingBusy) { onVote(option.id) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        option.label,
                        color = if (selected) Palette.teal else Palette.ink,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ───────────────────────────── Committee ─────────────────────────────

@Composable
private fun CommitteeHome(state: HomeUiState, actions: HomeActions) {
    CollectionCard(state, onDetails = { actions.openTab(HomeTab.Dues) })

    state.myDue?.takeIf { it.status == DueStatus.PENDING }?.let { due ->
        NotizCard(onClick = { actions.openTab(HomeTab.Dues) }, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(NotizIcons.Rupee, contentDescription = null, tint = Palette.amberText, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(Res.string.home_your_flat_due, formatRupees(due.amountPaise), localDate(due.dueDate)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(Res.string.home_pay_now), color = Palette.teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(stringResource(Res.string.home_attention))
        GroupCard {
            val rows = buildList<@Composable (Boolean) -> Unit> {
                if (state.toVerify > 0) add { divider ->
                    AttentionRow(NotizIcons.Rupee, Palette.amberText, Palette.amberSoft, stringResource(Res.string.home_att_verify),
                        stringResource(Res.string.home_att_verify_sub), state.toVerify, divider) { actions.openTab(HomeTab.Dues) }
                }
                if (state.joinRequests > 0) add { divider ->
                    AttentionRow(NotizIcons.UserPlus, Palette.blue, Palette.blueSoft, stringResource(Res.string.home_att_join),
                        state.joinNames.take(2).joinToString(", "), state.joinRequests, divider, actions.openMembers)
                }
                if (state.openRequests > 0) add { divider ->
                    AttentionRow(NotizIcons.Wrench, Palette.red, Palette.redSoft, stringResource(Res.string.home_att_requests),
                        state.oldestOpenRequest?.let { stringResource(Res.string.home_att_requests_sub, it.category) }, state.openRequests, divider) {
                        actions.openTab(HomeTab.HelpDesk)
                    }
                }
                if (state.reportedMessages > 0) add { divider ->
                    AttentionRow(NotizIcons.Flag, Palette.purple, Palette.purpleSoft, stringResource(Res.string.home_att_reports),
                        stringResource(Res.string.home_att_reports_sub), state.reportedMessages, divider) { actions.openTab(HomeTab.Chat) }
                }
            }
            if (rows.isEmpty()) {
                ListRow(
                    title = stringResource(Res.string.home_all_caught_up),
                    subtitle = stringResource(Res.string.home_all_caught_up_sub),
                    leading = { IconTile(NotizIcons.Check, Palette.teal, Palette.tealSoft, size = 40.dp, iconSize = 20.dp, radius = 12.dp) },
                    divider = false,
                )
            } else {
                rows.forEachIndexed { i, row -> row(i < rows.lastIndex) }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(stringResource(Res.string.home_post_notice), onClick = { actions.openTab(HomeTab.Notices) }, icon = NotizIcons.Plus, modifier = Modifier.weight(1f))
            SecondaryButton(stringResource(Res.string.home_raise_bill), onClick = { actions.openTab(HomeTab.Dues) }, accent = true, icon = NotizIcons.Plus, height = 56.dp, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(stringResource(Res.string.home_new_poll), onClick = actions.openPolls, icon = NotizIcons.Poll, height = 56.dp, modifier = Modifier.weight(1f))
            SecondaryButton(stringResource(Res.string.home_invite), onClick = actions.openMembers, icon = NotizIcons.Share, height = 56.dp, modifier = Modifier.weight(1f))
        }
    }

    LatestNotice(state, actions)
}

@Composable
private fun CollectionCard(state: HomeUiState, onDetails: () -> Unit) {
    val bill = state.bill
    NotizCard(radius = 24.dp, padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        if (bill == null) {
            Text(stringResource(Res.string.home_no_bills), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(stringResource(Res.string.home_no_bills_body), color = Palette.muted, fontSize = 14.sp)
            return@NotizCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.home_collection, bill.title), color = Palette.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(Res.string.home_details), color = Palette.teal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDetails).padding(6.dp))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatRupees(bill.collectedPaise), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
            Text(stringResource(Res.string.home_of_total, formatRupees(bill.totalPaise)), color = Palette.muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        val total = bill.totalPaise.coerceAtLeast(1)
        val paidShare = bill.collectedPaise.toFloat() / total
        val verifyShare = (bill.verifyFlats.toFloat() / (bill.paidFlats + bill.verifyFlats + bill.unpaidFlats).coerceAtLeast(1))
            .coerceAtMost(1f - paidShare.coerceAtMost(1f))
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFE8EEEB))) {
            if (paidShare > 0f) Box(Modifier.weight(paidShare.coerceAtMost(1f)).fillMaxHeight().background(Palette.teal))
            if (verifyShare > 0f) Box(Modifier.weight(verifyShare).fillMaxHeight().background(Palette.marigold))
            val rest = 1f - paidShare - verifyShare
            if (rest > 0.001f) Box(Modifier.weight(rest).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth()) {
            Stat(bill.paidFlats, stringResource(Res.string.home_flats_paid), Palette.teal, Modifier.weight(1f))
            Stat(bill.verifyFlats, stringResource(Res.string.home_to_verify), Palette.amberText, Modifier.weight(1f))
            Stat(bill.unpaidFlats, stringResource(Res.string.home_unpaid), Palette.ink, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(value: Int, label: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value.toString(), color = color, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Palette.muted, fontSize = 12.sp)
    }
}

@Composable
private fun AttentionRow(
    icon: ImageVector,
    tint: Color,
    background: Color,
    title: String,
    subtitle: String?,
    count: Int,
    divider: Boolean,
    onClick: () -> Unit,
) {
    ListRow(
        title = title,
        subtitle = subtitle?.takeIf { it.isNotBlank() },
        leading = { IconTile(icon, tint, background, size = 40.dp, iconSize = 20.dp, radius = 12.dp) },
        divider = divider,
        showChevron = true,
        onClick = onClick,
    ) {
        CountBadge(count)
    }
}
