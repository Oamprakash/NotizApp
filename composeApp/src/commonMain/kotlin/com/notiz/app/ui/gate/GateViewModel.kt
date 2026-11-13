package com.notiz.app.ui.gate

import com.notiz.app.AppContainer
import com.notiz.app.data.AttachmentRepository
import com.notiz.app.data.GateEntry
import com.notiz.app.data.GatePass
import com.notiz.app.data.GateStatus
import com.notiz.app.i18n.UiText
import com.notiz.app.i18n.uiText
import com.notiz.app.resources.*
import com.notiz.app.ui.BaseViewModel
import com.notiz.app.ui.SessionContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

data class GateUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadError: UiText? = null,
    val error: UiText? = null,
    val info: UiText? = null,
    val busyId: String? = null,
    val sheetBusy: Boolean = false,
    val entries: List<GateEntry> = emptyList(),
    val passes: List<GatePass> = emptyList(),
    /** Flats with residents (gate desk only), for the flat field suggestions. */
    val flats: List<String> = emptyList(),
    /** The pass just created, shown with its code and a share button. */
    val newPass: GatePass? = null,
    val today: String = "",
) {
    val atGate get() = entries.filter { it.isOpen }
    val inside get() = entries.filter { it.status == GateStatus.INSIDE }
    val done get() = entries.filter { it.status == GateStatus.LEFT || it.status == GateStatus.CANCELLED }
    val waiting get() = entries.filter { it.status == GateStatus.WAITING }
    val passesToday get() = passes.filter { it.validOn == today }
}

/**
 * One view model for both sides of the gate. [desk] = the security desk (whole society, last 24 h);
 * otherwise the signed-in resident's own flat (last 30 days) and their guest passes.
 */
@OptIn(ExperimentalTime::class)
class GateViewModel(
    private val container: AppContainer,
    private val session: SessionContext,
    val desk: Boolean,
) : BaseViewModel() {
    val state = MutableStateFlow(GateUiState())

    init { load() }

    private fun todayIso() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    fun load(pullToRefresh: Boolean = false) {
        state.update { it.copy(loadError = null, refreshing = pullToRefresh) }
        launchSafe(onError = { msg -> state.update { it.copy(loading = false, refreshing = false, loadError = msg) } }) {
            val sid = session.societyId
            val today = todayIso()
            val since = (Clock.System.now() - if (desk) 1.days else 30.days).toString()
            val flat = if (desk) null else session.flatNo
            val entries = container.gate.entries(sid, since, flat)
            val passes = container.gate.openPasses(sid, today, flat)
            val flats = if (desk && state.value.flats.isEmpty()) {
                runCatching { container.societies.directory(sid) }.getOrDefault(emptyList())
                    .map { it.flatNo }.filter { it.isNotBlank() }.distinct().sorted()
            } else {
                state.value.flats
            }
            state.update {
                it.copy(loading = false, refreshing = false, entries = entries, passes = passes, flats = flats, today = today)
            }
        }
    }

    private fun act(id: String, success: UiText? = null, block: suspend () -> Unit) {
        state.update { it.copy(busyId = id, error = null, info = null) }
        launchSafe(onError = { msg -> state.update { it.copy(busyId = null, error = msg) }; load() }) {
            block()
            state.update { it.copy(busyId = null, info = success) }
            load()
        }
    }

    // Resident
    fun respond(entry: GateEntry, allow: Boolean) =
        act(entry.id, uiText(if (allow) Res.string.gate_allowed_info else Res.string.gate_denied_info)) {
            container.gate.respond(entry.id, allow)
        }

    fun createPass(name: String, validOn: String, category: String) {
        state.update { it.copy(sheetBusy = true, error = null, info = null) }
        launchSafe(onError = { msg -> state.update { it.copy(sheetBusy = false, error = msg) } }) {
            val pass = container.gate.createPass(session.societyId, name, validOn, category)
            state.update { it.copy(sheetBusy = false, newPass = pass) }
            load()
        }
    }

    fun cancelPass(pass: GatePass) = act(pass.id, uiText(Res.string.gate_pass_cancelled)) { container.gate.cancelPass(pass.id) }

    fun closeNewPass() = state.update { it.copy(newPass = null) }

    // Gate desk
    fun logVisitor(
        flat: String,
        category: String,
        name: String,
        phone: String,
        company: String,
        vehicle: String,
        letIn: Boolean,
        photo: ByteArray?,
        onDone: () -> Unit,
    ) {
        state.update { it.copy(sheetBusy = true, error = null, info = null) }
        var uploaded: String? = null
        launchSafe(
            onError = { msg ->
                // Don't leave an orphan photo behind if logging failed
                uploaded?.let { path -> launchSafe(onError = {}) { container.attachments.delete(listOf(path)) } }
                state.update { it.copy(sheetBusy = false, error = msg) }
            },
        ) {
            uploaded = photo?.let { container.attachments.upload(session.societyId, AttachmentRepository.Kind.GATE, it) }
            val e = container.gate.logVisitor(
                session.societyId, flat, category, name, phone, company, vehicle, letIn, uploaded.orEmpty(),
            )
            state.update {
                it.copy(
                    sheetBusy = false,
                    info = uiText(if (letIn) Res.string.gate_logged_in else Res.string.gate_logged_asked, e.flatNo),
                )
            }
            onDone()
            load()
        }
    }

    fun useCode(code: String, onDone: () -> Unit) {
        state.update { it.copy(sheetBusy = true, error = null, info = null) }
        launchSafe(onError = { msg -> state.update { it.copy(sheetBusy = false, error = msg) } }) {
            val e = container.gate.usePass(session.societyId, code)
            state.update { it.copy(sheetBusy = false, info = uiText(Res.string.gate_code_ok, e.visitorName, e.flatNo)) }
            onDone()
            load()
        }
    }

    fun markIn(entry: GateEntry) = act(entry.id) { container.gate.markIn(entry.id) }
    fun markOut(entry: GateEntry) = act(entry.id) { container.gate.markOut(entry.id) }
    fun cancel(entry: GateEntry) = act(entry.id) { container.gate.cancel(entry.id) }

    fun dismiss() = state.update { it.copy(error = null, info = null) }

    companion object {
        /** Days a resident can pick for a guest pass: today and the next few. */
        @OptIn(ExperimentalTime::class)
        fun passDays(count: Int = 5): List<String> {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            return (0 until count).map { today.plus(it, DateTimeUnit.DAY).toString() }
        }
    }
}
