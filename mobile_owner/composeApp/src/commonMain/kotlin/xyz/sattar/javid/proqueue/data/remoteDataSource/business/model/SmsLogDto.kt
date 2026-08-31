package xyz.sattar.javid.proqueue.data.remoteDataSource.business.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of the server's outgoing-SMS ledger
 * (GET business/{id}/sms-logs/). A null [visitor] means the message went to the
 * owner themselves (new-booking notice), not to a client.
 */
@Serializable
data class SmsLogDto(
    @SerialName("id") val id: Long,
    @SerialName("message_text") val messageText: String = "",
    @SerialName("status") val status: String = "",
    @SerialName("error_detail") val errorDetail: String? = null,
    @SerialName("sent_at") val sentAt: String? = null,
    @SerialName("visitor") val visitor: SmsLogVisitorDto? = null
)

@Serializable
data class SmsLogVisitorDto(
    @SerialName("id") val id: Long,
    @SerialName("full_name") val fullName: String = "",
    @SerialName("phone_number") val phoneNumber: String = ""
)

/**
 * Page envelope for the SMS log. Deliberately *not* [PaginatedResponseDto]:
 * this endpoint sends `next`/`previous` as absolute URLs, not page numbers.
 */
@Serializable
data class SmsLogPageDto(
    @SerialName("count") val count: Int = 0,
    @SerialName("next") val next: String? = null,
    @SerialName("previous") val previous: String? = null,
    @SerialName("results") val results: List<SmsLogDto> = emptyList()
)

/** GET business/{id}/sms-logs/summary/ — this month's quota picture. */
@Serializable
data class SmsLogSummaryDto(
    @SerialName("sent_this_month") val sentThisMonth: Int = 0,
    @SerialName("failed_this_month") val failedThisMonth: Int = 0,
    @SerialName("monthly_quota") val monthlyQuota: Int = 0, // -1 = unlimited
    @SerialName("monthly_used") val monthlyUsed: Int = 0,
    @SerialName("wallet_balance") val walletBalance: Int = 0
)

/** Status values the log filter can be narrowed to. */
object SmsLogStatus {
    const val SENT = "SENT"
    const val FAILED = "FAILED"
    /** Never attempted — the owner's SMS quota was already exhausted. */
    const val SKIPPED_QUOTA = "SKIPPED_QUOTA"
}

/**
 * One row of the server's outgoing-push ledger (GET business/{id}/push-logs/).
 * Same shape as [SmsLogDto] on purpose — the two channels share one report
 * screen — but push always has a [visitor] (there is no "owner notification"
 * concept on this channel yet) and carries [title]/[body] instead of a single
 * message_text.
 */
@Serializable
data class PushLogDto(
    @SerialName("id") val id: Long,
    @SerialName("title") val title: String = "",
    @SerialName("body") val body: String = "",
    @SerialName("status") val status: String = "",
    @SerialName("error_detail") val errorDetail: String? = null,
    @SerialName("sent_at") val sentAt: String? = null,
    @SerialName("visitor") val visitor: SmsLogVisitorDto? = null
)

@Serializable
data class PushLogPageDto(
    @SerialName("count") val count: Int = 0,
    @SerialName("next") val next: String? = null,
    @SerialName("previous") val previous: String? = null,
    @SerialName("results") val results: List<PushLogDto> = emptyList()
)

/** GET business/{id}/push-logs/summary/ — no quota/wallet fields; push is free. */
@Serializable
data class PushLogSummaryDto(
    @SerialName("sent_this_month") val sentThisMonth: Int = 0,
    @SerialName("failed_this_month") val failedThisMonth: Int = 0
)
