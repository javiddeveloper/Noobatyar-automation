package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.AppointmentRepository
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentOrdering

class SyncAppointmentsUseCase(private val repository: AppointmentRepository) {
    suspend operator fun invoke(
        businessId: Long,
        visitorId: Long? = null,
        status: String? = null,
        date: Long? = null,
        dateFrom: Long? = null,
        dateTo: Long? = null,
        ordering: AppointmentOrdering? = AppointmentOrdering.DATE_DESC,
        page: Int = 1,
        pageSize: Int = 20
    ): Boolean {
        return repository.syncAppointments(
            businessId,
            visitorId,
            status,
            date,
            dateFrom,
            dateTo,
            ordering,
            page,
            pageSize
        )
    }
}
