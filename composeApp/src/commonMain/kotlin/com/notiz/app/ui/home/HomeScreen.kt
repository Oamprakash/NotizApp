package com.notiz.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notiz.app.LocalAppContainer
import com.notiz.app.push.OnPushUpdate
import com.notiz.app.push.PushEvents
import com.notiz.app.push.PushTypes
import com.notiz.app.resources.*
import com.notiz.app.ui.SessionContext
import com.notiz.app.ui.chat.ChatTab
import com.notiz.app.ui.complaints.ComplaintsTab
import com.notiz.app.ui.dues.DuesTab
import com.notiz.app.ui.notices.NoticesHub
import com.notiz.app.ui.theme.NotizIcons
import com.notiz.app.ui.theme.Palette
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class HomeTab(val label: StringResource, val icon: () -> ImageVector) {
    Home(Res.string.tab_home, { NotizIcons.Home }),
    Notices(Res.string.tab_notices, { NotizIcons.Notice }),
    Dues(Res.string.tab_dues, { NotizIcons.Rupee }),
    HelpDesk(Res.string.tab_help, { NotizIcons.Wrench }),
    Chat(Res.string.tab_chat, { NotizIcons.Chat }),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    session: SessionContext,
    onOpenNotice: (String) -> Unit,
    onOpenMembers: () -> Unit,
    onOpenPaymentSettings: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var tabName by rememberSaveable { mutableStateOf(HomeTab.Home.name) }
    var showPolls by rememberSaveable { mutableStateOf(false) }
    val tab = HomeTab.valueOf(tabName)
    fun open(target: HomeTab) {
        tabName = target.name
        if (target == HomeTab.Notices) showPolls = false
    }

    val container = LocalAppContainer.current
    val homeVm = viewModel { HomeViewModel(container, session) }
    val home by homeVm.state.collectAsState()
    // Badges and the Home tab stay fresh as things happen
    OnPushUpdate(PushTypes.NOTICE, PushTypes.DUES, PushTypes.COMPLAINT, PushTypes.POLL, PushTypes.MEMBER, PushTypes.CHAT) { homeVm.load() }
    LaunchedEffect(tab) { if (tab == HomeTab.Home || tab == HomeTab.Notices) homeVm.load() }

    // Tapped a notification → jump to its tab
    val openedPush by PushEvents.opened.collectAsState()
    LaunchedEffect(openedPush) {
        val target = when (openedPush) {
            PushTypes.NOTICE -> HomeTab.Notices
            PushTypes.DUES -> HomeTab.Dues
            PushTypes.COMPLAINT -> HomeTab.HelpDesk
            PushTypes.CHAT -> HomeTab.Chat
            PushTypes.POLL -> {
                showPolls = true
                HomeTab.Notices
            }
            else -> null
        } ?: return@LaunchedEffect
        tabName = target.name
        PushEvents.consumeOpened()
    }

    val actions = HomeActions(
        openTab = ::open,
        openPolls = {
            tabName = HomeTab.Notices.name
            showPolls = true
        },
        openNotice = onOpenNotice,
        openMembers = onOpenMembers,
        openSettings = onOpenSettings,
    )

    Scaffold(
        containerColor = Palette.ground,
        bottomBar = {
            BottomBar(
                selected = tab,
                badgeFor = { item ->
                    when (item) {
                        HomeTab.Notices -> home.unreadNotices
                        HomeTab.Dues -> if (session.isAdmin) home.toVerify else home.duesBadge
                        HomeTab.HelpDesk -> if (session.isAdmin) home.openRequests else 0
                        else -> 0
                    }
                },
                onSelect = ::open,
            )
        },
    ) { padding ->
        // consumeWindowInsets stops the chat's imePadding from double-counting the bottom bar
        val modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
        when (tab) {
            // Home draws its teal band behind the status bar, so it only takes the bottom padding
            HomeTab.Home -> HomeTabContent(
                session = session,
                state = home,
                onRefresh = { homeVm.load(pullToRefresh = true) },
                onVote = homeVm::vote,
                onDismissError = homeVm::dismissError,
                actions = actions,
                modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            )
            HomeTab.Notices -> NoticesHub(
                session = session,
                showPolls = showPolls,
                onShowPolls = { showPolls = it },
                unreadNotices = home.unreadNotices,
                onOpenNotice = onOpenNotice,
                modifier = modifier,
            )
            HomeTab.Dues -> DuesTab(session, onOpenPaymentSettings, modifier)
            HomeTab.HelpDesk -> ComplaintsTab(session, modifier)
            HomeTab.Chat -> ChatTab(session, modifier)
        }
    }
}

/** Notiz's own tab bar: soft teal pill behind the selected icon, small orange count badges. */
@Composable
private fun BottomBar(selected: HomeTab, badgeFor: (HomeTab) -> Int, onSelect: (HomeTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.line))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(66.dp).padding(horizontal = 4.dp)) {
            HomeTab.entries.forEach { item ->
                val active = item == selected
                val badge = badgeFor(item)
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(item) }
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box {
                        Box(
                            Modifier.size(width = 56.dp, height = 30.dp).background(if (active) Palette.tealSoft else Color.Transparent, RoundedCornerShape(15.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(item.icon(), contentDescription = null, tint = if (active) Palette.teal else Palette.muted, modifier = Modifier.size(22.dp))
                        }
                        if (badge > 0) {
                            Box(
                                Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = (-3).dp)
                                    .heightIn(min = 18.dp).widthIn(min = 18.dp)
                                    .background(Color.White, RoundedCornerShape(9.dp)).padding(2.dp)
                                    .background(Palette.orange, RoundedCornerShape(8.dp)).padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (badge > 99) "99+" else "$badge", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        stringResource(item.label),
                        color = if (active) Palette.teal else Palette.muted,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
