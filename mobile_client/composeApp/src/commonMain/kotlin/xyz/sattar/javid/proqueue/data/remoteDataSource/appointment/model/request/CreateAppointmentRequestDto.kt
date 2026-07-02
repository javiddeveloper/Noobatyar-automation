package xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateAppointmentRequestDto(
    @SerialName("business_id") val businessId: Long,
    @SerialName("visitor_id") val visitorId: Long,
    @SerialName("appointment_date") val appointmentDate: Long,
    @SerialName("service_duration") val serviceDuration: Int? = null,
    val description: String? = null
)
