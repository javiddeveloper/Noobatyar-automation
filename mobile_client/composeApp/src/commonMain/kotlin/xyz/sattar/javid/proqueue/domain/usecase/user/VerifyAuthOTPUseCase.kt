package xyz.sattar.javid.proqueue.domain.usecase.user

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.TokenManager
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.mapper.toDomain
import xyz.sattar.javid.proqueue.domain.UserRepository
import xyz.sattar.javid.proqueue.domain.model.user.VerifyAuthOTP

class VerifyAuthOTPUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(
        phone: String,
        code: String,
    ): ApiResponse<VerifyAuthOTP> {
        return when (val response = userRepository.verifyAuthOTP(phone, code)) {
            is ApiResponse.Success -> {
                if (response.data.isRegistered && response.data.tokens != null) {
                    TokenManager.saveTokens(
                        accessToken = response.data.tokens.access,
                        refreshToken = response.data.tokens.refresh
                    )
                }
                ApiResponse.Success(response.data.toDomain())
            }
            is ApiResponse.Error -> ApiResponse.Error(response.message, response.code)
        }
    }
}
