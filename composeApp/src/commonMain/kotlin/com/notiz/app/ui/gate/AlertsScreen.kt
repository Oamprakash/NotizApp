package com.notiz.app.ui.gate

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notiz.app.AppContainer
import com.notiz.app.LocalAppContainer
import com.notiz.app.i18n.UiText
import com.notiz.app.i18n.asString
import com.notiz.app.i18n.uiText
import com.notiz.app.push.AlertCheck
import com.notiz.app.push.rememberAlertCheck
import com.notiz.app.resources.*
import com.notiz.app.ui.BaseViewModel
import com.notiz.app.ui.components.DetailTopBar
import com.notiz.app.ui.components.ErrorBanner
import com.notiz.app.ui.components.GroupCard
import com.notiz.app.ui.components.InfoBanner
import com.notiz.app.ui.components.NotizCard
import com.notiz.app.ui.components.NotizTextButton
import com.notiz.app.ui.components.PrimaryButton
import com.notiz.app.ui.theme.NotizIcons
import com.notiz.app.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

data class AlertsUiState(val busy: Boolean = false, val info: UiText? = null, val error: UiText? = null)

class AlertsViewModel(private val container: AppContainer) : BaseViewModel() {
    val state = MutableStateFlow(AlertsUiState())

    fun sendTest() {
        state.update { AlertsUiState(busy = true) }
        launchSafe(onError = { msg -> state.update { AlertsUiState(error = msg) } }) {
            container.gate.sendTestAlert()
            state.update { AlertsUiState(info = uiText(Res.string.alerts_test_sent)) }
        }
    }

    fun dismiss() = state.update { it.copy(info = null, error = null) }
}

/** Phone makers that stop apps in the background unless you allow it, with what to switch on. */
private fun brandTip(brand: String): StringResource? = when {
    brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> Res.string.alerts_tip_xiaomi
    brand.contains("oneplus") || brand.contains("oppo") || brand.contains("realme") -> Res.string.alerts_tip_coloros
    brand.contains("vivo") || brand.contains("iqoo") -> Res.string.alerts_tip_vivo
    brand.contains("samsung") -> Res.string.alerts_tip_samsung
    brand.contains("motorola") || brand.contains("google") || brand.contains("nothing") -> null
    brand == "apple" -> null
    else -> Res.string.alerts_tip_other
}

/** Settings → Visitor alerts: checks everything that could stop an alert reaching this phone. */
@Composable
fun AlertsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { AlertsViewModel(container) }
    val state by vm.state.collectAsState()
    val check = rememberAlertCheck()
    val health = check.health
    val ios = health.brand == "apple"

    Scaffold(
        containerColor = Palette.ground,
        topBar = { DetailTopBar(stringResource(Res.string.alerts_title), onBack) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ErrorBanner(state.error?.asString(), onDismiss = vm::dismiss)
            InfoBanner(state.info?.asString(), onDismiss = vm::dismiss)

            // Summary
            Row(
                Modifier.fillMaxWidth()
                    .background(if (health.allGood) Palette.tealSoft else Palette.redSoft, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    if (health.allGood) NotizIcons.Check else NotizIcons.Flag,
                    contentDescription = null,
                    tint = if (health.allGood) Palette.teal else Palette.red,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (health.allGood) Res.string.alerts_all_good else Res.string.alerts_needs_fix),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (health.allGood) Palette.teal else Palette.red,
                    )
                    Text(stringResource(Res.string.alerts_intro), fontSize = 13.sp, color = Palette.inkSoft, lineHeight = 18.sp)
                }
            }

            GroupCard {
                CheckRow(stringResource(Res.string.alerts_check_notifications), health.notificationsOn, onFix = check::openNotificationSettings)
                CheckRow(
                    stringResource(if (ios) Res.string.alerts_check_time_sensitive else Res.string.alerts_check_channel),
                    health.visitorAlertsOn,
                    onFix = check::openVisitorAlertSettings,
                )
                CheckRow(
                    stringResource(if (ios) Res.string.alerts_check_background_ios else Res.string.alerts_check_battery),
                    health.backgroundOn,
                    onFix = check::openBackgroundSettings,
                    divider = false,
                )
            }

            brandTip(health.brand)?.let { tip ->
                NotizCard {
                    Text(stringResource(Res.string.alerts_tip_title), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(stringResource(tip), color = Palette.inkSoft, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
                    NotizTextButton(onClick = check::openBackgroundSettings) {
                        Text(stringResource(Res.string.alerts_open_settings), color = Palette.teal, fontWeight = FontWeight.Bold)
                    }
                }
            }

            PrimaryButton(
                stringResource(if (state.busy) Res.string.action_sending else Res.string.alerts_send_test),
                onClick = vm::sendTest,
                enabled = !state.busy,
                icon = NotizIcons.Bell,
                height = 52.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(Res.string.alerts_test_hint), color = Palette.muted, fontSize = 12.sp, lineHeight = 17.sp)
            Box(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CheckRow(title: String, ok: Boolean?, onFix: () -> Unit, divider: Boolean = true) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val good = ok != false
            Box(
                Modifier.size(26.dp).background(if (good) Palette.teal else Palette.red, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (good) "✓" else "!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (!good) {
                NotizTextButton(onClick = onFix) {
                    Text(stringResource(Res.string.alerts_fix), color = Palette.red, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (divider) Box(Modifier.fillMaxWidth().padding(start = 52.dp).height(1.dp).background(Palette.lineSoft))
    }
}

/** Shown on the Gate screen when something would stop visitor alerts on this phone. */
@Composable
fun AlertWarningCard(check: AlertCheck, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    if (check.health.allGood) return
    NotizCard(onClick = onOpen, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(NotizIcons.Flag, contentDescription = null, tint = Palette.red, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.alerts_warning_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Palette.red)
                Text(stringResource(Res.string.alerts_warning_body), color = Palette.inkSoft, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Icon(NotizIcons.ChevronRight, contentDescription = null, tint = Palette.muted, modifier = Modifier.size(20.dp))
        }
    }
}
