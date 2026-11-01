package com.notiz.app.i18n

import androidx.compose.runtime.Composable
import com.notiz.app.resources.*
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Text produced outside the UI (ViewModels, errors) that is translated when it's shown,
 * so it follows the language the user picks.
 */
sealed interface UiText {
    data class Resource(val res: StringResource, val args: List<Any> = emptyList()) : UiText

    /** Text that can't be translated (e.g. an unexpected server message). */
    data class Plain(val text: String) : UiText
}

fun uiText(res: StringResource, vararg args: Any): UiText = UiText.Resource(res, args.toList())

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Resource -> stringResource(res, *args.toTypedArray())
    is UiText.Plain -> text
}

/** Turns a failure into a message for the UI, translating the errors our database raises. */
fun Throwable.toUiText(): UiText = when (this) {
    is AuthRestException -> UiText.Plain(errorDescription)
    is RestException -> serverError(error) ?: UiText.Plain(error)
    is HttpRequestException -> uiText(Res.string.error_no_internet)
    else -> message?.let { serverError(it) ?: UiText.Plain(it) } ?: uiText(Res.string.error_generic)
}

/** Messages raised by `raise exception` in the SQL migrations (supabase/migrations). */
private fun serverError(message: String): UiText? {
    val m = message.lowercase()
    return when {
        "invalid invite code" in m -> uiText(Res.string.error_invalid_invite)
        "admins only" in m -> uiText(Res.string.error_admins_only)
        "already submitted" in m -> uiText(Res.string.error_already_submitted)
        "valid utr" in m -> uiText(Res.string.error_invalid_utr)
        "at least two options" in m -> uiText(Res.string.error_two_options)
        "invalid option" in m -> uiText(Res.string.error_invalid_option)
        "not allowed" in m -> uiText(Res.string.error_not_allowed)
        // Admin tools (20261101000000_admin_tools.sql, delete-account Edge Function)
        "only unpaid dues can be cancelled" in m -> uiText(Res.string.error_only_unpaid)
        "this due was cancelled" in m || "due_cancelled" in m -> uiText(Res.string.error_due_cancelled)
        "make another member admin first" in m -> uiText(Res.string.error_make_admin_first)
        "use leave society" in m -> uiText(Res.string.error_use_leave)
        "enter the society name" in m -> uiText(Res.string.error_society_name)
        "enter the flat number" in m -> uiText(Res.string.error_flat_no)
        "enter a title" in m -> uiText(Res.string.error_enter_title)
        "enter a valid amount" in m -> uiText(Res.string.error_enter_amount)
        "upi id should look like" in m -> uiText(Res.string.setup_upi_invalid)
        "can't mute yourself" in m -> uiText(Res.string.error_cant_mute_self)
        "can't report your own message" in m -> uiText(Res.string.error_cant_report_own)
        // A muted member's insert is refused by the messages RLS policy
        "row-level security" in m && "messages" in m -> uiText(Res.string.chat_you_are_muted)
        "last_admin" in m -> uiText(Res.string.error_last_admin_delete, jsonMessage(message) ?: "")
        // Online payments (create-payment-link Edge Function and payment RPCs)
        "online_payments_not_enabled" in m -> uiText(Res.string.error_online_off)
        "not_your_flat" in m -> uiText(Res.string.error_not_allowed)
        "already_reported" in m -> uiText(Res.string.error_already_submitted)
        "razorpay 401" in m || "authentication failed" in m -> uiText(Res.string.error_razorpay_keys)
        "razorpay" in m && "key id" in m -> uiText(Res.string.error_razorpay_key_id)
        "enter the razorpay key secret" in m -> uiText(Res.string.error_razorpay_secret)
        "save the razorpay keys first" in m -> uiText(Res.string.error_razorpay_save_first)
        "razorpay_error" in m -> razorpayReason(message)?.let { uiText(Res.string.error_razorpay_said, it) }
            ?: uiText(Res.string.error_razorpay_down)
        "server_error" in m && "razorpay" in m -> uiText(Res.string.error_razorpay_down)
        else -> null
    }
}

/** "…\"message\":\"Razorpay 400: Order amount less than minimum…\"…" → "Order amount less than minimum…" */
private fun razorpayReason(message: String): String? =
    Regex("""Razorpay \d{3}: ([^"\\]+)""").find(message)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

/** The "message" field of a JSON error body returned by our Edge Functions. */
private fun jsonMessage(body: String): String? =
    Regex(""""message"\s*:\s*"([^"\\]+)"""").find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
