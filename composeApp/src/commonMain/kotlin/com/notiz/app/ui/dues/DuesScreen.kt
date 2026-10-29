@file:OptIn(ExperimentalMaterial3Api::class)

package com.notiz.app.ui.dues

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notiz.app.AppContainer
import com.notiz.app.LocalAppContainer
import com.notiz.app.data.Due
import com.notiz.app.data.DueStatus
import com.notiz.app.push.OnPushUpdate
import com.notiz.app.push.PushTypes
import com.notiz.app.ui.BaseViewModel
import com.notiz.app.ui.SessionContext
import com.notiz.app.ui.components.EmptyState
import com.notiz.app.ui.components.ErrorBanner
import com.notiz.app.ui.components.ErrorBox
import com.notiz.app.ui.components.InfoBanner
import com.notiz.app.ui.components.LoadingBox
import com.notiz.app.ui.components.StatusPill
import com.notiz.app.ui.theme.StatusColors
import com.notiz.app.util.Upi
import com.notiz.app.util.formatRupees
import com.notiz.app.util.isValidIsoDate
import com.notiz.app.util.parseRupeesToPaise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import com.notiz.app.resources.*
import org.jetbrains.compose.resources.stringResource
import com.notiz.app.i18n.UiText
import com.notiz.app.i18n.asString
import com.notiz.app.i18n.uiText
import com.notiz.app.i18n.localDate
import org.jetbrains.compose.resources.StringResource
import com.notiz.app.export.MimeTypes
import com.notiz.app.export.ReceiptData
import com.notiz.app.export.buildDuesWorkbook
import com.notiz.app.export.buildReceiptPdf
import com.notiz.app.export.duesFileName
import com.notiz.app.export.receiptFileName
import com.notiz.app.export.receiptNumberLabel
import com.notiz.app.export.rememberFileSharer
import androidx.lifecycle.compose.LifecycleResumeEffect

enum class DuesFilter(val label: StringResource) {
    ALL(Res.string.dues_filter_all),
    TO_VERIFY(Res.string.dues_to_verify),
    PENDING(Res.string.dues_unpaid),
    PAID(Res.string.dues_paid),
}

data class DuesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val dues: List<Due> = emptyList(),
    val filter: DuesFilter = DuesFilter.ALL,
    val loadError: UiText? = null,
    val actionError: UiText? = null,
    val info: UiText? = null,
    val busy: Boolean = false,
    /** The society accepts online (Razorpay) payments */
    val onlineEnabled: Boolean = false,
) {
    val visible: List<Due>
        get() = when (filter) {
            DuesFilter.ALL -> dues
            DuesFilter.TO_VERIFY -> dues.filter { it.status == DueStatus.SUBMITTED }
            DuesFilter.PENDING -> dues.filter { it.status == DueStatus.PENDING }
            DuesFilter.PAID -> dues.filter { it.status == DueStatus.PAID }
        }
    val collectedPaise get() = dues.filter { it.status == DueStatus.PAID }.sumOf { it.amountPaise }
    val outstandingPaise get() = dues.filter { it.status != DueStatus.PAID }.sumOf { it.amountPaise }
    val toVerifyCount get() = dues.count { it.status == DueStatus.SUBMITTED }
}

