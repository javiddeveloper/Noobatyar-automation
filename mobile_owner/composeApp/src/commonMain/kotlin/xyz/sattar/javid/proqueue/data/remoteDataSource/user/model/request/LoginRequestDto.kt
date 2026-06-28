package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    val phone: String,
    val password: String,
)