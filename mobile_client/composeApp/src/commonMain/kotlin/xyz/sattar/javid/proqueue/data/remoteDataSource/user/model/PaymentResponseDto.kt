package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaymentResponseDto(
    @SerialName("payment_url") val paymentUrl: String,
    @SerialName("track_id") val trackId: Long
)
