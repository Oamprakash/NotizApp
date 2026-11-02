package com.notiz.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
            Column {
                HorizontalDivider(color = Palette.line)
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                    HomeTab.entries.forEach { item ->
                        val badge = when (item) {
                            HomeTab.Notices -> home.unreadNotices
                            HomeTab.Dues -> if (session.isAdmin) home.toVerify else home.duesBadge
                            HomeTab.HelpDesk -> if (session.isAdmin) home.openRequests else 0
                            else -> 0
                        }
                        NavigationBarItem(
                            selected = item == tab,
                            onClick = { open(item) },
                            icon = {
                                BadgedBox(badge = { if (badge > 0) Badge(containerColor = Palette.orange) { Text(if (badge > 99) "99+" else "$badge") } }) {
                                    Icon(item.icon(), contentDescription = null, modifier = Modifier.size(22.dp))
                                }
                            },
                            label = {
                                Text(
                                    stringResource(item.label),
                                    fontSize = 11.sp,
                                    fontWeight = if (item == tab) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Palette.teal,
                                selectedTextColor = Palette.teal,
                                indicatorColor = Palette.tealSoft,
                                unselectedIconColor = Palette.muted,
                                unselectedTextColor = Palette.muted,
                            ),
                        )
                    }
                }
            }
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