class DuesViewModel(
    private val container: AppContainer,
    private val session: SessionContext,
) : BaseViewModel() {
    val state = MutableStateFlow(DuesUiState())

    init { load() }

    fun load(pullToRefresh: Boolean = false) {
        state.update { it.copy(refreshing = pullToRefresh, loadError = null) }
        launchSafe(onError = { msg -> state.update { it.copy(loading = false, refreshing = false, loadError = msg) } }) {
            var dues = container.dues.dues(session.societyId)
            val online = runCatching { container.payments.onlineEnabled(session.societyId) }.getOrDefault(false)
            state.update { it.copy(loading = false, refreshing = false, dues = dues, onlineEnabled = online) }

            // Came back from Razorpay? Confirm any paid links now in case the webhook is slow.
            val waiting = dues.filter {
                it.status == DueStatus.PENDING && it.paymentLinkId != null && it.flatNo == session.flatNo
            }
            val nowPaid = waiting.count { due ->
                runCatching { container.payments.checkOnlinePayment(due.id).status == DueStatus.PAID }.getOrDefault(false)
            }
            if (nowPaid > 0) {
                dues = container.dues.dues(session.societyId)
                state.update { it.copy(dues = dues, info = uiText(Res.string.dues_online_confirmed)) }
            }
        }
    }

    /** Opens Razorpay for [due] via [openUrl]; the due turns paid by webhook (or the check on return). */
    fun payOnline(due: Due, openUrl: (String) -> Unit) {
        state.update { it.copy(busy = true, actionError = null, info = null) }
        launchSafe(onError = { msg -> state.update { it.copy(busy = false, actionError = msg) } }) {
            val result = container.payments.payOnline(due.id)
            val url = result.url
            if (result.status == DueStatus.PAID || url == null) {
                state.update { it.copy(busy = false, info = uiText(Res.string.dues_online_confirmed)) }
                load()
            } else {
                state.update { it.copy(busy = false, info = uiText(Res.string.dues_online_hint)) }
                openUrl(url)
            }
        }
    }

    fun setFilter(filter: DuesFilter) = state.update { it.copy(filter = filter) }
    fun dismissMessages() = state.update { it.copy(actionError = null, info = null) }

    private fun action(successInfo: UiText? = null, onDone: () -> Unit = {}, block: suspend () -> Unit) {
        state.update { it.copy(busy = true, actionError = null, info = null) }
        launchSafe(onError = { msg -> state.update { it.copy(busy = false, actionError = msg) } }) {
            block()
            state.update { it.copy(busy = false, info = successInfo) }
            onDone()
            load()
        }
    }

    fun submitUtr(due: Due, utr: String, onDone: () -> Unit) =
        action(uiText(Res.string.dues_info_reported), onDone) {
            container.dues.submitPayment(due.id, utr)
        }

    fun verify(due: Due, approve: Boolean) =
        action(if (approve) uiText(Res.string.dues_info_marked_paid, due.flatNo) else uiText(Res.string.dues_info_sent_back)) {
            container.dues.verifyPayment(due.id, approve)
        }

    fun raiseBill(title: String, amountPaise: Long, dueDate: String, onDone: () -> Unit) {
        state.update { it.copy(busy = true, actionError = null, info = null) }
        launchSafe(onError = { msg -> state.update { it.copy(busy = false, actionError = msg) } }) {
            val flats = container.dues.raiseBill(session.societyId, title, amountPaise, dueDate)
            state.update { it.copy(busy = false, info = uiText(Res.string.dues_info_bill_raised, flats)) }
            onDone()
            load()
        }
    }
}

