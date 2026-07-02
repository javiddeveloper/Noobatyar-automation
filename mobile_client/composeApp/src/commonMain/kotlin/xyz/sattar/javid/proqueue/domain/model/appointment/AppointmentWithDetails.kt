package xyz.sattar.javid.proqueue.domain.model.appointment

import xyz.sattar.javid.proqueue.domain.model.business.Business


data class AppointmentWithDetails(
    val appointment: Appointment,
    val business: Business
)