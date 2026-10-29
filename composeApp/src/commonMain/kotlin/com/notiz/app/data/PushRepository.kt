package com.notiz.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PushRepository(private val supabase: SupabaseClient) {

    /** Links this device's FCM token to the signed-in user (moves it if another account used it). */
    suspend fun register(token: String, platform: String) {
        supabase.postgrest.rpc(
            "register_device_token",
            buildJsonObject {
                put("p_token", token)
                put("p_platform", platform)
            },
        )
    }

    suspend fun unregister(token: String) {
        supabase.postgrest.rpc("unregister_device_token", buildJsonObject { put("p_token", token) })
    }
}
