package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionDto(
    val id: Int? = null,
    val status: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("is_valid") val isValid: Boolean? = null,
    @SerialName("is_vip") val isVip: Boolean? = null,
    val plan: PlanDto? = null
)

@Serializable
data class PlanDto(
    val id: Int,
    val name: String,
    val price: Long,
    @SerialName("discount_price") val discountPrice: Long? = null,
    @SerialName("price_display") val priceDisplay: String,
    @SerialName("duration_display") val durationDisplay: String,
    val description: List<String> = emptyList(),
    @SerialName("is_vip") val isVip: Boolean
)
