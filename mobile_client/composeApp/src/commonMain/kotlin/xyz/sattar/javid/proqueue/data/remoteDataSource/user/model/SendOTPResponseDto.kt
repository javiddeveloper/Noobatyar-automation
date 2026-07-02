package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendOTPResponseDto(
    @SerialName("expires_in") val expiresIn: Int,
)