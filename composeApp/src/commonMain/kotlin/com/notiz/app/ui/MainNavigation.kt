package com.notiz.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.notiz.app.data.Membership
import com.notiz.app.data.Society
import com.notiz.app.ui.home.HomeScreen
import com.notiz.app.ui.members.MembersScreen
import com.notiz.app.ui.notices.NoticeDetailScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class NoticeDetailRoute(val noticeId: String)

@Serializable
data object MembersRoute

/** Navigation for a signed-in, approved member. */
@Composable
fun MainNavigation(membership: Membership, society: Society, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val session = SessionContext(membership = membership, society = society)

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                session = session,
                onOpenNotice = { navController.navigate(NoticeDetailRoute(it)) },
                onOpenMembers = { navController.navigate(MembersRoute) },
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
