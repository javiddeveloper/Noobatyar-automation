package xyz.sattar.javid.proqueue.data.remoteDataSource.business.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateBusinessRequestDto(
    @SerialName("title") val title: String,
    @SerialName("category") val category: String,
    @SerialName("phone") val phone: String,
    @SerialName("address") val address: String,
    @SerialName("default_service_duration") val defaultServiceDuration: Int,
    @SerialName("work_start_hour") val workStartHour: Int,
    @SerialName("work_end_hour") val workEndHour: Int,
    @SerialName("notification_enabled") val notificationEnabled: Boolean=true,
    @SerialName("notification_types") val notificationTypes: String ="SMS,WHATSAPP,TELEGRAM",
    @SerialName("notification_minutes_before") val notificationMinutesBefore: Int,
    @SerialName("allow_anonymous_view") val allowAnonymousView: Boolean = false,
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
    @SerialName("bio") val bio: String? = null
)
