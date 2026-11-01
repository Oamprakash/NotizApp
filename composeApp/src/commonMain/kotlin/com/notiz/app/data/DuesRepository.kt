package com.notiz.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DuesRepository(private val supabase: SupabaseClient) {

    /** RLS returns only the caller's flat for residents, every flat for admins. */
    suspend fun dues(societyId: String): List<Due> =
        supabase.from("dues")
            .select(Columns.raw("*, payer:profiles!dues_submitted_by_fkey(full_name)")) {
                filter {
                    eq("society_id", societyId)
                    neq("status", DueStatus.CANCELLED)
                }
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

    /**
     * Admin: [approve] = money received (a reported UPI payment, or cash/cheque straight from
     * unpaid) and assigns the receipt number; otherwise sends a reported payment back to unpaid.
     */
    suspend fun verifyPayment(dueId: String, approve: Boolean) {
        supabase.postgrest.rpc(
            "verify_payment",
            buildJsonObject {
                put("p_due_id", dueId)
                put("p_approve", approve)
            },
        )
    }

    /** Admin: cancels one unpaid due (e.g. a flat billed by mistake). */
    suspend fun cancelDue(dueId: String) {
        supabase.postgrest.rpc("cancel_due", buildJsonObject { put("p_due_id", dueId) })
    }

    /** Admin: cancels every unpaid due of a bill. Paid ones stay. Returns how many were cancelled. */
    suspend fun cancelBill(societyId: String, title: String, dueDate: String): Int =
        supabase.postgrest.rpc(
            "cancel_bill",
            buildJsonObject {
                put("p_society_id", societyId)
                put("p_title", title)
                put("p_due_date", dueDate)
            },
        ).decodeAs()

    /** Admin: changes the unpaid dues of a bill. Returns how many were changed. */
    suspend fun editBill(
        societyId: String,
        title: String,
        dueDate: String,
        newTitle: String,
        newAmountPaise: Long,
        newDueDate: String,
    ): Int =
        supabase.postgrest.rpc(
            "edit_bill",
            buildJsonObject {
                put("p_society_id", societyId)
                put("p_title", title)
                put("p_due_date", dueDate)
                put("p_new_title", newTitle.trim())
                put("p_new_amount_paise", newAmountPaise)
                put("p_new_due_date", newDueDate)
            },
        ).decodeAs()
}
