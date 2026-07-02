package xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessDto

@Serializable
data class ClientAppointmentDto(
    @SerialName("id") val id: Long,
    @SerialName("business") val business: BusinessDto,
    @SerialName("appointment_date") val appointmentDate: Long?,
    @SerialName("status") val status: String,
    @SerialName("queue_position") val queuePosition: Int,
    @SerialName("estimated_turn_time") val estimatedTurnTime: Long?
)
