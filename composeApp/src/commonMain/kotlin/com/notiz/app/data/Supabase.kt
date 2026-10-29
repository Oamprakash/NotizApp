package com.notiz.app.data

import com.notiz.app.AppConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
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
