package xyz.sattar.javid.proqueue.data.localDataSource.appointment

import xyz.sattar.javid.proqueue.data.localDataSource.business.toDomain
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.toDomain
import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails

fun AppointmentEntity.toDomain() = Appointment(
    id = id,
    businessId = businessId,
    visitorId = visitorId,
    appointmentDate = appointmentDate,
    serviceDuration = serviceDuration,
    status = status,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Appointment.toEntity() = AppointmentEntity(
    id = id,
    businessId = businessId,
    visitorId = visitorId,
    appointmentDate = appointmentDate,
    serviceDuration = serviceDuration,
    status = status,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun AppointmentWithDetailsEntity.toDomain() = AppointmentWithDetails(
    appointment = appointment.toDomain(),
    visitor = visitor.toDomain(),
    business = business.toDomain()
)
