package xyz.sattar.javid.proqueue.domain.model.user

import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.TokensDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.UserDto

data class VerifyAuthOTP(
    val isRegistered: Boolean,
    val registerToken: String? = null,
    val expiresIn: Int? = null,
    val user: UserDto? = null,
    val tokens: TokensDto? = null
)
