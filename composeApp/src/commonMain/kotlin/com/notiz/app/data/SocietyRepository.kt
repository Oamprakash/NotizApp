package com.notiz.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SocietyRepository(private val supabase: SupabaseClient) {

    private fun uid() = supabase.auth.currentUserOrNull()?.id ?: error("Not signed in")

    /** The caller's memberships, each with its society embedded. */
    suspend fun myMemberships(): List<Membership> =
        supabase.from("memberships")
            .select(Columns.raw("*, societies(*)")) {
                filter { eq("user_id", uid()) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList()

    suspend fun createSociety(
        name: String,
        city: String,
        flatNo: String,
        upiVpa: String,
        upiPayeeName: String,
    ): Society =
        supabase.postgrest.rpc(
            "create_society",
            buildJsonObject {
                put("p_name", name)
                put("p_city", city)
                put("p_flat_no", flatNo)
                put("p_upi_vpa", upiVpa)
                put("p_upi_payee_name", upiPayeeName)
            },
        ).decodeAs()

    suspend fun joinSociety(inviteCode: String, flatNo: String): Membership =
        supabase.postgrest.rpc(
            "join_society",
            buildJsonObject {
                put("p_invite_code", inviteCode)
                put("p_flat_no", flatNo)
            },
        ).decodeAs()

    /** All memberships of a society with member names (admins see pending ones too). */
    suspend fun members(societyId: String): List<Membership> =
        supabase.from("memberships")
            .select(Columns.raw("*, profiles(id, full_name)")) {
                filter { eq("society_id", societyId) }
                order("flat_no", Order.ASCENDING)
            }
            .decodeList()

    suspend fun setMembershipStatus(membershipId: String, status: String) {
        supabase.from("memberships").update({ set("status", status) }) {
            filter { eq("id", membershipId) }
        }
    }

    suspend fun setMembershipRole(membershipId: String, role: String) {
        supabase.from("memberships").update({ set("role", role) }) {
            filter { eq("id", membershipId) }
        }
    }
}
