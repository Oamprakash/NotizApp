package com.notiz.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DuesRepository(private val supabase: SupabaseClient) {

    /** RLS returns only the caller's flat for residents, every flat for admins. */
    suspend fun dues(societyId: String): List<Due> =
        supabase.from("dues")
            .select {
                filter { eq("society_id", societyId) }
                order("due_date", Order.DESCENDING)
                order("flat_no", Order.ASCENDING)
            }
            .decodeList()

    /** Admin: bills every approved flat. Returns the number of flats billed. */
    suspend fun raiseBill(societyId: String, title: String, amountPaise: Long, dueDate: String): Int =
        supabase.postgrest.rpc(
            "create_dues_for_all",
            buildJsonObject {
                put("p_society_id", societyId)
                put("p_title", title.trim())
                put("p_amount_paise", amountPaise)
                put("p_due_date", dueDate)
            },
        ).decodeAs()

    suspend fun submitPayment(dueId: String, utr: String) {
        supabase.postgrest.rpc(
            "submit_payment",
            buildJsonObject {
                put("p_due_id", dueId)
                put("p_utr", utr.trim())
            },
        )
    }

    /** Admin: [approve] = money received; otherwise sends it back to pending. */
    suspend fun verifyPayment(dueId: String, approve: Boolean) {
        supabase.postgrest.rpc(
            "verify_payment",
            buildJsonObject {
                put("p_due_id", dueId)
                put("p_approve", approve)
            },
        )
    }
}
