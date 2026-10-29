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
import com.notiz.app.util.formatDate
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

enum class DuesFilter(val label: String) { ALL("All"), TO_VERIFY("To verify"), PENDING("Unpaid"), PAID("Paid") }

data class DuesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val dues: List<Due> = emptyList(),
    val filter: DuesFilter = DuesFilter.ALL,
    val loadError: String? = null,
    val actionError: String? = null,
    val info: String? = null,
    val busy: Boolean = false,
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
            val dues = container.dues.dues(session.societyId)
            state.update { it.copy(loading = false, refreshing = false, dues = dues) }
        }
    }

    fun setFilter(filter: DuesFilter) = state.update { it.copy(filter = filter) }
    fun dismissMessages() = state.update { it.copy(actionError = null, info = null) }

    private fun action(successInfo: String? = null, onDone: () -> Unit = {}, block: suspend () -> Unit) {
        state.update { it.copy(busy = true, actionError = null, info = null) }
        launchSafe(onError = { msg -> state.update { it.copy(busy = false, actionError = msg) } }) {
            block()
            state.update { it.copy(busy = false, info = successInfo) }
            onDone()
            load()
        }
    }

    fun submitUtr(due: Due, utr: String, onDone: () -> Unit) =
        action("Payment reported. The treasurer will confirm it.", onDone) {
            container.dues.submitPayment(due.id, utr)
        }

    fun verify(due: Due, approve: Boolean) =
        action(if (approve) "Marked ${due.flatNo} as paid." else "Sent back to unpaid.") {
            container.dues.verifyPayment(due.id, approve)
        }

    fun raiseBill(title: String, amountPaise: Long, dueDate: String, onDone: () -> Unit) {
        state.update { it.copy(busy = true, actionError = null, info = null) }
        launchSafe(onError = { msg -> state.update { it.copy(busy = false, actionError = msg) } }) {
            val flats = container.dues.raiseBill(session.societyId, title, amountPaise, dueDate)
            state.update { it.copy(busy = false, info = "Bill raised for $flats flats.") }
            onDone()
            load()
        }
    }
}

@Composable
fun DuesTab(session: SessionContext, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val vm = viewModel { DuesViewModel(container, session) }
    val state by vm.state.collectAsState()
    OnPushUpdate(PushTypes.DUES) { vm.load() }
    val uriHandler = LocalUriHandler.current

    var raising by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf<Due?>(null) }
    var payError by remember { mutableStateOf<String?>(null) }

    fun openUpi(due: Due) {
        val society = session.society
        if (society.upiVpa.isBlank()) {
            payError = "Your society hasn't added a UPI ID yet. Ask the treasurer how to pay."
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
            payError = "No UPI app found. Pay ${formatRupees(due.amountPaise)} to ${society.upiVpa} manually."
        }
        reporting = due // Ask for the UTR once they return
    }

    Box(modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingBox()
            state.loadError != null && state.dues.isEmpty() -> ErrorBox(state.loadError!!, onRetry = { vm.load() })
            else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(pullToRefresh = true) }) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (session.isAdmin) {
                        item { AdminSummary(state) }
                        item {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                DuesFilter.entries.forEach { f ->
                                    FilterChip(
                                        selected = state.filter == f,
                                        onClick = { vm.setFilter(f) },
                                        label = { Text(f.label) },
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
                                    "Nothing here",
                                    if (session.isAdmin) "Raise a bill to collect maintenance from every flat."
                                    else "You're all settled. New bills will show up here.",
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
                            onPay = { openUpi(due) },
                            onReport = { reporting = due },
                            onVerify = { approve -> vm.verify(due, approve) },
                        )
                    }
                }
            }
        }

        if (session.isAdmin) {
            ExtendedFloatingActionButton(
                onClick = { raising = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Raise bill") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
        Column(Modifier.align(Alignment.TopCenter)) {
            ErrorBanner(state.actionError ?: payError, onDismiss = { vm.dismissMessages(); payError = null })
            InfoBanner(state.info, onDismiss = vm::dismissMessages)
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
    if (raising) {
        RaiseBillDialog(
            busy = state.busy,
            onDismiss = { raising = false },
            onRaise = { title, paise, date -> vm.raiseBill(title, paise, date) { raising = false } },
        )
    }
}

@Composable
private fun AdminSummary(state: DuesUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryFigure("Collected", formatRupees(state.collectedPaise))
            SummaryFigure("Outstanding", formatRupees(state.outstandingPaise))
            SummaryFigure("To verify", state.toVerifyCount.toString())
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
    onPay: () -> Unit,
    onReport: () -> Unit,
    onVerify: (approve: Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isAdmin) "Flat ${due.flatNo}" else due.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        (if (isAdmin) "${due.title} · " else "") + "Due ${formatDate(due.dueDate)}",
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
                Text("UTR: ${due.utr}", style = MaterialTheme.typography.bodySmall)
            }

            // Resident (or admin for their own flat): pay
            if (due.status == DueStatus.PENDING && (isMyFlat || !isAdmin)) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPay, enabled = !busy) { Text("Pay via UPI") }
                    OutlinedButton(onClick = onReport, enabled = !busy) { Text("Already paid") }
                }
            }
            // Admin: confirm payments residents reported
            if (isAdmin && due.status == DueStatus.SUBMITTED) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onVerify(true) }, enabled = !busy) { Text("Mark paid") }
                    OutlinedButton(onClick = { onVerify(false) }, enabled = !busy) { Text("Not received") }
                }
            }
        }
    }
}

@Composable
private fun DueStatusPill(status: String) {
    when (status) {
        DueStatus.PAID -> StatusPill("Paid", StatusColors.success)
        DueStatus.SUBMITTED -> StatusPill("Verifying", StatusColors.info)
        else -> StatusPill("Unpaid", StatusColors.warning)
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
        title = { Text("Report your payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "After paying ${formatRupees(due.amountPaise)}" +
                        (if (upiVpa.isNotBlank()) " to $upiVpa" else "") +
                        ", enter the 12-digit UTR / UPI reference number from your UPI app.",
                )
                OutlinedTextField(
                    value = utr,
                    onValueChange = { utr = it.filter(Char::isLetterOrDigit).take(30) },
                    label = { Text("UTR / reference no.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(utr) }, enabled = utr.length >= 6 && !busy) {
                Text(if (busy) "Sending…" else "Submit")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Later") } },
    )
}

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
    var title by remember { mutableStateOf("Maintenance") }
    var amount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(defaultDueDate()) }
    val paise = parseRupeesToPaise(amount)
    val dateOk = isValidIsoDate(dueDate)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Raise a bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Every approved flat gets this bill.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(80) },
                    label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.take(10) },
                    label = { Text("Amount per flat (₹)") }, singleLine = true,
                    isError = amount.isNotEmpty() && paise == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dueDate, onValueChange = { dueDate = it.take(10) },
                    label = { Text("Due date (YYYY-MM-DD)") }, singleLine = true,
                    isError = !dateOk,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (paise != null) onRaise(title, paise, dueDate.trim()) },
                enabled = title.isNotBlank() && paise != null && dateOk && !busy,
            ) { Text(if (busy) "Raising…" else "Raise bill") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
