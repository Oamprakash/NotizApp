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

    /** Join with the invite code; security staff join without a flat ([asStaff]). */
    suspend fun joinSociety(inviteCode: String, flatNo: String, asStaff: Boolean = false): Membership =
        supabase.postgrest.rpc(
            "join_society",
            buildJsonObject {
                put("p_invite_code", inviteCode)
                put("p_flat_no", flatNo)
                put("p_as_staff", asStaff)
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

    // ───────────── Roles and permissions ─────────────

    /** What the signed-in user may do in this society. */
    suspend fun myPermissions(societyId: String): Set<String> =
        supabase.postgrest.rpc("my_permissions", buildJsonObject { put("p_society_id", societyId) })
            .decodeAs<List<String>>().toSet()

    /** President: give a member a role. Moving someone off security needs their [flatNo]. */
    suspend fun setMemberRole(membershipId: String, role: String, flatNo: String? = null) {
        supabase.postgrest.rpc(
            "set_member_role",
            buildJsonObject {
                put("p_membership_id", membershipId)
                put("p_role", role)
                if (flatNo != null) put("p_flat_no", flatNo.trim())
            },
        )
    }

    /** Every role's permissions in this society (the president's editor). */
    suspend fun rolePermissions(societyId: String): List<RolePermission> =
        supabase.from("role_permissions").select { filter { eq("society_id", societyId) } }.decodeList()

    suspend fun setRolePermission(societyId: String, role: String, permission: String, enabled: Boolean) {
        supabase.postgrest.rpc(
            "set_role_permission",
            buildJsonObject {
                put("p_society_id", societyId)
                put("p_role", role)
                put("p_permission", permission)
                put("p_enabled", enabled)
            },
        )
    }

    suspend fun resetRolePermissions(societyId: String, role: String) {
        supabase.postgrest.rpc(
            "reset_role_permissions",
            buildJsonObject {
                put("p_society_id", societyId)
                put("p_role", role)
            },
        )
    }

    /** Flats, names and roles; phone numbers only for security staff and the committee. */
    suspend fun directory(societyId: String): List<DirectoryEntry> =
        supabase.postgrest.rpc("directory", buildJsonObject { put("p_society_id", societyId) }).decodeList()

    // ───────────── Admin tools ─────────────

    suspend fun updateSettings(societyId: String, name: String, city: String, upiVpa: String, upiPayeeName: String): Society =
        supabase.postgrest.rpc(
            "update_society_settings",
            buildJsonObject {
                put("p_society_id", societyId)
                put("p_name", name.trim())
                put("p_city", city.trim())
                put("p_upi_vpa", upiVpa.trim())
                put("p_upi_payee_name", upiPayeeName.trim())
            },
        ).decodeAs()

    /** New invite code; the old one stops working. */
    suspend fun regenerateInviteCode(societyId: String): String =
        supabase.postgrest.rpc("regenerate_invite_code", buildJsonObject { put("p_society_id", societyId) })
            .decodeAs()

    suspend fun removeMember(membershipId: String) {
        supabase.postgrest.rpc("remove_member", buildJsonObject { put("p_membership_id", membershipId) })
    }

    suspend fun setMemberFlat(membershipId: String, flatNo: String) {
        supabase.postgrest.rpc(
            "set_member_flat",
            buildJsonObject {
                put("p_membership_id", membershipId)
                put("p_flat_no", flatNo.trim())
            },
        )
    }

    suspend fun leaveSociety(societyId: String) {
        supabase.postgrest.rpc("leave_society", buildJsonObject { put("p_society_id", societyId) })
    }
}
