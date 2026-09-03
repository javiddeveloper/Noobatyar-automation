package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.AppointmentRepository

class GetDailyCountsUseCase(private val repository: AppointmentRepository) {
    suspend operator fun invoke(businessId: Long, days: Int = 7, daysAhead: Int = 0) =
        repository.getDailyCounts(businessId, days, daysAhead)
}
