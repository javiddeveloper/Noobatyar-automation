package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.AppointmentRepository

class RemoveAppointmentUseCase(private val repository: AppointmentRepository) {
    suspend operator fun invoke(appointmentId: Long): Boolean =
        repository.removeAppointment(appointmentId)
}