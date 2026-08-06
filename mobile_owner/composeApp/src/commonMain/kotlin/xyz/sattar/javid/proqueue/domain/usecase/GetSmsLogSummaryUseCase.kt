package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.BusinessRepository

class GetSmsLogSummaryUseCase(private val repository: BusinessRepository) {
    suspend operator fun invoke(businessId: Long) = repository.getSmsLogSummary(businessId)
}
