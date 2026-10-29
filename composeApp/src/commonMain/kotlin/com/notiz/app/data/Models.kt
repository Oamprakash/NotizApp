package com.notiz.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Timestamps are kept as ISO-8601 strings from Postgres; see util/Format.kt for display.

@Serializable
data class Profile(
    val id: String,
    @SerialName("full_name") val fullName: String,
)

@Serializable
data class Society(
    val id: String,
    val name: String,
    val city: String = "",
    @SerialName("upi_vpa") val upiVpa: String = "",
    @SerialName("upi_payee_name") val upiPayeeName: String = "",
    @SerialName("invite_code") val inviteCode: String = "",
    @SerialName("online_payments_enabled") val onlinePaymentsEnabled: Boolean = false,
)

@Serializable
data class Membership(
    val id: String,
    @SerialName("society_id") val societyId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("flat_no") val flatNo: String,
    val role: String,
    val status: String,
    // Present when selected with an embed: memberships?select=*,societies(*)
    val societies: Society? = null,
    // Present when selected with an embed: memberships?select=*,profiles(id,full_name)
    val profiles: Profile? = null,
) {
    val isAdmin get() = role == "admin"
    val isApproved get() = status == "approved"
    val isPending get() = status == "pending"
}

@Serializable
data class Notice(
    val id: String,
    @SerialName("society_id") val societyId: String,
    val title: String,
    val body: String = "",
    val pinned: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class NewNotice(
    @SerialName("society_id") val societyId: String,
    @SerialName("author_id") val authorId: String,
    val title: String,
    val body: String,
    val pinned: Boolean,
)

@Serializable
data class NoticeRead(@SerialName("notice_id") val noticeId: String)

@Serializable
data class NoticeReadCount(
    @SerialName("notice_id") val noticeId: String,
    val reads: Long,
    val members: Long,
)

@Serializable
data class Due(
    val id: String,
    @SerialName("society_id") val societyId: String,
    @SerialName("flat_no") val flatNo: String,
    val title: String,
    @SerialName("amount_paise") val amountPaise: Long,
    @SerialName("due_date") val dueDate: String,
    val status: String,
    val utr: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("receipt_no") val receiptNo: Int? = null,
    /** "upi" (reported by resident), "cash", or "online" (Razorpay) — set when paid */
    @SerialName("payment_mode") val paymentMode: String? = null,
    @SerialName("payment_link_id") val paymentLinkId: String? = null,
    /** Who reported the payment — embedded as payer:profiles!dues_submitted_by_fkey(full_name) */
    val payer: PayerName? = null,
)

@Serializable
data class PayerName(@SerialName("full_name") val fullName: String)

object DueStatus {
    const val PENDING = "pending"
    const val SUBMITTED = "submitted"
    const val PAID = "paid"
}

@Serializable
data class Complaint(
    val id: String,
    @SerialName("society_id") val societyId: String,
    @SerialName("flat_no") val flatNo: String = "",
    val category: String,
    val description: String,
    val status: String,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class NewComplaint(
    @SerialName("society_id") val societyId: String,
    @SerialName("raised_by") val raisedBy: String,
    val category: String,
    val description: String,
)

object ComplaintStatus {
    const val OPEN = "open"
    const val IN_PROGRESS = "in_progress"
    const val RESOLVED = "resolved"
    val all = listOf(OPEN, IN_PROGRESS, RESOLVED)
}

@Serializable
data class Poll(
    val id: String,
    @SerialName("society_id") val societyId: String,
    val question: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("poll_options") val options: List<PollOption> = emptyList(),
)

@Serializable
data class PollOption(
    val id: String,
    @SerialName("poll_id") val pollId: String,
    val label: String,
    val position: Int = 0,
)

@Serializable
data class PollVote(
    @SerialName("poll_id") val pollId: String,
    @SerialName("option_id") val optionId: String,
)

@Serializable
data class PollResult(
    @SerialName("option_id") val optionId: String,
    val votes: Long,
)

@Serializable
data class Message(
    val id: String,
    @SerialName("society_id") val societyId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_name") val senderName: String = "",
    @SerialName("flat_no") val flatNo: String = "",
    val body: String,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class NewMessage(
    @SerialName("society_id") val societyId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
)

object PaymentMode {
    const val UPI = "upi"
    const val CASH = "cash"
    const val ONLINE = "online"
}

/** Admin view of the society's Razorpay setup (the key secret is never sent to the app). */
@Serializable
data class GatewayStatus(
    val configured: Boolean = false,
    @SerialName("key_id") val keyId: String? = null,
    @SerialName("webhook_secret") val webhookSecret: String? = null,
    @SerialName("online_enabled") val onlineEnabled: Boolean = false,
    @SerialName("test_mode") val testMode: Boolean = false,
)

/** Reply from the create-payment-link Edge Function. */
@Serializable
data class PayLinkResult(val status: String, val url: String? = null)

@Serializable
data class OnlineFlag(@SerialName("online_payments_enabled") val onlinePaymentsEnabled: Boolean = false)
