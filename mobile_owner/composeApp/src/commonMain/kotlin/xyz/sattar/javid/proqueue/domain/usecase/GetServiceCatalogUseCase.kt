package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.domain.BusinessRepository

/**
 * Existing service-name chips for a business category (shared across every
 * business in that category, not just the current one).
 */
class GetServiceCatalogUseCase(private val repository: BusinessRepository) {
    suspend operator fun invoke(category: String): List<String> {
        return when (val response = repository.getServiceCatalog(category)) {
            is ApiResponse.Success -> response.data.map { it.name }
            is ApiResponse.Error -> emptyList()
        }
    }
}
