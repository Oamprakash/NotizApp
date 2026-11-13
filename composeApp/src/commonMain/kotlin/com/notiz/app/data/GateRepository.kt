package com.notiz.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Gate log. RLS decides what comes back: the gate desk (security staff, president) sees the whole
 * society, everyone else only their own flat. Every change goes through a database function.
 */
class GateRepository(private val supabase: SupabaseClient) {

    /** Visitors since [sinceIso] (an ISO timestamp), newest first. */
    suspend fun entries(societyId: String, sinceIso: String, flatNo: String? = null): List<GateEntry> =
        supabase.from("gate_entries")
            .select {
                filter {
                    eq("society_id", societyId)
                    gte("created_at", sinceIso)
                    if (flatNo != null) eq("flat_no", flatNo)
                }
                order("created_at", Order.DESCENDING)
                limit(300)
            }
            .decodeList()

    /** Passes that can still be used, from [fromDate] (yyyy-MM-dd) on. */
    suspend fun openPasses(societyId: String, fromDate: String, flatNo: String? = null): List<GatePass> =
        supabase.from("gate_passes")
            .select {
                filter {
                    eq("society_id", societyId)
                    gte("valid_on", fromDate)
                    exact("used_at", null)
                    exact("cancelled_at", null)
                    if (flatNo != null) eq("flat_no", flatNo)
                }
                order("valid_on", Order.ASCENDING)
            }
            .decodeList()

    suspend fun logVisitor(
        societyId: String,
        flatNo: String,
        category: String,
        name: String,
        phone: String,
        company: String,
        vehicleNo: String,
        letIn: Boolean,
        photoPath: String = "",
    ): GateEntry =
        supabase.postgrest.rpc(
            "log_visitor",
            buildJsonObject {
                put("p_society_id", societyId)
                put("p_flat_no", flatNo.trim())
                put("p_category", category)
                put("p_name", name.trim())
                put("p_phone", phone.trim())
                put("p_company", company.trim())
                put("p_vehicle_no", vehicleNo.trim())
                put("p_let_in", letIn)
                put("p_photo_path", photoPath)
            },
        ).decodeAs()

    suspend fun respond(entryId: String, allow: Boolean) {
        supabase.postgrest.rpc("respond_visitor", buildJsonObject { put("p_entry_id", entryId); put("p_allow", allow) })
    }

    suspend fun markIn(entryId: String) {
        supabase.postgrest.rpc("mark_visitor_in", buildJsonObject { put("p_entry_id", entryId) })
    }

    suspend fun markOut(entryId: String) {
        supabase.postgrest.rpc("mark_visitor_out", buildJsonObject { put("p_entry_id", entryId) })
    }

    suspend fun cancel(entryId: String) {
        supabase.postgrest.rpc("cancel_visitor", buildJsonObject { put("p_entry_id", entryId) })
    }

    suspend fun createPass(societyId: String, guestName: String, validOn: String, category: String): GatePass =
        supabase.postgrest.rpc(
            "create_gate_pass",
            buildJsonObject {
                put("p_society_id", societyId)
                put("p_guest_name", guestName.trim())
                put("p_valid_on", validOn)
                put("p_category", category)
            },
        ).decodeAs()

    suspend fun cancelPass(passId: String) {
        supabase.postgrest.rpc("cancel_gate_pass", buildJsonObject { put("p_pass_id", passId) })
    }

    /** The guard enters a guest's code; returns the new entry (already inside). */
    suspend fun usePass(societyId: String, code: String): GateEntry =
        supabase.postgrest.rpc(
            "use_gate_pass",
            buildJsonObject {
                put("p_society_id", societyId)
                put("p_code", code.trim())
            },
        ).decodeAs()

    companion object {
        /** Quick picks for the company field. */
        val deliveryCompanies = listOf("Swiggy", "Zomato", "Amazon", "Flipkart", "Zepto", "Blinkit", "BigBasket", "Courier")
        val cabCompanies = listOf("Uber", "Ola", "Rapido", "Auto")
        val serviceKinds = listOf("Maid", "Cook", "Driver", "Plumber", "Electrician", "Laundry")
    }
}
