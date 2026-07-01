package xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateAppointmentRequestDto(
    val status: String? = null,
    @SerialName("appointment_date") val appointmentDate: Long? = null,
    @SerialName("service_duration") val serviceDuration: Int? = null,
    val description: String? = null
)
