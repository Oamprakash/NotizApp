@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.notiz.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.notiz.app.push.PushEvents
import com.notiz.app.push.PushTypes
import com.notiz.app.ui.SessionContext
import com.notiz.app.ui.chat.ChatTab
import com.notiz.app.ui.complaints.ComplaintsTab
import com.notiz.app.ui.dues.DuesTab
import com.notiz.app.ui.notices.NoticesTab
import com.notiz.app.ui.polls.PollsTab
import com.notiz.app.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.StringResource
import com.notiz.app.ui.components.LanguageToggle

private enum class HomeTab(val label: StringResource, val icon: ImageVector) {
    Notices(Res.string.tab_notices, Icons.Filled.Notifications),
    Dues(Res.string.tab_dues, Icons.Filled.ShoppingCart),
    Complaints(Res.string.tab_complaints, Icons.Filled.Build),
    Polls(Res.string.tab_polls, Icons.Filled.Check),
    Chat(Res.string.tab_chat, Icons.Filled.Email),
}

@Composable
fun HomeScreen(
    session: SessionContext,
    onOpenNotice: (String) -> Unit,
    onOpenMembers: () -> Unit,
    onSignOut: () -> Unit,
) {
    var tabName by rememberSaveable { mutableStateOf(HomeTab.Notices.name) }
    val tab = HomeTab.valueOf(tabName)

    // Tapped a notification → jump to its tab
    val openedPush by PushEvents.opened.collectAsState()
    LaunchedEffect(openedPush) {
        val target = when (openedPush) {
            PushTypes.NOTICE -> HomeTab.Notices
            PushTypes.DUES -> HomeTab.Dues
            PushTypes.COMPLAINT -> HomeTab.Complaints
            PushTypes.POLL -> HomeTab.Polls
            PushTypes.CHAT -> HomeTab.Chat
            else -> null
        } ?: return@LaunchedEffect
        tabName = target.name
        PushEvents.consumeOpened()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session.society.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (session.isAdmin) {
                                stringResource(Res.string.home_flat_admin, session.flatNo)
                            } else {
                                stringResource(Res.string.home_flat, session.flatNo)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    LanguageToggle()
                    IconButton(onClick = onOpenMembers) {
                        Icon(
                            if (session.isAdmin) Icons.AutoMirrored.Filled.List else Icons.Filled.Person,
                            contentDescription = stringResource(Res.string.members_title),
                        )
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(Res.string.action_sign_out))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { tabName = item.name },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        },
    ) { padding ->
        // consumeWindowInsets stops the chat's imePadding from double-counting the bottom bar
        val modifier = Modifier.padding(padding).consumeWindowInsets(padding)
        when (tab) {
            HomeTab.Notices -> NoticesTab(session, onOpenNotice, modifier)
            HomeTab.Dues -> DuesTab(session, modifier)
            HomeTab.Complaints -> ComplaintsTab(session, modifier)
            HomeTab.Polls -> PollsTab(session, modifier)
            HomeTab.Chat -> ChatTab(session, modifier)
        }
    }
}
