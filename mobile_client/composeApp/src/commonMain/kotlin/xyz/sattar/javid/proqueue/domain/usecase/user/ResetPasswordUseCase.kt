package xyz.sattar.javid.proqueue.domain.usecase.user

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.domain.UserRepository

class ResetPasswordUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(
        phone: String,
        resetToken: String,
        newPassword: String
    ): ApiResponse<Unit> {
        return when (val response = userRepository.resetPassword(phone, resetToken, newPassword)) {
            is ApiResponse.Success -> ApiResponse.Success(Unit)
            is ApiResponse.Error -> ApiResponse.Error(response.message, response.code)
        }
    }
}
