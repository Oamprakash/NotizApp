package com.notiz.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.notiz.app.data.Membership
import com.notiz.app.data.Society
import com.notiz.app.push.PushEvents
import com.notiz.app.push.PushTypes
import com.notiz.app.ui.home.HomeScreen
import com.notiz.app.ui.members.MembersScreen
import com.notiz.app.ui.payments.PaymentSettingsScreen
import com.notiz.app.ui.notices.NoticeDetailScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class NoticeDetailRoute(val noticeId: String)

@Serializable
data object MembersRoute

@Serializable
data object PaymentSettingsRoute

/** Navigation for a signed-in, approved member. */
@Composable
fun MainNavigation(membership: Membership, society: Society, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val session = SessionContext(membership = membership, society = society)

    // Tapped a notification: join requests open Members; everything else goes back to Home,
    // which switches to the right tab.
    val openedPush by PushEvents.opened.collectAsState()
    LaunchedEffect(openedPush) {
        when (openedPush) {
            null -> Unit
            PushTypes.APPROVED -> PushEvents.consumeOpened()
            PushTypes.MEMBER -> {
                navController.navigate(MembersRoute) { launchSingleTop = true }
                PushEvents.consumeOpened()
            }
            else -> navController.popBackStack(HomeRoute, inclusive = false)
        }
    }

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                session = session,
                onOpenNotice = { navController.navigate(NoticeDetailRoute(it)) },
                onOpenMembers = { navController.navigate(MembersRoute) },
                onOpenPaymentSettings = { navController.navigate(PaymentSettingsRoute) },
                onSignOut = onSignOut,
            )
        }
        composable<NoticeDetailRoute> { entry ->
            val route = entry.toRoute<NoticeDetailRoute>()
            NoticeDetailScreen(
                session = session,
                noticeId = route.noticeId,
                onBack = { navController.popBackStack() },
            )
        }
        composable<PaymentSettingsRoute> {
            PaymentSettingsScreen(session = session, onBack = { navController.popBackStack() })
        }
        composable<MembersRoute> {
            MembersScreen(session = session, onBack = { navController.popBackStack() })
        }
    }
}

/** Who is signed in and where — passed to every screen of the main app. */
data class SessionContext(val membership: Membership, val society: Society) {
    val societyId get() = society.id
    val isAdmin get() = membership.isAdmin
    val flatNo get() = membership.flatNo
    val userId get() = membership.userId
}
