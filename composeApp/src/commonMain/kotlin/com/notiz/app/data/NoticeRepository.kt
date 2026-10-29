package com.notiz.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class NoticeRepository(private val supabase: SupabaseClient) {

    private fun uid() = supabase.auth.currentUserOrNull()?.id ?: error("Not signed in")

    /** Pinned notices first, then newest first. */
    suspend fun notices(societyId: String): List<Notice> =
        supabase.from("notices")
            .select {
                filter { eq("society_id", societyId) }
                order("pinned", Order.DESCENDING)
                order("created_at", Order.DESCENDING)
            }
            .decodeList()

    suspend fun notice(noticeId: String): Notice =
        supabase.from("notices")
            .select { filter { eq("id", noticeId) } }
            .decodeSingle()

    /** IDs of notices the current user has opened. */
    suspend fun myReadNoticeIds(): Set<String> =
        supabase.from("notice_reads")
            .select(Columns.raw("notice_id")) { filter { eq("user_id", uid()) } }
            .decodeList<NoticeRead>()
            .mapTo(mutableSetOf()) { it.noticeId }

    suspend fun markRead(noticeId: String) {
        supabase.postgrest.rpc("mark_notice_read", buildJsonObject { put("p_notice_id", noticeId) })
    }

    /** Admin only: read counts keyed by notice id. */
    suspend fun readCounts(societyId: String): Map<String, NoticeReadCount> =
        supabase.postgrest.rpc("notice_read_counts", buildJsonObject { put("p_society_id", societyId) })
            .decodeList<NoticeReadCount>()
            .associateBy { it.noticeId }

    suspend fun post(societyId: String, title: String, body: String, pinned: Boolean) {
        supabase.from("notices").insert(
            NewNotice(
                societyId = societyId,
                authorId = uid(),
                title = title.trim(),
                body = body.trim(),
                pinned = pinned,
            )
        )
    }

    suspend fun setPinned(noticeId: String, pinned: Boolean) {
        supabase.from("notices").update({ set("pinned", pinned) }) {
            filter { eq("id", noticeId) }
        }
    }

    suspend fun delete(noticeId: String) {
        supabase.from("notices").delete { filter { eq("id", noticeId) } }
    }
}
