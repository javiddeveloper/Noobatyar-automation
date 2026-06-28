package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request

import kotlinx.serialization.Serializable

@Serializable
data class SendOTPRequestDto(
    val phone: String,
)