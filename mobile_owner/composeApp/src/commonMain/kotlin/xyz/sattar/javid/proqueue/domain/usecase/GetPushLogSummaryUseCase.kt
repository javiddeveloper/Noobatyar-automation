package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.BusinessRepository

class GetPushLogSummaryUseCase(private val repository: BusinessRepository) {
    suspend operator fun invoke(businessId: Long) = repository.getPushLogSummary(businessId)
}
