package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(
    val phone: String,
    val password: String,
    val name: String
)