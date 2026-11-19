@file:OptIn(ExperimentalMaterial3Api::class)

package com.notiz.app.ui.gate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notiz.app.LocalAppContainer
import com.notiz.app.data.GateCategory
import com.notiz.app.data.GateEntry
import com.notiz.app.data.GatePass
import com.notiz.app.data.GateRepository
import com.notiz.app.data.GateStatus
import com.notiz.app.i18n.asString
import com.notiz.app.i18n.localDate
import com.notiz.app.photos.LocalPhoto
import com.notiz.app.photos.RemotePhotoStrip
import com.notiz.app.photos.rememberCameraCapture
import com.notiz.app.push.OnPushUpdate
import com.notiz.app.push.rememberAlertCheck
import com.notiz.app.push.PushTypes
import com.notiz.app.resources.*
import com.notiz.app.ui.SessionContext
import com.notiz.app.ui.components.Chip
import com.notiz.app.ui.components.DetailTopBar
import com.notiz.app.ui.components.ErrorBanner
import com.notiz.app.ui.components.ErrorBox
import com.notiz.app.ui.components.IconTile
import com.notiz.app.ui.components.InfoBanner
import com.notiz.app.ui.components.LoadingBox
import com.notiz.app.ui.components.NotizCard
import com.notiz.app.ui.components.NotizDialog
import com.notiz.app.ui.components.NotizSheet
import com.notiz.app.ui.components.NotizTextButton
import com.notiz.app.ui.components.NotizTextField
import com.notiz.app.ui.components.PrimaryButton
import com.notiz.app.ui.components.QuickPick
import com.notiz.app.ui.components.ScreenTitle
import com.notiz.app.ui.components.SecondaryButton
import com.notiz.app.ui.components.SegmentedTabs
import com.notiz.app.ui.theme.NotizIcons
import com.notiz.app.ui.theme.Palette
import com.notiz.app.util.formatTime
import com.notiz.app.util.urlEncode
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

// ─────────────────────────────────────────────────────────────
// Labels
// ─────────────────────────────────────────────────────────────

@Composable
fun gateCategoryLabel(category: String): String = stringResource(
    when (category) {
        GateCategory.GUEST -> Res.string.gate_cat_guest
        GateCategory.DELIVERY -> Res.string.gate_cat_delivery
        GateCategory.CAB -> Res.string.gate_cat_cab
        GateCategory.SERVICE -> Res.string.gate_cat_service
        else -> Res.string.gate_cat_other
    },
)

fun gateCategoryIcon(category: String): ImageVector = when (category) {
    GateCategory.GUEST -> NotizIcons.User
    GateCategory.DELIVERY -> NotizIcons.Package
    GateCategory.CAB -> NotizIcons.Car
    GateCategory.SERVICE -> NotizIcons.Wrench
    else -> NotizIcons.Door
}

/** "Ravi · Swiggy delivery", "Uber cab", "Kumar · Guest" */
@Composable
fun visitorTitle(e: GateEntry): String {
    val kind = listOf(e.company, gateCategoryLabel(e.category)).filter { it.isNotBlank() }.joinToString(" ")
    return if (e.visitorName.isNotBlank()) "${e.visitorName} · $kind" else kind
}

@Composable
private fun StatusChip(e: GateEntry) {
    when (e.status) {
        GateStatus.WAITING -> Chip(stringResource(Res.string.gate_status_waiting), Palette.amberText, Palette.amberSoft)
        GateStatus.ALLOWED -> Chip(stringResource(Res.string.gate_status_allowed), Palette.teal, Palette.tealSoft)
        GateStatus.DENIED -> Chip(stringResource(Res.string.gate_status_denied), Palette.red, Palette.redSoft)
        GateStatus.INSIDE -> Chip(stringResource(Res.string.gate_status_inside), Palette.blueText, Palette.blueSoft)
        GateStatus.LEFT -> Chip(stringResource(Res.string.gate_status_left), Palette.muted, Palette.chip)
        else -> Chip(stringResource(Res.string.gate_status_cancelled), Palette.muted, Palette.chip)
    }
}

