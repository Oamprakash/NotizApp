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
        // Online payments (create-payment-link Edge Function and payment RPCs)
        "online_payments_not_enabled" in m -> uiText(Res.string.error_online_off)
        "not_your_flat" in m -> uiText(Res.string.error_not_allowed)
        "already_reported" in m -> uiText(Res.string.error_already_submitted)
        "razorpay 401" in m || "authentication failed" in m -> uiText(Res.string.error_razorpay_keys)
        "razorpay" in m && "key id" in m -> uiText(Res.string.error_razorpay_key_id)
        "enter the razorpay key secret" in m -> uiText(Res.string.error_razorpay_secret)
        "save the razorpay keys first" in m -> uiText(Res.string.error_razorpay_save_first)
        "server_error" in m && "razorpay" in m -> uiText(Res.string.error_razorpay_down)
        else -> null
    }
}
