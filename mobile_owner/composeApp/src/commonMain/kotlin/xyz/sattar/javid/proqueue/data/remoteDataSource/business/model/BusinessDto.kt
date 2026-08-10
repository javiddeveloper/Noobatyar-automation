package xyz.sattar.javid.proqueue.data.remoteDataSource.business.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BusinessDto(
    @SerialName("id") val id: Long,
    @SerialName("title") val title: String,
    @SerialName("category") val category: String? = null,
    @SerialName("unique_code") val uniqueCode: String? = null,
    @SerialName("phone") val phone: String,
    @SerialName("address") val address: String,
    @SerialName("logo") val logo: String? = null,
    @SerialName("default_service_duration") val defaultServiceDuration: Int,
    @SerialName("work_start_hour") val workStartHour: Int,
    @SerialName("work_end_hour") val workEndHour: Int,
    @SerialName("notification_enabled") val notificationEnabled: Boolean,
    @SerialName("notification_types") val notificationTypes: String,
    @SerialName("notification_minutes_before") val notificationMinutesBefore: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("allow_anonymous_view") val allowAnonymousView: Boolean = false,
    // Defaults to true to match the server: an older backend that doesn't send
    // this field yet still notifies the owner, so assuming false here would
    // silently show the switch as off while SMS kept arriving.
    @SerialName("notify_owner_by_sms") val notifyOwnerBySms: Boolean = true,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("accepted_payment_methods") val acceptedPaymentMethods: List<String>? = null,
    @SerialName("max_appointments_per_hour") val maxAppointmentsPerHour: Int? = null,
    @SerialName("deposit_mode") val depositMode: String? = null,
    @SerialName("deposit_amount") val depositAmount: Int? = null,
    @SerialName("merchant_id") val merchantId: String? = null,
    @SerialName("payment_link") val paymentLink: String? = null,
    @SerialName("card_number") val cardNumber: String? = null,
    @SerialName("card_owner_name") val cardOwnerName: String? = null,
    @SerialName("bio") val bio: String? = null,
    // Content moderation (read-only server side). All four are nullable with a
    // null default so the app keeps working against a backend that predates the
    // moderation layer — a missing moderation_status simply hides the badges
    // instead of claiming a business is pending.
    @SerialName("moderation_status") val moderationStatus: String? = null,
    @SerialName("moderation_status_display") val moderationStatusDisplay: String? = null,
    @SerialName("moderation_note") val moderationNote: String? = null,
    @SerialName("moderation_submitted_at") val moderationSubmittedAt: String? = null,
    // Billing lock, independent of moderation — the owner's plan lapsed rather
    // than a content review. False default: an older backend that doesn't send
    // this yet should never be treated as locked.
    @SerialName("is_locked") val isLocked: Boolean = false,
    // Emergency notice shown on the public booking page. Public clients only see
    // the message while the switch is on, so both fields travel together.
    @SerialName("notice_enabled") val noticeEnabled: Boolean = false,
    @SerialName("notice_message") val noticeMessage: String? = null,
    // "MANUAL" (owner texts from their own SIM) or "PANEL" (server sends).
    @SerialName("reminder_delivery") val reminderDelivery: String? = null,
    // The business's own service menu and whether clients may go off it.
    // Defaulted so a backend that predates the field still deserializes.
    @SerialName("services") val services: List<String> = emptyList(),
    @SerialName("allow_client_add_service") val allowClientAddService: Boolean = false
)
