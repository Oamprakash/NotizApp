package com.notiz.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.notiz.app.data.Perms
import com.notiz.app.data.Roles
import com.notiz.app.resources.*
import com.notiz.app.ui.theme.Palette
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private fun roleRes(role: String): StringResource = when (role) {
    Roles.PRESIDENT -> Res.string.role_president
    Roles.SECRETARY -> Res.string.role_secretary
    Roles.TREASURER -> Res.string.role_treasurer
    Roles.SECURITY -> Res.string.role_security
    else -> Res.string.role_resident
}

/** "President", "Security staff"… in the app language. */
@Composable
fun roleLabel(role: String): String = stringResource(roleRes(role))

/** One line on what the role is for (role picker). */
@Composable
fun roleDescription(role: String): String = stringResource(
    when (role) {
        Roles.PRESIDENT -> Res.string.role_president_desc
        Roles.SECRETARY -> Res.string.role_secretary_desc
        Roles.TREASURER -> Res.string.role_treasurer_desc
        Roles.SECURITY -> Res.string.role_security_desc
        else -> Res.string.role_resident_desc
    },
)

@Composable
fun permissionTitle(permission: String): String = stringResource(
    when (permission) {
        Perms.NOTICES -> Res.string.perm_notices
        Perms.POLLS -> Res.string.perm_polls
        Perms.BILLS -> Res.string.perm_bills
        Perms.PAYMENTS -> Res.string.perm_payments
        Perms.PAYMENT_SETTINGS -> Res.string.perm_payment_settings
        Perms.MEMBERS -> Res.string.perm_members
        Perms.HELPDESK -> Res.string.perm_helpdesk
        Perms.MODERATION -> Res.string.perm_moderation
        Perms.GATE -> Res.string.perm_gate
        else -> Res.string.perm_society
    },
)

@Composable
fun permissionDescription(permission: String): String = stringResource(
    when (permission) {
        Perms.NOTICES -> Res.string.perm_notices_desc
        Perms.POLLS -> Res.string.perm_polls_desc
        Perms.BILLS -> Res.string.perm_bills_desc
        Perms.PAYMENTS -> Res.string.perm_payments_desc
        Perms.PAYMENT_SETTINGS -> Res.string.perm_payment_settings_desc
        Perms.MEMBERS -> Res.string.perm_members_desc
        Perms.HELPDESK -> Res.string.perm_helpdesk_desc
        Perms.MODERATION -> Res.string.perm_moderation_desc
        Perms.GATE -> Res.string.perm_gate_desc
        else -> Res.string.perm_society_desc
    },
)

/** Chip colour per role: committee teal, security blue (residents get no chip). */
fun roleColor(role: String): Color = when (role) {
    Roles.SECURITY -> Palette.blueText
    Roles.PRESIDENT -> Palette.tealDark
    else -> Palette.teal
}

/** Role chip for lists; nothing for plain residents. */
@Composable
fun RoleChip(role: String) {
    if (role == Roles.RESIDENT) return
    Chip(roleLabel(role), roleColor(role))
}
