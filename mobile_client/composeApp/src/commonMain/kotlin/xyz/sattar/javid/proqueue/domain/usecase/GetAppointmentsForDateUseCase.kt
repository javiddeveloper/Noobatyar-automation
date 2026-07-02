package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.AppointmentRepository
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails

class GetAppointmentsForDateUseCase(private val repository: AppointmentRepository) {
    suspend operator fun invoke(businessId: Long, date: Long): List<AppointmentWithDetails> {
        return repository.getAppointmentsForDate(businessId, date)
    }
}
