package xyz.sattar.javid.proqueue.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaginatedResponseDto<T>(
    @SerialName("count") val count: Int,
    @SerialName("total_pages") val totalPages: Int? = null,
    @SerialName("current_page") val currentPage: Int? = null,
    @SerialName("next") val next: Int? = null,
    @SerialName("previous") val previous: Int? = null,
    @SerialName("results") val results: List<T>
)
