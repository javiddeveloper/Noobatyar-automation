package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.AppointmentRepository

class UpdateAppointmentUseCase(
    private val appointmentRepository: AppointmentRepository
) {
    suspend operator fun invoke(appointmentId: Long, date: Long, duration: Int?, description: String?): Boolean {
        return appointmentRepository.updateAppointment(appointmentId, date, duration, description)
    }
}
