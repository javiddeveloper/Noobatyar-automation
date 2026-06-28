package xyz.sattar.javid.proqueue.data.remoteDataSource.business.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BusinessDto(
    @SerialName("id") val id: Long,
    @SerialName("title") val title: String,
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
    @SerialName("updated_at") val updatedAt: String
)
