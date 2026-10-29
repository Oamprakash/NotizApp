package com.notiz.app.data

import com.notiz.app.AppConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

fun createAppSupabaseClient(): SupabaseClient =
    createSupabaseClient(
        supabaseUrl = AppConfig.SUPABASE_URL,
        supabaseKey = AppConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
    }

/** Short, human-readable message for showing errors in the UI. */
fun Throwable.userMessage(): String = when (this) {
    is AuthRestException -> errorDescription
    is RestException -> error
    is HttpRequestException -> "No internet connection. Please try again."
    else -> message ?: "Something went wrong"
}
