package com.notiz.app.ui.roles

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notiz.app.AppContainer
import com.notiz.app.LocalAppContainer
import com.notiz.app.data.Perms
import com.notiz.app.data.Roles
import com.notiz.app.i18n.UiText
import com.notiz.app.i18n.asString
import com.notiz.app.i18n.uiText
import com.notiz.app.resources.*
import com.notiz.app.ui.BaseViewModel
import com.notiz.app.ui.SessionContext
import com.notiz.app.ui.settings.ConfirmDialog
import com.notiz.app.ui.components.DetailTopBar
import com.notiz.app.ui.components.ErrorBanner
import com.notiz.app.ui.components.ErrorBox
import com.notiz.app.ui.components.FilterPill
import com.notiz.app.ui.components.GroupCard
import com.notiz.app.ui.components.InfoBanner
import com.notiz.app.ui.components.LoadingBox
import com.notiz.app.ui.components.NotizSwitch
import com.notiz.app.ui.components.SecondaryButton
import com.notiz.app.ui.components.permissionDescription
import com.notiz.app.ui.components.permissionTitle
import com.notiz.app.ui.components.roleDescription
import com.notiz.app.ui.components.roleLabel
import com.notiz.app.ui.theme.NotizIcons
import com.notiz.app.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.stringResource

data class RolesUiState(
    val loading: Boolean = true,
    val loadError: UiText? = null,
    val error: UiText? = null,
    val info: UiText? = null,
    /** role → permissions it has in this society */
    val grants: Map<String, Set<String>> = emptyMap(),
    val role: String = Roles.SECRETARY,
    val busy: Boolean = false,
) {
    val current: Set<String> get() = grants[role].orEmpty()
}

class RolesViewModel(
    private val container: AppContainer,
    private val session: SessionContext,
) : BaseViewModel() {
    val state = MutableStateFlow(RolesUiState())

    init { load() }

    fun load() {
        state.update { it.copy(loadError = null) }
        launchSafe(onError = { msg -> state.update { it.copy(loading = false, loadError = msg) } }) {
            val rows = container.societies.rolePermissions(session.societyId)
            val grants = rows.groupBy({ it.role }, { it.permission }).mapValues { it.value.toSet() }
            state.update { it.copy(loading = false, grants = grants) }
        }
    }

    fun select(role: String) = state.update { it.copy(role = role, error = null, info = null) }

    /** Flips the switch straight away and puts it back if the server says no. */
    fun toggle(permission: String, enabled: Boolean) {
        val role = state.value.role
        fun apply(on: Boolean) = state.update { s ->
            val set = s.grants[role].orEmpty().let { if (on) it + permission else it - permission }
            s.copy(grants = s.grants + (role to set))
        }
        apply(enabled)
        state.update { it.copy(error = null, info = null) }
        launchSafe(onError = { msg -> apply(!enabled); state.update { it.copy(error = msg) } }) {
            container.societies.setRolePermission(session.societyId, role, permission, enabled)
        }
    }

    fun reset() {
        val role = state.value.role
        state.update { it.copy(busy = true, error = null, info = null) }
        launchSafe(onError = { msg -> state.update { it.copy(busy = false, error = msg) } }) {
            container.societies.resetRolePermissions(session.societyId, role)
            state.update { it.copy(busy = false, info = uiText(Res.string.roles_reset_done)) }
            load()
        }
    }

    fun dismiss() = state.update { it.copy(error = null, info = null) }
}

/** President only: what each role can do in this society. */
@Composable
fun RolesScreen(session: SessionContext, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { RolesViewModel(container, session) }
    val state by vm.state.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Palette.ground,
        topBar = { DetailTopBar(stringResource(Res.string.roles_title), onBack) },
    ) { padding ->
        when {
            !session.isPresident -> ErrorBox(stringResource(Res.string.roles_president_only), onRetry = onBack, modifier = Modifier.padding(padding))
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.loadError != null && state.grants.isEmpty() ->
                ErrorBox(state.loadError!!.asString(), onRetry = vm::load, modifier = Modifier.padding(padding))
            else -> Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ErrorBanner(state.error?.asString(), onDismiss = vm::dismiss)
                InfoBanner(state.info?.asString(), onDismiss = vm::dismiss)

                Text(stringResource(Res.string.roles_intro), color = Palette.muted, fontSize = 14.sp, lineHeight = 20.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Roles.editable.forEach { r -> FilterPill(roleLabel(r), selected = state.role == r, onClick = { vm.select(r) }) }
                }

                // What the selected role is for
                Row(
                    Modifier.fillMaxWidth().background(Palette.tealSoft, RoundedCornerShape(16.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        if (state.role == Roles.SECURITY) NotizIcons.Shield else NotizIcons.Key,
                        contentDescription = null,
                        tint = Palette.teal,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(roleLabel(state.role), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Palette.teal)
                        Text(roleDescription(state.role), fontSize = 13.sp, color = Palette.inkSoft, lineHeight = 18.sp)
                    }
                }

                GroupCard {
                    Perms.all.forEachIndexed { i, p ->
                        PermissionRow(
                            title = permissionTitle(p),
                            description = permissionDescription(p),
                            checked = p in state.current,
                            enabled = !state.busy,
                            divider = i < Perms.all.lastIndex,
                            onChange = { vm.toggle(p, it) },
                        )
                    }
                }

                if (state.role == Roles.SECURITY || state.role == Roles.RESIDENT) {
                    Text(stringResource(Res.string.roles_resident_note), color = Palette.muted, fontSize = 12.sp, lineHeight = 17.sp)
                }
                Text(stringResource(Res.string.roles_president_note), color = Palette.muted, fontSize = 12.sp, lineHeight = 17.sp)
                SecondaryButton(
                    stringResource(Res.string.roles_reset),
                    onClick = { confirmReset = true },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.height(12.dp))
            }
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            text = stringResource(Res.string.roles_reset_confirm, roleLabel(state.role)),
            confirm = stringResource(Res.string.roles_reset),
            onDismiss = { confirmReset = false },
            onConfirm = { confirmReset = false; vm.reset() },
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    divider: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.ink)
                Text(description, fontSize = 12.sp, color = Palette.muted, lineHeight = 16.sp)
            }
            NotizSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        }
        if (divider) Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(Palette.lineSoft))
    }
}
