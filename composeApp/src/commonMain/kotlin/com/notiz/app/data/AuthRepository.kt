package com.notiz.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.StateFlow

class AuthRepository(private val supabase: SupabaseClient) {

    val sessionStatus: StateFlow<SessionStatus> get() = supabase.auth.sessionStatus

    fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    fun requireUserId(): String = currentUserId() ?: error("Not signed in")

    /** Sends an SMS OTP. [phone] must be in E.164 format, e.g. +919876543210. */
    suspend fun sendOtp(phone: String) {
        supabase.auth.signInWith(OTP) {
            this.phone = phone
        }
    }

    suspend fun verifyOtp(phone: String, code: String) {
        supabase.auth.verifyPhoneOtp(type = OtpType.Phone.SMS, phone = phone, token = code)
    }

    suspend fun signOut() {
        supabase.auth.signOut()
    }

    suspend fun myProfile(): Profile? =
        supabase.from("profiles")
            .select { filter { eq("id", requireUserId()) } }
            .decodeSingleOrNull<Profile>()

    suspend fun saveProfile(fullName: String) {
        supabase.from("profiles").upsert(Profile(id = requireUserId(), fullName = fullName.trim()))
    }
}
