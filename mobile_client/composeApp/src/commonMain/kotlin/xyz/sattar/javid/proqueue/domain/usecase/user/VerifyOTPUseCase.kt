package xyz.sattar.javid.proqueue.domain.usecase.user

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.mapper.toDomain
import xyz.sattar.javid.proqueue.domain.UserRepository
import xyz.sattar.javid.proqueue.domain.model.user.VerifyOTP

class VerifyOTPUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(
        phone: String,
        code: String,
    ): ApiResponse<VerifyOTP> {
        return when (val response = userRepository.verifyOTP(phone, code)) {
            is ApiResponse.Success -> ApiResponse.Success(response.data.toDomain())
            is ApiResponse.Error -> ApiResponse.Error(response.message, response.code)
        }
    }
}
