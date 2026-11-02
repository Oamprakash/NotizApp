package com.notiz.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ChatRepository(private val supabase: SupabaseClient) {

    private fun uid() = supabase.auth.currentUserOrNull()?.id ?: error("Not signed in")

    /** Latest messages, oldest first. */
    suspend fun recent(societyId: String, count: Long = 100): List<Message> =
        supabase.from("messages")
            .select {
                filter { eq("society_id", societyId) }
                order("created_at", Order.DESCENDING)
                limit(count)
            }
            .decodeList<Message>()
            .reversed()

    /** Sends and returns the stored message (with server-filled name, flat and time). */
    suspend fun sendMessage(societyId: String, body: String, replyToId: String? = null): Message =
        supabase.from("messages")
            .insert(NewMessage(societyId = societyId, senderId = uid(), body = body.trim(), replyToId = replyToId)) { select() }
            .decodeSingle()

    /**
     * New and changed (deleted) messages for a society, live over Supabase Realtime.
     * Unsubscribes when collection stops.
     */
    fun liveMessages(societyId: String): Flow<Message> = channelFlow {
        val channel = supabase.channel("chat-$societyId")
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter("society_id", FilterOperator.EQ, societyId)
        }
        val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "messages"
            filter("society_id", FilterOperator.EQ, societyId)
        }
        // Start collecting before subscribing so no early message is missed
        val insertJob = launch(start = CoroutineStart.UNDISPATCHED) {
            inserts.collect { action -> send(action.decodeRecord<Message>()) }
        }
        val updateJob = launch(start = CoroutineStart.UNDISPATCHED) {
            updates.collect { action -> send(action.decodeRecord<Message>()) }
        }
        channel.subscribe()
        try {
            awaitCancellation()
        } finally {
            insertJob.cancel()
            updateJob.cancel()
            withContext(NonCancellable) { supabase.realtime.removeChannel(channel) }
        }
    }

    // ───────────── Moderation ─────────────

    /** Admin: messages reported since [sinceIso] that are still visible (not deleted). */
    suspend fun openReportCount(societyId: String, sinceIso: String): Int =
        supabase.from("message_reports")
            .select(Columns.raw("message_id, messages(deleted_at)")) {
                filter {
                    eq("society_id", societyId)
                    gte("created_at", sinceIso)
                }
            }
            .decodeList<ReportRow>()
            .filter { it.messages?.deletedAt == null }
            .map { it.messageId }
            .distinct()
            .size

    /** Sender or admin. Wipes the text; a "message deleted" stub stays in the chat. */
    suspend fun deleteMessage(messageId: String) {
        supabase.postgrest.rpc("delete_message", buildJsonObject { put("p_message_id", messageId) })
    }

    /** Tells the admins. Returns false if you had already reported this message. */
    suspend fun reportMessage(messageId: String): Boolean =
        supabase.postgrest.rpc("report_message", buildJsonObject { put("p_message_id", messageId) }).decodeAs()

    /** Admin: stop (or allow again) a member sending chat messages. */
    suspend fun setMuted(societyId: String, userId: String, muted: Boolean) {
        supabase.postgrest.rpc(
            "set_chat_muted",
            buildJsonObject {
                put("p_society_id", societyId)
                put("p_user_id", userId)
                put("p_muted", muted)
            },
        )
    }
}
