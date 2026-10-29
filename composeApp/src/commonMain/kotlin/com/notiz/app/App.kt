package com.notiz.app

import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notiz.app.ui.AppState
import com.notiz.app.ui.AppViewModel
import com.notiz.app.ui.MainNavigation
import com.notiz.app.ui.auth.LoginScreen
import com.notiz.app.ui.components.ErrorBox
import com.notiz.app.ui.components.LoadingBox
import com.notiz.app.ui.onboarding.PendingApprovalScreen
import com.notiz.app.ui.onboarding.ProfileScreen
import com.notiz.app.ui.onboarding.SocietySetupScreen
import com.notiz.app.ui.theme.NotizTheme

@Composable
fun App() {
    val container = AppContainer.instance
    CompositionLocalProvider(LocalAppContainer provides container) {
        NotizTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val appViewModel = viewModel { AppViewModel(container) }
                val state by appViewModel.state.collectAsState()
                val generation by appViewModel.generation.collectAsState()

                when (val s = state) {
                    AppState.Loading -> LoadingBox()
                    AppState.SignedOut -> LoginScreen(generation)
                    AppState.NeedsProfile -> ProfileScreen(generation, onDone = appViewModel::refresh)
                    AppState.NeedsSociety -> SocietySetupScreen(
                        generation = generation,
                        onDone = appViewModel::refresh,
                        onSignOut = appViewModel::signOut,
                    )
                    is AppState.Pending -> PendingApprovalScreen(
                        membership = s.membership,
                        onRefresh = appViewModel::refresh,
                        onSignOut = appViewModel::signOut,
                    )
                    is AppState.Ready -> MainNavigation(
                        membership = s.membership,
                        society = s.society,
                        onSignOut = appViewModel::signOut,
                    )
                    is AppState.Error -> ErrorBox(message = s.message, onRetry = appViewModel::refresh)
                }
            }
        }
    }
}
