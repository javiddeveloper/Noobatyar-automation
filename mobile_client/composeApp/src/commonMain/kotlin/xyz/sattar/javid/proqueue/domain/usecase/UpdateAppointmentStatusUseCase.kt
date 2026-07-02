package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.AppointmentRepository

class UpdateAppointmentStatusUseCase(
    private val appointmentRepository: AppointmentRepository
) {
    suspend operator fun invoke(appointmentId: Long, status: String): Boolean {
        return appointmentRepository.updateAppointmentStatus(appointmentId, status)
    }
}
