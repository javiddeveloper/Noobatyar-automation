package xyz.sattar.javid.proqueue.domain.model.appointment

import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor

data class AppointmentWithDetails(
    val appointment: Appointment,
    val visitor: Visitor,
    val business: Business
)