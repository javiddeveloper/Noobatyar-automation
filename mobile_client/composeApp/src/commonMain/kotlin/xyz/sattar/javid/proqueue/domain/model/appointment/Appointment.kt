package xyz.sattar.javid.proqueue.domain.model.appointment

data class Appointment(
    val id: Long,
    val businessId: Long,
    val visitorId: Long,
    val appointmentDate: Long,
    val serviceDuration: Int?,
    val status: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
