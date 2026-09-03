package xyz.sattar.javid.proqueue.data.remoteDataSource.business.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The category vocabulary as served by `GET business/categories/`
 * (backend `business/views.py::BusinessCategoriesView`).
 *
 * Fetched rather than read from [BusinessCategory][xyz.sattar.javid.proqueue.domain.model.business.BusinessCategory]
 * so a category added on the server reaches installed apps without a release.
 * The enum stays as the offline fallback — see `BusinessRepositoryImpl`.
 */
@Serializable
data class BusinessCategoriesDto(
    @SerialName("groups") val groups: List<BusinessCategoryGroupDto> = emptyList(),
)

@Serializable
data class BusinessCategoryGroupDto(
    @SerialName("key") val key: String = "",
    @SerialName("label") val label: String = "",
    @SerialName("categories") val categories: List<BusinessCategoryOptionDto> = emptyList(),
)

@Serializable
data class BusinessCategoryOptionDto(
    @SerialName("value") val value: String = "",
    @SerialName("label") val label: String = "",
)
