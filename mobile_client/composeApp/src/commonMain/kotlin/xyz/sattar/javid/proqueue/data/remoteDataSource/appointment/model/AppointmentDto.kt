package xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.model.VisitorDto

@Serializable
data class AppointmentDto(
    val id: Long,
    val visitor: VisitorDto,
    @SerialName("appointment_date") val appointmentDate: Long,
    @SerialName("service_duration") val serviceDuration: Int?,
    val status: String,
    val description: String?,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)
