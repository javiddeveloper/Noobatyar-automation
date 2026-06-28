package xyz.sattar.javid.proqueue.domain.usecase

import kotlinx.coroutines.flow.Flow
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.model.business.Business

class ObserveBusinessesUseCase(private val repository: BusinessRepository) {
    operator fun invoke(): Flow<List<Business>> = repository.loadAllBusinessFlow()
}
