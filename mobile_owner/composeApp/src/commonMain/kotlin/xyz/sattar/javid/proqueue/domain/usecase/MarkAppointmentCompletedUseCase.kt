package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.AppointmentRepository

class MarkAppointmentCompletedUseCase(private val repository: AppointmentRepository) {
    suspend operator fun invoke(appointmentId: Long): Boolean =
        repository.updateAppointmentStatus(appointmentId, "COMPLETED")
}
