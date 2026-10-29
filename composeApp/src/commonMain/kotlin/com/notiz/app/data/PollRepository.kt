package com.notiz.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class PollRepository(private val supabase: SupabaseClient) {

    suspend fun polls(societyId: String): List<Poll> =
        supabase.from("polls")
            .select(Columns.raw("*, poll_options(*)")) {
                filter { eq("society_id", societyId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<Poll>()
            .map { poll -> poll.copy(options = poll.options.sortedBy { it.position }) }

    /** Vote counts keyed by option id. */
    suspend fun results(societyId: String): Map<String, Long> =
        supabase.postgrest.rpc("poll_results", buildJsonObject { put("p_society_id", societyId) })
            .decodeList<PollResult>()
            .associate { it.optionId to it.votes }

    /** The option this flat voted for, keyed by poll id (RLS returns only the caller's flat). */
    suspend fun myFlatVotes(): Map<String, String> =
        supabase.from("poll_votes")
            .select(Columns.raw("poll_id, option_id"))
            .decodeList<PollVote>()
            .associate { it.pollId to it.optionId }

    suspend fun vote(pollId: String, optionId: String) {
        supabase.postgrest.rpc(
            "cast_vote",
            buildJsonObject {
                put("p_poll_id", pollId)
                put("p_option_id", optionId)
            },
        )
    }

    suspend fun create(societyId: String, question: String, options: List<String>) {
        supabase.postgrest.rpc(
            "create_poll",
            buildJsonObject {
                put("p_society_id", societyId)
                put("p_question", question.trim())
                putJsonArray("p_options") { options.forEach { add(it.trim()) } }
            },
        )
    }
}
