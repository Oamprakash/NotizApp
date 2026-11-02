package com.notiz.app.ui.home

import com.notiz.app.AppContainer
import com.notiz.app.data.Complaint
import com.notiz.app.data.ComplaintStatus
import com.notiz.app.data.Due
import com.notiz.app.data.DueStatus
import com.notiz.app.data.Notice
import com.notiz.app.data.Poll
import com.notiz.app.i18n.UiText
import com.notiz.app.ui.BaseViewModel
import com.notiz.app.ui.SessionContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The latest bill (same title + due date) and how much of it is collected. */
data class BillProgress(
    val title: String,
    val dueDate: String,
    val collectedPaise: Long,
    val totalPaise: Long,
    val paidFlats: Int,
    val verifyFlats: Int,
    val unpaidFlats: Int,
)

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: UiText? = null,
    val myName: String = "",
    // Resident
    val myDue: Due? = null,
    val myDueDaysLeft: Int? = null,
    val latestNotice: Notice? = null,
    val latestNoticeUnread: Boolean = false,
    val unreadNotices: Int = 0,
    val poll: Poll? = null,
    val myVote: String? = null,
    val pollVotes: Long = 0,
    // Committee
    val bill: BillProgress? = null,
    val toVerify: Int = 0,
    val joinRequests: Int = 0,
    val joinNames: List<String> = emptyList(),
    val openRequests: Int = 0,
    val oldestOpenRequest: Complaint? = null,
    val reportedMessages: Int = 0,
    val votingBusy: Boolean = false,
) {
    /** Bottom-bar badges. */
    val duesBadge: Int get() = myDue?.takeIf { it.status == DueStatus.PENDING }?.let { 1 } ?: 0
}

/**
 * Everything the Home tab and the bottom-bar badges show, loaded together. Each part fails on its
 * own so one slow table never blanks the whole screen.
 */
class HomeViewModel(
    private val container: AppContainer,
    private val session: SessionContext,
) : BaseViewModel() {
    val state = MutableStateFlow(HomeUiState())

    init { load() }

    @OptIn(ExperimentalTime::class)
    fun load(pullToRefresh: Boolean = false) {
        state.update { it.copy(error = null, refreshing = pullToRefresh) }
        launchSafe(onError = { msg -> state.update { it.copy(loading = false, refreshing = false, error = msg) } }) {
            val sid = session.societyId
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            coroutineScope {
                val profile = async { soft { container.auth.myProfile() } }
                val dues = async { soft { container.dues.dues(sid) } ?: emptyList() }
                val notices = async { soft { container.notices.notices(sid) } ?: emptyList() }
                val reads = async { soft { container.notices.myReadNoticeIds() } ?: emptySet() }
                val polls = async { soft { container.polls.polls(sid) } ?: emptyList() }
                val votes = async { soft { container.polls.myFlatVotes() } ?: emptyMap() }
                val results = async { soft { container.polls.results(sid) } ?: emptyMap() }
                val members = async { if (session.isAdmin) soft { container.societies.members(sid) } ?: emptyList() else emptyList() }
                val complaints = async { if (session.isAdmin) soft { container.complaints.complaints(sid) } ?: emptyList() else emptyList() }
                val reports = async {
                    if (session.isAdmin) soft { container.chat.openReportCount(sid, today.minus(7, DateTimeUnit.DAY).toString()) } ?: 0 else 0
                }

                val allDues = dues.await()
                val myOpen = allDues
                    .filter { it.flatNo == session.flatNo && (it.status == DueStatus.PENDING || it.status == DueStatus.SUBMITTED) }
                    .minByOrNull { it.dueDate }
                val readIds = reads.await()
                val noticeList = notices.await()
                val latest = noticeList.firstOrNull()
                val myVotes = votes.await()
                val pollList = polls.await()
                // The newest poll this flat hasn't answered, else the newest poll
                val poll = pollList.firstOrNull { it.id !in myVotes } ?: pollList.firstOrNull()
                val pollResults = results.await()
                val pending = members.await().filter { it.isPending }
                val open = complaints.await().filter { it.status != ComplaintStatus.RESOLVED }

                state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        myName = profile.await()?.fullName.orEmpty(),
                        myDue = myOpen,
                        myDueDaysLeft = myOpen?.let { d -> runCatching { today.daysUntil(LocalDate.parse(d.dueDate)) }.getOrNull() },
                        latestNotice = latest,
                        latestNoticeUnread = latest != null && latest.id !in readIds,
                        unreadNotices = noticeList.count { n -> n.id !in readIds },
                        poll = poll,
                        myVote = poll?.let { p -> myVotes[p.id] },
                        pollVotes = poll?.options?.sumOf { o -> pollResults[o.id] ?: 0L } ?: 0L,
                        bill = if (session.isAdmin) latestBill(allDues) else null,
                        toVerify = allDues.count { d -> d.status == DueStatus.SUBMITTED },
                        joinRequests = pending.size,
                        joinNames = pending.map { m -> "${m.flatNo} ${m.profiles?.fullName.orEmpty()}".trim() },
                        openRequests = open.size,
                        oldestOpenRequest = open.minByOrNull { c -> c.createdAt },
                        reportedMessages = reports.await(),
                    )
                }
            }
        }
    }

    /** Votes from the Home poll card. */
    fun vote(optionId: String) {
        val poll = state.value.poll ?: return
        state.update { it.copy(votingBusy = true, myVote = optionId) }
        launchSafe(onError = { msg -> state.update { it.copy(votingBusy = false, error = msg) } }) {
            container.polls.vote(poll.id, optionId)
            state.update { it.copy(votingBusy = false) }
            load()
        }
    }

    fun dismissError() = state.update { it.copy(error = null) }

    private fun latestBill(dues: List<Due>): BillProgress? {
        val group = dues.groupBy { it.title to it.dueDate }.maxByOrNull { it.key.second } ?: return null
        val list = group.value
        return BillProgress(
            title = group.key.first,
            dueDate = group.key.second,
            collectedPaise = list.filter { it.status == DueStatus.PAID }.sumOf { it.amountPaise },
            totalPaise = list.sumOf { it.amountPaise },
            paidFlats = list.count { it.status == DueStatus.PAID },
            verifyFlats = list.count { it.status == DueStatus.SUBMITTED },
            unpaidFlats = list.count { it.status == DueStatus.PENDING },
        )
    }

    private suspend fun <T> soft(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }
}
