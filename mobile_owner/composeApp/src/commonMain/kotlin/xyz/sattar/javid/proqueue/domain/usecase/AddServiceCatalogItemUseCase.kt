package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.core.network.ApiException
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.domain.BusinessRepository

/**
 * Adds a new service-name chip to a category's shared catalog. Not scoped to
 * the current business: the moment this returns, every business in
 * [category] can pick the new chip too. Idempotent server-side
 * (get_or_create), so calling this with a name that already exists is safe
 * and simply returns the existing item.
 */
class AddServiceCatalogItemUseCase(private val repository: BusinessRepository) {
    suspend operator fun invoke(category: String, name: String): String {
        return when (val response = repository.addServiceCatalogItem(category, name)) {
            is ApiResponse.Success -> response.data.name
            is ApiResponse.Error -> throw ApiException(response.message, response.code)
        }
    }
}
