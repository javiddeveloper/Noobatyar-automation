package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaymentRequestDto(
    @SerialName("plan_id") val planId: String
)
