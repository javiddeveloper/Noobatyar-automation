package xyz.sattar.javid.proqueue.data.remoteDataSource.business.model

import kotlinx.serialization.Serializable

/**
 * One pickable "service received" chip, shared across every business in
 * [category] (GET/POST business/service-catalog/) — not scoped to a single
 * business. See business.models.ServiceCatalogItem on the backend for why.
 */
@Serializable
data class ServiceCatalogItemDto(
    val id: Long = 0,
    val category: String = "",
    val name: String = ""
)

@Serializable
data class AddServiceCatalogItemRequestDto(
    val category: String,
    val name: String
)
