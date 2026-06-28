package xyz.sattar.javid.proqueue.domain.model.user

data class VerifyOTP(
    val resetToken: String,
    val expiresIn: Int,
)
