package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.BusinessRepository

class DeleteBusinessUseCase(private val repository: BusinessRepository) {
    suspend operator fun invoke(businessId: Long) =
        repository.deleteBusiness(businessId)
}
