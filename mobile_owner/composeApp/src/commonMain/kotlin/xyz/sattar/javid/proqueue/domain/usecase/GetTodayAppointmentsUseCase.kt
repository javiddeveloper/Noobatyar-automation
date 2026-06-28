package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.AppointmentRepository
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails

class GetTodayAppointmentsUseCase(private val repository: AppointmentRepository) {
    suspend operator fun invoke(businessId: Long, currentTime: Long): List<AppointmentWithDetails> {
        // Get recent appointments (last 50)
        return repository.getTodayAppointments(businessId)
    }
}
