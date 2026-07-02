package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VerifyOTPAuthResponseDto(
    @SerialName("is_registered") val isRegistered: Boolean,
    @SerialName("register_token") val registerToken: String? = null,
    @SerialName("expires_in") val expiresIn: Int? = null,
    val user: UserDto? = null,
    val tokens: TokensDto? = null
)