/** "In 4:30 PM · Out 5:10 PM" or "Arrived 4:30 PM" */
@Composable
private fun visitorTimes(e: GateEntry): String = listOfNotNull(
    e.enteredAt?.let { stringResource(Res.string.gate_time_in, formatTime(it)) },
    e.exitedAt?.let { stringResource(Res.string.gate_time_out, formatTime(it)) },
).ifEmpty { listOf(stringResource(Res.string.gate_time_arrived, formatTime(e.createdAt))) }.joinToString(" · ")

@Composable
private fun SectionLabel(text: String, count: Int? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (count != null) Text(count.toString(), color = Palette.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ─────────────────────────────────────────────────────────────
// Security desk
// ─────────────────────────────────────────────────────────────

/** Bottom-bar tab for security staff. */
@Composable
fun GateDeskTab(session: SessionContext, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            ScreenTitle(stringResource(Res.string.gate_title), subtitle = session.society.name) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Palette.chip).clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(NotizIcons.Settings, contentDescription = stringResource(Res.string.settings_title), tint = Palette.teal, modifier = Modifier.size(20.dp))
                }
            }
        }
        GateDesk(session, Modifier.weight(1f))
    }
}

@Composable
private fun GateDesk(session: SessionContext, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val vm = viewModel(key = "gate-desk") { GateViewModel(container, session, desk = true) }
    val state by vm.state.collectAsState()
    var logging by remember { mutableStateOf(false) }
    var enteringCode by remember { mutableStateOf(false) }
    OnPushUpdate(PushTypes.GATE) { vm.load() }
    // While a flat hasn't answered, check every 15 s in case a push is slow
    val anyWaiting = state.waiting.isNotEmpty()
    LaunchedEffect(anyWaiting) {
        while (anyWaiting) {
            delay(15_000)
            vm.load()
        }
    }

    Column(modifier) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                stringResource(Res.string.gate_new_visitor),
                onClick = { vm.dismiss(); logging = true },
                icon = NotizIcons.Plus,
                height = 52.dp,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                stringResource(Res.string.gate_enter_code),
                onClick = { vm.dismiss(); enteringCode = true },
                icon = NotizIcons.Key,
                modifier = Modifier.weight(1f).height(52.dp),
            )
        }
        ErrorBanner(state.error?.asString(), onDismiss = vm::dismiss, modifier = Modifier.padding(horizontal = 16.dp))
        InfoBanner(state.info?.asString(), onDismiss = vm::dismiss, modifier = Modifier.padding(horizontal = 16.dp))
        Box(Modifier.weight(1f)) {
            when {
                state.loading -> LoadingBox()
                state.loadError != null && state.entries.isEmpty() -> ErrorBox(state.loadError!!.asString(), onRetry = { vm.load() })
                else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(pullToRefresh = true) }) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.entries.isEmpty() && state.passesToday.isEmpty()) {
                            item { EmptyCard(stringResource(Res.string.gate_desk_empty_title), stringResource(Res.string.gate_desk_empty)) }
                        }
                        if (state.atGate.isNotEmpty()) {
                            item { SectionLabel(stringResource(Res.string.gate_at_gate), state.atGate.size) }
                            items(state.atGate, key = { it.id }) { e ->
                                DeskCard(e, busy = state.busyId == e.id, onIn = { vm.markIn(e) }, onCancel = { vm.cancel(e) })
                            }
                        }
                        if (state.inside.isNotEmpty()) {
                            item { SectionLabel(stringResource(Res.string.gate_inside_now), state.inside.size) }
                            items(state.inside, key = { it.id }) { e ->
                                InsideRow(e, busy = state.busyId == e.id, onOut = { vm.markOut(e) })
                            }
                        }
                        if (state.passesToday.isNotEmpty()) {
                            item { SectionLabel(stringResource(Res.string.gate_expected_today), state.passesToday.size) }
                            items(state.passesToday, key = { it.id }) { p -> ExpectedRow(p) }
                        }
                        if (state.done.isNotEmpty()) {
                            item { SectionLabel(stringResource(Res.string.gate_earlier)) }
                            items(state.done, key = { it.id }) { e -> LogRow(e, showFlat = true) }
                        }
                    }
                }
            }
        }
    }

    if (logging) {
        NewVisitorSheet(
            flats = state.flats,
            busy = state.sheetBusy,
            error = state.error?.asString(),
            onDismiss = { logging = false },
            onLog = { flat, cat, name, phone, company, vehicle, letIn, photo ->
                vm.logVisitor(flat, cat, name, phone, company, vehicle, letIn, photo, onDone = { logging = false })
            },
        )
    }
    if (enteringCode) {
        EnterCodeSheet(
            busy = state.sheetBusy,
            error = state.error?.asString(),
            onDismiss = { enteringCode = false },
            onCheck = { code -> vm.useCode(code, onDone = { enteringCode = false }) },
        )
    }
}