@Composable
fun DuesTab(session: SessionContext, onOpenPaymentSettings: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val vm = viewModel { DuesViewModel(container, session) }
    val state by vm.state.collectAsState()
    OnPushUpdate(PushTypes.DUES) { vm.load() }
    // Returning from the Razorpay page (or any other app): refresh, which also confirms paid links
    var resumedOnce by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (resumedOnce) {
            vm.load()
        } else {
            resumedOnce = true
        }
        onPauseOrDispose { }
    }
    val uriHandler = LocalUriHandler.current

    var raising by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf<Due?>(null) }
    var payError by remember { mutableStateOf<UiText?>(null) }
    var cashFor by remember { mutableStateOf<Due?>(null) }
    val sharer = rememberFileSharer()

    fun share(build: () -> ByteArray, fileName: String, mimeType: String) {
        try {
            sharer.share(build(), fileName, mimeType)
        } catch (e: Exception) {
            payError = uiText(Res.string.dues_share_failed)
        }
    }

    fun shareReceipt(due: Due) {
        val no = due.receiptNo ?: return
        val data = ReceiptData(
            societyName = session.society.name,
            city = session.society.city,
            receiptNo = no,
            paidOn = due.paidAt ?: due.createdAt,
            flatNo = due.flatNo,
            payerName = due.payer?.fullName,
            billTitle = due.title,
            dueDate = due.dueDate,
            amountPaise = due.amountPaise,
            utr = due.utr,
            paymentMode = due.paymentMode,
        )
        share({ buildReceiptPdf(data) }, receiptFileName(data), MimeTypes.PDF)
    }

    fun exportExcel() = share(
        { buildDuesWorkbook(state.dues) },
        duesFileName(session.society.name, todayIso()),
        MimeTypes.XLSX,
    )

    fun openUpi(due: Due) {
        val society = session.society
        if (society.upiVpa.isBlank()) {
            payError = uiText(Res.string.dues_no_upi_id)
            return
        }
        val uri = Upi.payUri(
            vpa = society.upiVpa,
            payeeName = society.upiPayeeName.ifBlank { society.name },
            amountPaise = due.amountPaise,
            note = "${due.title} ${due.flatNo}",
            transactionRef = due.id.replace("-", ""),
        )
        try {
            uriHandler.openUri(uri)
        } catch (e: Exception) {
            payError = uiText(Res.string.dues_no_upi_app, formatRupees(due.amountPaise), society.upiVpa)
        }
        reporting = due // Ask for the UTR once they return
    }

    Box(modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingBox()
            state.loadError != null && state.dues.isEmpty() -> ErrorBox(state.loadError!!.asString(), onRetry = { vm.load() })
            else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(pullToRefresh = true) }) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (session.isAdmin) {
                        item { AdminSummary(state, onExport = ::exportExcel, onOpenPaymentSettings = onOpenPaymentSettings) }
                        item {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                DuesFilter.entries.forEach { f ->
                                    FilterChip(
                                        selected = state.filter == f,
                                        onClick = { vm.setFilter(f) },
                                        label = { Text(stringResource(f.label)) },
                                    )
                                }
                            }
                        }
                    }
                    val list = if (session.isAdmin) state.visible else state.dues
                    if (list.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 48.dp)) {
                                EmptyState(
                                    stringResource(Res.string.dues_empty_title),
                                    if (session.isAdmin) {
                                        stringResource(Res.string.dues_empty_admin)
                                    } else {
                                        stringResource(Res.string.dues_empty_resident)
                                    },
                                )
                            }
                        }
                    }
                    items(list, key = { it.id }) { due ->
                        DueCard(
                            due = due,
                            isAdmin = session.isAdmin,
                            isMyFlat = due.flatNo == session.flatNo,
                            busy = state.busy,
                            onlineEnabled = state.onlineEnabled,
                            onPayOnline = { vm.payOnline(due) { url -> runCatching { uriHandler.openUri(url) } } },
                            onPay = { openUpi(due) },
                            onReport = { reporting = due },
                            onVerify = { approve -> vm.verify(due, approve) },
                            onPaidInCash = { cashFor = due },
                            onReceipt = { shareReceipt(due) },
                        )
                    }
                }
            }
        }

        if (session.isAdmin) {
            ExtendedFloatingActionButton(
                onClick = { raising = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(Res.string.dues_raise_bill)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
        Column(Modifier.align(Alignment.TopCenter)) {
            ErrorBanner(state.actionError?.asString() ?: payError?.asString(), onDismiss = { vm.dismissMessages(); payError = null })
            InfoBanner(state.info?.asString(), onDismiss = vm::dismissMessages)
        }
    }

    reporting?.let { due ->
        ReportPaymentDialog(
            due = due,
            upiVpa = session.society.upiVpa,
            busy = state.busy,
            onDismiss = { reporting = null },
            onSubmit = { utr -> vm.submitUtr(due, utr) { reporting = null } },
        )
    }
    cashFor?.let { due ->
        AlertDialog(
            onDismissRequest = { cashFor = null },
            title = { Text(stringResource(Res.string.dues_cash_title)) },
            text = { Text(stringResource(Res.string.dues_cash_body, due.flatNo, formatRupees(due.amountPaise), due.title)) },
            confirmButton = {
                TextButton(onClick = { cashFor = null; vm.verify(due, approve = true) }) {
                    Text(stringResource(Res.string.dues_mark_paid))
                }
            },
            dismissButton = { TextButton(onClick = { cashFor = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    if (raising) {
        RaiseBillDialog(
            busy = state.busy,
            onDismiss = { raising = false },
            onRaise = { title, paise, date -> vm.raiseBill(title, paise, date) { raising = false } },
        )
    }
}

@Composable
private fun AdminSummary(state: DuesUiState, onExport: () -> Unit, onOpenPaymentSettings: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryFigure(stringResource(Res.string.dues_collected), formatRupees(state.collectedPaise))
                SummaryFigure(stringResource(Res.string.dues_outstanding), formatRupees(state.outstandingPaise))
                SummaryFigure(stringResource(Res.string.dues_to_verify), state.toVerifyCount.toString())
            }
            Spacer(Modifier.height(12.dp))
            if (state.dues.isNotEmpty()) {
                OutlinedButton(onClick = onExport) { Text(stringResource(Res.string.dues_export_excel)) }
            }
            TextButton(onClick = onOpenPaymentSettings) {
                Text(
                    stringResource(
                        if (state.onlineEnabled) Res.string.dues_online_on else Res.string.dues_online_setup,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SummaryFigure(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun DueCard(
    due: Due,
    isAdmin: Boolean,
    isMyFlat: Boolean,
    busy: Boolean,
    onlineEnabled: Boolean,
    onPayOnline: () -> Unit,
    onPay: () -> Unit,
    onReport: () -> Unit,
    onVerify: (approve: Boolean) -> Unit,
    onPaidInCash: () -> Unit,
    onReceipt: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isAdmin) stringResource(Res.string.home_flat, due.flatNo) else due.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        (if (isAdmin) "${due.title} · " else "") +
                            stringResource(Res.string.dues_due_on, localDate(due.dueDate)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatRupees(due.amountPaise), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    DueStatusPill(due.status)
                }
            }
            if (!due.utr.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(Res.string.dues_utr, due.utr.orEmpty()), style = MaterialTheme.typography.bodySmall)
            }

            // Resident (or admin for their own flat): pay
            if (due.status == DueStatus.PENDING && (isMyFlat || !isAdmin)) {
                Spacer(Modifier.height(12.dp))
                if (onlineEnabled) {
                    // Online first: confirmed automatically, no UTR to type
                    Button(onClick = onPayOnline, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.dues_pay_online))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onPay, enabled = !busy) { Text(stringResource(Res.string.dues_pay_upi)) }
                        TextButton(onClick = onReport, enabled = !busy) { Text(stringResource(Res.string.dues_already_paid)) }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onPay, enabled = !busy) { Text(stringResource(Res.string.dues_pay_upi)) }
                        OutlinedButton(onClick = onReport, enabled = !busy) { Text(stringResource(Res.string.dues_already_paid)) }
                    }
                }
            }
            // Admin: confirm payments residents reported
            if (isAdmin && due.status == DueStatus.SUBMITTED) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onVerify(true) }, enabled = !busy) { Text(stringResource(Res.string.dues_mark_paid)) }
                    OutlinedButton(onClick = { onVerify(false) }, enabled = !busy) { Text(stringResource(Res.string.dues_not_received)) }
                }
            }
            // Admin: record a cash / cheque payment for an unpaid flat
            if (isAdmin && due.status == DueStatus.PENDING) {
                Spacer(Modifier.height(if (isMyFlat) 4.dp else 12.dp))
                TextButton(onClick = onPaidInCash, enabled = !busy) { Text(stringResource(Res.string.dues_paid_cash)) }
            }
            // Paid: receipt number + share the PDF
            if (due.status == DueStatus.PAID && due.receiptNo != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.dues_receipt_no, receiptNumberLabel(due.receiptNo)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = onReceipt) { Text(stringResource(Res.string.dues_receipt)) }
                }
            }
        }
    }
}

