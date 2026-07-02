package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.BusinessRepository

class FetchBusinessesUseCase(private val repository: BusinessRepository) {
    suspend operator fun invoke(page: Int, pageSize: Int): Boolean {
        return repository.fetchAndCacheBusinesses(page, pageSize)
    }
}
