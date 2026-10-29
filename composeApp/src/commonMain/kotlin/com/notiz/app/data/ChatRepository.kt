package com.notiz.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
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
    suspend fun sendMessage(societyId: String, body: String): Message =
        supabase.from("messages")
            .insert(NewMessage(societyId = societyId, senderId = uid(), body = body.trim())) { select() }
            .decodeSingle()

    /** New messages for a society, live over Supabase Realtime. Unsubscribes when collection stops. */
    fun newMessages(societyId: String): Flow<Message> = channelFlow {
        val channel = supabase.channel("chat-$societyId")
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter("society_id", FilterOperator.EQ, societyId)
        }
        // Start collecting before subscribing so no early message is missed
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            inserts.collect { action -> send(action.decodeRecord<Message>()) }
        }
        channel.subscribe()
        try {
            awaitCancellation()
        } finally {
            job.cancel()
            withContext(NonCancellable) { supabase.realtime.removeChannel(channel) }
        }
    }
}
