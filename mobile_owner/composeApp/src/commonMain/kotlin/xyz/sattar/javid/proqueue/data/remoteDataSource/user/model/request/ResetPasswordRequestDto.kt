package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResetPasswordRequestDto(
    val phone: String,
    @SerialName("reset_token") val resetToken: String,
    @SerialName("new_password") val newPassword: String,
)