@Composable
private fun EmptyCard(title: String, body: String) {
    NotizCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(NotizIcons.Door, Palette.teal, Palette.tealSoft)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(body, color = Palette.muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun VisitorHeader(e: GateEntry, showFlat: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showFlat) {
            Box(
                Modifier.size(width = 60.dp, height = 44.dp).background(Palette.tealSoft, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(e.flatNo, color = Palette.teal, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            IconTile(gateCategoryIcon(e.category), Palette.teal, Palette.tealSoft, size = 44.dp, iconSize = 20.dp, radius = 12.dp)
        }
        if (e.photoPath.isNotBlank()) RemotePhotoStrip(listOf(e.photoPath), size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(visitorTitle(e), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val extra = listOf(e.vehicleNo, visitorTimes(e)).filter { it.isNotBlank() }.joinToString(" · ")
            Text(extra, color = Palette.muted, fontSize = 12.sp)
        }
        StatusChip(e)
    }
}

/** A visitor still at the gate, with what the guard can do next. */
@Composable
private fun DeskCard(e: GateEntry, busy: Boolean, onIn: () -> Unit, onCancel: () -> Unit) {
    NotizCard {
        VisitorHeader(e, showFlat = true)
        when (e.status) {
            GateStatus.ALLOWED -> Text(
                stringResource(Res.string.gate_allowed_by, e.decidedByName.ifBlank { e.flatNo }),
                color = Palette.teal,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            GateStatus.DENIED -> Text(
                stringResource(Res.string.gate_denied_by, e.decidedByName.ifBlank { e.flatNo }),
                color = Palette.red,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            else -> Text(
                stringResource(Res.string.gate_waiting_hint),
                color = Palette.muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (e.status != GateStatus.DENIED) {
                PrimaryButton(
                    stringResource(if (e.status == GateStatus.ALLOWED) Res.string.gate_let_in else Res.string.gate_let_in_anyway),
                    onClick = onIn,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
            }
            SecondaryButton(
                stringResource(Res.string.gate_send_away),
                onClick = onCancel,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun InsideRow(e: GateEntry, busy: Boolean, onOut: () -> Unit) {
    NotizCard(padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(width = 60.dp, height = 40.dp).background(Palette.blueSoft, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(e.flatNo, color = Palette.blueText, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1)
            }
            if (e.photoPath.isNotBlank()) RemotePhotoStrip(listOf(e.photoPath), size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(visitorTitle(e), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(e.vehicleNo, visitorTimes(e)).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Palette.muted,
                    fontSize = 12.sp,
                )
            }
            NotizTextButton(onClick = onOut, enabled = !busy) {
                Text(stringResource(Res.string.gate_mark_out), color = Palette.teal, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ExpectedRow(p: GatePass) {
    NotizCard(padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(gateCategoryIcon(p.category), Palette.purple, Palette.purpleSoft, size = 40.dp, iconSize = 18.dp, radius = 12.dp)
            Column(Modifier.weight(1f)) {
                Text("${p.guestName} · ${p.flatNo}", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(Res.string.gate_expected_by, p.createdByName.ifBlank { p.flatNo }), color = Palette.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LogRow(e: GateEntry, showFlat: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (e.photoPath.isNotBlank()) {
            RemotePhotoStrip(listOf(e.photoPath), size = 36.dp)
        } else {
            Icon(gateCategoryIcon(e.category), contentDescription = null, tint = Palette.muted, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                (if (showFlat) "${e.flatNo} · " else "") + visitorTitle(e),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(visitorTimes(e), color = Palette.muted, fontSize = 12.sp)
        }
        StatusChip(e)
    }
}

// ─────────────────────────────────────────────────────────────
// Desk sheets
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewVisitorSheet(
    flats: List<String>,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onLog: (
        flat: String,
        category: String,
        name: String,
        phone: String,
        company: String,
        vehicle: String,
        letIn: Boolean,
        photo: ByteArray?,
    ) -> Unit,
) {
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var cameraFailed by remember { mutableStateOf(false) }
    val takePhoto = rememberCameraCapture(
        onCaptured = { photo = it; cameraFailed = false },
        onError = { cameraFailed = true },
    )
    var category by rememberSaveable { mutableStateOf(GateCategory.DELIVERY) }
    var flat by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var company by rememberSaveable { mutableStateOf("") }
    var vehicle by rememberSaveable { mutableStateOf("") }
    val flatKey = flat.trim().uppercase()
    val knownFlat = flats.isEmpty() || flatKey in flats
    val suggestions = if (flatKey.isEmpty() || flatKey in flats) emptyList() else flats.filter { it.contains(flatKey) }.take(6)
    val quick = when (category) {
        GateCategory.DELIVERY -> GateRepository.deliveryCompanies
        GateCategory.CAB -> GateRepository.cabCompanies
        GateCategory.SERVICE -> GateRepository.serviceKinds
        else -> emptyList()
    }
    val ready = flatKey.isNotEmpty() && knownFlat && (name.isNotBlank() || company.isNotBlank()) && !busy

    NotizSheet(stringResource(Res.string.gate_new_visitor), onDismissRequest = onDismiss, dismissible = !busy) {
        // Kind of visitor: five big tiles
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GateCategory.all.forEach { c ->
                val selected = category == c
                Column(
                    Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Palette.tealSoft else Color.White)
                        .border(if (selected) 1.5.dp else 1.dp, if (selected) Palette.teal else Palette.line, RoundedCornerShape(14.dp))
                        .clickable { category = c; company = "" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(gateCategoryIcon(c), contentDescription = null, tint = if (selected) Palette.teal else Palette.ink, modifier = Modifier.size(20.dp))
                    Text(
                        gateCategoryLabel(c),
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (selected) Palette.teal else Palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        VisitorPhotoRow(
            photo = photo,
            enabled = !busy,
            failed = cameraFailed,
            onTake = { cameraFailed = false; takePhoto() },
            onRemove = { photo = null },
        )

        NotizTextField(
            value = flat,
            onValueChange = { flat = it.uppercase().take(20) },
            label = { Text(stringResource(Res.string.gate_flat_field)) },
            placeholder = { Text("B-202") },
            isError = !knownFlat && suggestions.isEmpty(),
            supportingText = if (!knownFlat && suggestions.isEmpty()) {
                { Text(stringResource(Res.string.gate_flat_unknown)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { f -> QuickPick(f, selected = false) { flat = f } }
            }
        }

        if (quick.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quick.forEach { q -> QuickPick(q, selected = company == q) { company = if (company == q) "" else q } }
            }
        }
        if (category != GateCategory.GUEST) {
            NotizTextField(
                value = company,
                onValueChange = { company = it.take(40) },
                label = { Text(stringResource(if (category == GateCategory.SERVICE) Res.string.gate_service_field else Res.string.gate_company_field)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        NotizTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            label = { Text(stringResource(if (category == GateCategory.GUEST) Res.string.gate_name_field else Res.string.gate_name_optional)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NotizTextField(
                value = phone,
                onValueChange = { v -> phone = v.filter { it.isDigit() }.take(15) },
                label = { Text(stringResource(Res.string.gate_phone_field)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            NotizTextField(
                value = vehicle,
                onValueChange = { vehicle = it.uppercase().take(20) },
                label = { Text(stringResource(Res.string.gate_vehicle_field)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (error != null) Text(error, color = Palette.red, fontSize = 13.sp)
        PrimaryButton(
            if (flatKey.isEmpty()) stringResource(Res.string.gate_ask_flat_empty) else stringResource(Res.string.gate_ask_flat, flatKey),
            onClick = { onLog(flatKey, category, name, phone, company, vehicle, false, photo) },
            enabled = ready,
            icon = NotizIcons.Bell,
            height = 52.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            stringResource(Res.string.gate_let_in_now),
            onClick = { onLog(flatKey, category, name, phone, company, vehicle, true, photo) },
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(Res.string.gate_let_in_now_hint), color = Palette.muted, fontSize = 12.sp)
    }
}

/** Optional photo of the visitor: a camera tile, or the photo with Retake / Remove. */
@Composable
private fun VisitorPhotoRow(photo: ByteArray?, enabled: Boolean, failed: Boolean, onTake: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val shape = RoundedCornerShape(16.dp)
        if (photo != null) {
            LocalPhoto(photo, Modifier.size(84.dp).clip(shape))
        } else {
            Box(
                Modifier.size(84.dp).clip(shape).background(Palette.chip)
                    .border(1.5.dp, Palette.line, shape)
                    .clickable(enabled = enabled, onClick = onTake),
                contentAlignment = Alignment.Center,
            ) {
                Icon(NotizIcons.Camera, contentDescription = stringResource(Res.string.gate_take_photo), tint = Palette.teal, modifier = Modifier.size(28.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.gate_photo_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                stringResource(if (failed) Res.string.gate_camera_failed else Res.string.gate_photo_hint),
                color = if (failed) Palette.red else Palette.muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Row {
                NotizTextButton(onClick = onTake, enabled = enabled) {
                    Text(stringResource(if (photo == null) Res.string.gate_take_photo else Res.string.gate_retake_photo), color = Palette.teal)
                }
                if (photo != null) {
                    NotizTextButton(onClick = onRemove, enabled = enabled) {
                        Text(stringResource(Res.string.photos_remove), color = Palette.red)
                    }
                }
            }
        }
    }
}

@Composable
private fun EnterCodeSheet(busy: Boolean, error: String?, onDismiss: () -> Unit, onCheck: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    NotizSheet(stringResource(Res.string.gate_enter_code), onDismissRequest = onDismiss, dismissible = !busy) {
        Text(stringResource(Res.string.gate_code_hint), color = Palette.muted, fontSize = 14.sp)
        NotizTextField(
            value = code,
            onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
            placeholder = { Text("000000", fontSize = 28.sp, letterSpacing = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 8.sp, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) Text(error, color = Palette.red, fontSize = 13.sp)
        PrimaryButton(
            stringResource(if (busy) Res.string.gate_checking else Res.string.gate_check_code),
            onClick = { onCheck(code) },
            enabled = code.length == 6 && !busy,
            height = 52.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Resident: my visitors and guest passes
// ─────────────────────────────────────────────────────────────

/**
 * "Gate" screen opened from Home or a notification. Residents answer visitors and invite guests;
 * someone with the gate permission who also lives here (the president) can switch to the desk.
 */
@Composable
fun GateScreen(session: SessionContext, onBack: () -> Unit, onOpenAlerts: () -> Unit = {}) {
    var deskView by rememberSaveable { mutableStateOf(false) }
    val canDesk = session.can(com.notiz.app.data.Perms.GATE)
    Scaffold(
        containerColor = Palette.ground,
        topBar = { DetailTopBar(stringResource(Res.string.gate_resident_title), onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (canDesk) {
                SegmentedTabs(
                    listOf(stringResource(Res.string.gate_tab_my_flat), stringResource(Res.string.gate_tab_desk)),
                    selected = if (deskView) 1 else 0,
                    onSelect = { deskView = it == 1 },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (deskView && canDesk) GateDesk(session, Modifier.weight(1f)) else FlatGate(session, onOpenAlerts, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FlatGate(session: SessionContext, onOpenAlerts: () -> Unit, modifier: Modifier = Modifier) {
    val alertCheck = rememberAlertCheck()
    val container = LocalAppContainer.current
    val vm = viewModel(key = "gate-flat") { GateViewModel(container, session, desk = false) }
    val state by vm.state.collectAsState()
    var inviting by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    OnPushUpdate(PushTypes.GATE) { vm.load() }
    val shareText = passShareText(session)
    fun share(p: GatePass) {
        runCatching { uriHandler.openUri("https://wa.me/?text=${urlEncode(shareText(p))}") }
    }

    Box(modifier) {
        when {
            state.loading -> LoadingBox()
            state.loadError != null && state.entries.isEmpty() -> ErrorBox(state.loadError!!.asString(), onRetry = { vm.load() })
            else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(pullToRefresh = true) }) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        ErrorBanner(state.error?.asString(), onDismiss = vm::dismiss)
                        InfoBanner(state.info?.asString(), onDismiss = vm::dismiss)
                        AlertWarningCard(alertCheck, onOpen = onOpenAlerts)
                    }
                    if (state.waiting.isNotEmpty()) {
                        items(state.waiting, key = { "w" + it.id }) { e ->
                            VisitorRequestCard(e, busy = state.busyId == e.id, onAnswer = { allow -> vm.respond(e, allow) })
                        }
                    }
                    item {
                        InviteGuestCard(onClick = { vm.dismiss(); inviting = true })
                    }
                    if (state.passes.isNotEmpty()) {
                        item { SectionLabel(stringResource(Res.string.gate_my_passes), state.passes.size) }
                        items(state.passes, key = { it.id }) { p ->
                            PassRow(p, busy = state.busyId == p.id, onShare = { share(p) }, onCancel = { vm.cancelPass(p) })
                        }
                    }
                    val history = state.entries.filter { it.status != GateStatus.WAITING }
                    item { SectionLabel(stringResource(Res.string.gate_recent_visitors)) }
                    if (history.isEmpty()) {
                        item { Text(stringResource(Res.string.gate_no_visitors), color = Palette.muted, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp)) }
                    }
                    items(history, key = { it.id }) { e -> LogRow(e, showFlat = false) }
                }
            }
        }
    }

    if (inviting) {
        InviteGuestSheet(
            busy = state.sheetBusy,
            error = state.error?.asString(),
            created = state.newPass,
            onDismiss = { inviting = false; vm.closeNewPass() },
            onCreate = { name, day, cat -> vm.createPass(name, day, cat) },
            onShare = { p -> share(p) },
        )
    }
}

/** WhatsApp text for a guest pass. */
@Composable
private fun passShareText(session: SessionContext): (GatePass) -> String {
    val template = stringResource(Res.string.gate_share_text)
    val months = com.notiz.app.i18n.localizedMonths()
    return { p ->
        template
            .replace("{society}", session.society.name)
            .replace("{flat}", p.flatNo)
            .replace("{code}", p.code)
            .replace("{date}", com.notiz.app.util.formatDate(p.validOn, months))
    }
}

/** A visitor waiting for this flat's answer: big Allow / Deny. Also used on Home. */
@Composable
fun VisitorRequestCard(e: GateEntry, busy: Boolean, onAnswer: (allow: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    // Ask once more before answering: a mis-tap would let a stranger in or turn a guest away
    var confirming by remember(e.id) { mutableStateOf<Boolean?>(null) }
    confirming?.let { allow ->
        val who = visitorTitle(e)
        NotizDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(if (allow) Res.string.gate_confirm_allow_title else Res.string.gate_confirm_deny_title, who)) },
            text = { Text(stringResource(if (allow) Res.string.gate_confirm_allow_body else Res.string.gate_confirm_deny_body)) },
            confirmButton = {
                NotizTextButton(onClick = { confirming = null; onAnswer(allow) }) {
                    Text(
                        stringResource(if (allow) Res.string.gate_allow else Res.string.gate_deny),
                        color = if (allow) Palette.teal else Palette.red,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = { NotizTextButton(onClick = { confirming = null }) { Text(stringResource(Res.string.gate_confirm_back)) } },
        )
    }
    Column(
        modifier.fillMaxWidth().background(Palette.amberSoft, shape).border(1.5.dp, Palette.marigold, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (e.photoPath.isNotBlank()) {
                // Tap to see the visitor full screen
                RemotePhotoStrip(listOf(e.photoPath), size = 72.dp)
            } else {
                IconTile(gateCategoryIcon(e.category), Palette.onMarigold, Palette.marigold, size = 46.dp, iconSize = 22.dp, radius = 14.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.gate_request_title), color = Palette.amberText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(visitorTitle(e), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val extra = listOf(
                    e.vehicleNo,
                    e.visitorPhone,
                    stringResource(Res.string.gate_time_arrived, formatTime(e.createdAt)),
                ).filter { it.isNotBlank() }.joinToString(" · ")
                Text(extra, color = Palette.inkSoft, fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
                    .border(1.dp, Palette.red.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .clickable(enabled = !busy) { confirming = false },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(Res.string.gate_deny), color = Palette.red, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            PrimaryButton(
                stringResource(Res.string.gate_allow),
                onClick = { confirming = true },
                enabled = !busy,
                icon = NotizIcons.Check,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun InviteGuestCard(onClick: () -> Unit) {
    NotizCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(NotizIcons.Key, Palette.purple, Palette.purpleSoft)
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.gate_invite_title), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(stringResource(Res.string.gate_invite_body), color = Palette.muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Icon(NotizIcons.ChevronRight, contentDescription = null, tint = Palette.muted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PassRow(p: GatePass, busy: Boolean, onShare: () -> Unit, onCancel: () -> Unit) {
    NotizCard(padding = PaddingValues(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(p.guestName, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${gateCategoryLabel(p.category)} · ${localDate(p.validOn)}",
                    color = Palette.muted,
                    fontSize = 12.sp,
                )
            }
            Text(
                p.code,
                color = Palette.purple,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                letterSpacing = 3.sp,
                modifier = Modifier.background(Palette.purpleSoft, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Row {
            NotizTextButton(onClick = onShare, enabled = !busy) { Text(stringResource(Res.string.members_share_whatsapp), color = Palette.teal) }
            NotizTextButton(onClick = onCancel, enabled = !busy) { Text(stringResource(Res.string.gate_cancel_pass), color = Palette.red) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InviteGuestSheet(
    busy: Boolean,
    error: String?,
    created: GatePass?,
    onDismiss: () -> Unit,
    onCreate: (name: String, day: String, category: String) -> Unit,
    onShare: (GatePass) -> Unit,
) {
    NotizSheet(stringResource(Res.string.gate_invite_title), onDismissRequest = onDismiss, dismissible = !busy) {
        if (created != null) {
            // The code, big, ready to share
            Column(
                Modifier.fillMaxWidth().background(Palette.purpleSoft, RoundedCornerShape(20.dp)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(Res.string.gate_pass_for, created.guestName), color = Palette.purple, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(created.code, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 8.sp, color = Palette.ink)
                Text(stringResource(Res.string.gate_pass_valid, localDate(created.validOn)), color = Palette.inkSoft, fontSize = 13.sp)
            }
            Text(stringResource(Res.string.gate_pass_how), color = Palette.muted, fontSize = 13.sp, lineHeight = 18.sp)
            PrimaryButton(
                stringResource(Res.string.members_share_whatsapp),
                onClick = { onShare(created) },
                icon = NotizIcons.Share,
                height = 52.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(stringResource(Res.string.action_done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        } else {
            InviteGuestForm(busy, error, onCreate)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InviteGuestForm(busy: Boolean, error: String?, onCreate: (name: String, day: String, category: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(GateCategory.GUEST) }
    val days = remember { GateViewModel.passDays() }
    var day by rememberSaveable { mutableStateOf(days.first()) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        NotizTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            label = { Text(stringResource(Res.string.gate_guest_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(Res.string.gate_who), color = Palette.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(GateCategory.GUEST, GateCategory.SERVICE, GateCategory.DELIVERY, GateCategory.CAB).forEach { c ->
                QuickPick(gateCategoryLabel(c), selected = category == c) { category = c }
            }
        }
        Text(stringResource(Res.string.gate_when), color = Palette.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEachIndexed { i, d ->
                val label = when (i) {
                    0 -> stringResource(Res.string.gate_today)
                    1 -> stringResource(Res.string.gate_tomorrow)
                    else -> localDate(d).substringBeforeLast(' ')
                }
                QuickPick(label, selected = day == d) { day = d }
            }
        }
        if (error != null) Text(error, color = Palette.red, fontSize = 13.sp)
        PrimaryButton(
            stringResource(if (busy) Res.string.action_sending else Res.string.gate_create_pass),
            onClick = { onCreate(name, day, category) },
            enabled = name.isNotBlank() && !busy,
            height = 52.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
