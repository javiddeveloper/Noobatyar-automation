package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.AppointmentRepository
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails

class CheckAppointmentConflictUseCase(private val repository: AppointmentRepository) {
    suspend operator fun invoke(
        businessId: Long,
        startTime: Long,
        duration: Int,
        defaultDuration: Int
    ): List<AppointmentWithDetails> {
        val endTime = startTime + duration * 60 * 1000
        return repository.getConflictingAppointments(businessId, startTime, endTime, defaultDuration)
    }
}
