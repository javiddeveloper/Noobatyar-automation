package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VerifyOTPResponseDto(
    @SerialName("reset_token") val resetToken: String,
    @SerialName("expires_in") val expiresIn: Int,
)
