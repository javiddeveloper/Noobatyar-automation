package xyz.sattar.javid.proqueue.domain.usecase.user

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.TokenManager
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.mapper.toDomain
import xyz.sattar.javid.proqueue.domain.UserRepository
import xyz.sattar.javid.proqueue.domain.model.user.User

class RegisterUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(
        phone: String,
        password: String,
        name: String
    ): ApiResponse<User> {
        return when (val response = userRepository.register(phone, password, name)) {
            is ApiResponse.Success -> {
                TokenManager.saveTokens(
                    accessToken = response.data.tokens.access,
                    refreshToken = response.data.tokens.refresh
                )
                ApiResponse.Success(response.data.user.toDomain())
            }
            is ApiResponse.Error -> ApiResponse.Error(response.message, response.code)
        }
    }
}
