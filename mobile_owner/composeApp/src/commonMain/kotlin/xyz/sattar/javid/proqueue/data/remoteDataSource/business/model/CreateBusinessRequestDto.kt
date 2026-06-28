package xyz.sattar.javid.proqueue.data.remoteDataSource.business.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateBusinessRequestDto(
    @SerialName("title") val title: String,
    @SerialName("phone") val phone: String,
    @SerialName("address") val address: String,
    @SerialName("default_service_duration") val defaultServiceDuration: Int,
    @SerialName("work_start_hour") val workStartHour: Int,
    @SerialName("work_end_hour") val workEndHour: Int,
    @SerialName("notification_enabled") val notificationEnabled: Boolean=true,
    @SerialName("notification_types") val notificationTypes: String ="SMS,WHATSAPP,TELEGRAM",
    @SerialName("notification_minutes_before") val notificationMinutesBefore: Int
)
