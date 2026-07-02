package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class RegisterRequestDto(
    val phone: String,
    @SerialName("register_token") val registerToken: String,
    val name: String
)