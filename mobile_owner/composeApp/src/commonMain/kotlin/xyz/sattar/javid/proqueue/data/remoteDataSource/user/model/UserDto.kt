package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Int,
    val phone: String,
    val name: String,
    @SerialName("user_type") val userType: String,
    @SerialName("is_employee") val isEmployee: Boolean,
    @SerialName("joined_at") val joinedAt: String
)

@Serializable
data class TokensDto(
    val access: String,
    val refresh: String
)

@Serializable
data class RegisterResponseDto(
    val user: UserDto,
    val tokens: TokensDto
)