@Composable
private fun DueStatusPill(status: String) {
    when (status) {
        DueStatus.PAID -> StatusPill(stringResource(Res.string.dues_paid), StatusColors.success)
        DueStatus.SUBMITTED -> StatusPill(stringResource(Res.string.dues_verifying), StatusColors.info)
        else -> StatusPill(stringResource(Res.string.dues_unpaid), StatusColors.warning)
    }
}

@Composable
private fun ReportPaymentDialog(
    due: Due,
    upiVpa: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (utr: String) -> Unit,
) {
    var utr by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.dues_report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (upiVpa.isNotBlank()) {
                        stringResource(Res.string.dues_report_body_vpa, formatRupees(due.amountPaise), upiVpa)
                    } else {
                        stringResource(Res.string.dues_report_body, formatRupees(due.amountPaise))
                    },
                )
                OutlinedTextField(
                    value = utr,
                    onValueChange = { utr = it.filter(Char::isLetterOrDigit).take(30) },
                    label = { Text(stringResource(Res.string.dues_utr_field)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(utr) }, enabled = utr.length >= 6 && !busy) {
                Text(stringResource(if (busy) Res.string.action_sending else Res.string.action_submit))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(Res.string.action_later)) } },
    )
}

@OptIn(ExperimentalTime::class)
private fun todayIso(): String = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

@OptIn(ExperimentalTime::class)
private fun defaultDueDate(): String {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return today.plus(10, DateTimeUnit.DAY).toString()
}

@Composable
private fun RaiseBillDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onRaise: (title: String, amountPaise: Long, dueDate: String) -> Unit,
) {
    val defaultTitle = stringResource(Res.string.dues_default_title)
    var title by remember { mutableStateOf(defaultTitle) }
    var amount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(defaultDueDate()) }
    val paise = parseRupeesToPaise(amount)
    val dateOk = isValidIsoDate(dueDate)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.dues_raise_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.dues_raise_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(80) },
                    label = { Text(stringResource(Res.string.notice_field_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.take(10) },
                    label = { Text(stringResource(Res.string.dues_amount_field)) }, singleLine = true,
                    isError = amount.isNotEmpty() && paise == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dueDate, onValueChange = { dueDate = it.take(10) },
                    label = { Text(stringResource(Res.string.dues_date_field)) }, singleLine = true,
                    isError = !dateOk,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (paise != null) onRaise(title, paise, dueDate.trim()) },
                enabled = title.isNotBlank() && paise != null && dateOk && !busy,
            ) { Text(stringResource(if (busy) Res.string.dues_raising else Res.string.dues_raise_bill)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
