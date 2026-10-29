package com.notiz.app.data

import com.notiz.app.AppConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Online payments through each society's own Razorpay account. */
class PaymentsRepository(private val supabase: SupabaseClient) {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonHeaders = Headers.build { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) }

    /** Whether residents can pay online right now (admins can switch it on and off any time). */
    suspend fun onlineEnabled(societyId: String): Boolean =
        supabase.from("societies")
            .select(Columns.raw("online_payments_enabled")) { filter { eq("id", societyId) } }
            .decodeSingleOrNull<OnlineFlag>()
            ?.onlinePaymentsEnabled ?: false

    /** Creates (or reuses) a Razorpay Payment Link for the due. Returns its URL, or status "paid". */
    suspend fun payOnline(dueId: String): PayLinkResult = callFunction(dueId, "create")

    /** Records a paid link whose webhook hasn't arrived yet. Returns the due's status. */
    suspend fun checkOnlinePayment(dueId: String): PayLinkResult = callFunction(dueId, "check")

    private suspend fun callFunction(dueId: String, action: String): PayLinkResult {
        val response = supabase.functions.invoke(
            function = "create-payment-link",
            body = buildJsonObject {
                put("due_id", dueId)
                put("action", action)
            },
            headers = jsonHeaders,
        )
        return json.decodeFromString(PayLinkResult.serializer(), response.bodyAsText())
    }

    // ───────────── Admin settings ─────────────

    suspend fun gatewayStatus(societyId: String): GatewayStatus =
        supabase.postgrest.rpc("payment_gateway_status", buildJsonObject { put("p_society_id", societyId) })
            .decodeList<GatewayStatus>()
            .firstOrNull() ?: GatewayStatus()

    /** Pass a blank [keySecret] to keep the saved one. */
    suspend fun saveKeys(societyId: String, keyId: String, keySecret: String) {
        supabase.postgrest.rpc(
            "set_razorpay_keys",
            buildJsonObject {
                put("p_society_id", societyId)
                put("p_key_id", keyId.trim())
                put("p_key_secret", keySecret.trim())
            },
        )
    }

    suspend fun setOnlineEnabled(societyId: String, enabled: Boolean) {
        supabase.postgrest.rpc(
            "set_online_payments",
            buildJsonObject {
                put("p_society_id", societyId)
                put("p_enabled", enabled)
            },
        )
    }

    suspend fun removeKeys(societyId: String) {
        supabase.postgrest.rpc("remove_razorpay_keys", buildJsonObject { put("p_society_id", societyId) })
    }

    /** The URL to paste into Razorpay → Webhooks for this society. */
    fun webhookUrl(societyId: String): String =
        "${AppConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/razorpay-webhook?society=$societyId"
}
