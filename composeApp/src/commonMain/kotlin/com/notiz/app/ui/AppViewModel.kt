package com.notiz.app.ui

import androidx.lifecycle.viewModelScope
import com.notiz.app.AppContainer
import com.notiz.app.data.Membership
import com.notiz.app.data.Society
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the signed-in user is in onboarding. Drives the root of the UI. */
sealed interface AppState {
    data object Loading : AppState
    data object SignedOut : AppState
    data object NeedsProfile : AppState
    data object NeedsSociety : AppState
    data class Pending(val membership: Membership) : AppState
    data class Ready(val membership: Membership, val society: Society) : AppState
    data class Error(val message: String) : AppState
}

class AppViewModel(private val container: AppContainer) : BaseViewModel() {

    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var loadedUserId: String? = null

    /** Bumped on every sign-out so screen ViewModels start fresh for the next user. */
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    init {
        viewModelScope.launch {
            container.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        // Token refreshes also emit Authenticated; reload only for a new user.
                        val uid = container.auth.currentUserId()
                        if (uid != loadedUserId) {
                            loadedUserId = uid
                            refresh()
                        }
                    }
                    is SessionStatus.NotAuthenticated -> {
                        if (loadedUserId != null) _generation.value += 1
                        loadedUserId = null
                        _state.value = AppState.SignedOut
                    }
                    // Supabase also emits Initializing when the app goes to the background (it pauses
                    // token refresh) and Authenticated again on return. Keep the current screen;
                    // the very first launch already starts in Loading.
                    is SessionStatus.Initializing -> Unit
                    is SessionStatus.RefreshFailure -> {
                        if (_state.value == AppState.Loading) {
                            _state.value = AppState.Error("Can't reach the server. Check your internet connection.")
                        }
                    }
                }
            }
        }
    }

    /** Re-reads profile and memberships and moves to the right onboarding step. */
    fun refresh() {
        _state.value = AppState.Loading
        launchSafe(onError = { _state.value = AppState.Error(it) }) {
            val profile = container.auth.myProfile()
            if (profile == null) {
                _state.value = AppState.NeedsProfile
                return@launchSafe
            }
            container.push.onProfileReady()
            val memberships = container.societies.myMemberships()
            val approved = memberships.firstOrNull { it.isApproved && it.societies != null }
            val pending = memberships.firstOrNull { it.isPending }
            _state.value = when {
                approved != null -> AppState.Ready(approved, approved.societies!!)
                pending != null -> AppState.Pending(pending)
                else -> AppState.NeedsSociety
            }
        }
    }

    fun signOut() {
        launchSafe(onError = { _state.value = AppState.Error(it) }) {
            container.push.onSigningOut()
            container.auth.signOut()
        }
    }
}
