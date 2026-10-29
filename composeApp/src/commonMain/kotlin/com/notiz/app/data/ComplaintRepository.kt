package com.notiz.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class ComplaintRepository(private val supabase: SupabaseClient) {

    private fun uid() = supabase.auth.currentUserOrNull()?.id ?: error("Not signed in")

    /** Residents see their own complaints; admins see all (enforced by RLS). */
    suspend fun complaints(societyId: String): List<Complaint> =
        supabase.from("complaints")
            .select {
                filter { eq("society_id", societyId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()

    suspend fun raise(societyId: String, category: String, description: String) {
        supabase.from("complaints").insert(
            NewComplaint(
                societyId = societyId,
                raisedBy = uid(),
                category = category,
                description = description.trim(),
            )
        )
    }

    suspend fun setStatus(complaintId: String, status: String) {
        supabase.from("complaints").update({ set("status", status) }) {
            filter { eq("id", complaintId) }
        }
    }

    companion object {
        val categories = listOf("Plumbing", "Electrical", "Lift", "Security", "Cleaning", "Parking", "Other")
    }
}
