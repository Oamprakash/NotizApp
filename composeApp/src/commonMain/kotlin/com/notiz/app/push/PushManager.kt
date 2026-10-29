package com.notiz.app.push

import com.notiz.app.data.AuthRepository
import com.notiz.app.data.PushRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Keeps this device's FCM token registered against the signed-in user, so the server
 * knows where to deliver notifications. Failures are ignored: push is best-effort and must
 * never block the app.
 */
class PushManager(
    private val repository: PushRepository,
    private val auth: AuthRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var registeredToken: String? = null
    private var profileReady = false

    init {
        // Token refreshes from Firebase (new install, restore, rotation)
        scope.launch {
            PushEvents.tokens.collect { token ->
                if (profileReady && auth.currentUserId() != null) register(token)
            }
        }
    }

    /** Call once the user is signed in AND has a profile (tokens reference profiles). */
    fun onProfileReady() {
        profileReady = true
        scope.launch {
            val token = safely { PushPlatform.currentToken() } ?: return@launch
            register(token)
        }
    }

    /** Call before signing out so this device stops getting the old account's notifications. */
    suspend fun onSigningOut() {
        profileReady = false
        // Only the token we registered; never wait on the platform here (sign-out must stay instant).
        val token = registeredToken ?: return
        safely { repository.unregister(token) }
        registeredToken = null
    }

    private suspend fun register(token: String) {
        if (token == registeredToken) return
        safely {
            repository.register(token, PushPlatform.name)
            registeredToken = token
        }
    }

    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        println("Notiz push: ${e.message}")
        null
    }
}
