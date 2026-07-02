package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.AppointmentRepository
import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment

class GetAppointmentByIdUseCase(
    private val appointmentRepository: AppointmentRepository
) {
    suspend operator fun invoke(appointmentId: Long): Appointment? {
        return appointmentRepository.getAppointmentById(appointmentId)
    }
}
