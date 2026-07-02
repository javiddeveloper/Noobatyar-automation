package xyz.sattar.javid.proqueue.data.repository.user

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.sattar.javid.proqueue.core.network.ApiResponse

import xyz.sattar.javid.proqueue.data.localDataSource.user.UserDao
import xyz.sattar.javid.proqueue.data.localDataSource.user.UserEntity
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.CheckVersionRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.RegisterRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.RegisterResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.UserApiService
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.LoginRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SendOTPResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.UserDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.VerifyOTPResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.ResetPasswordRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.SendOTPRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.VerifyOTPRequestDto
import xyz.sattar.javid.proqueue.domain.UserRepository
import xyz.sattar.javid.proqueue.domain.model.VersionInfo

class UserRepositoryImpl(
    private val userApiService: UserApiService,
    private val userDao: UserDao
) : UserRepository {

    override suspend fun checkVersion(versionName: String): ApiResponse<VersionInfo> {
        val versionInt = versionName.replace(".", "").toIntOrNull() ?: 0
        return userApiService.checkVersion(CheckVersionRequestDto(versionCode = versionInt))
    }

    override suspend fun register(
        phone: String,
        registerToken: String,
        name: String
    ): ApiResponse<RegisterResponseDto> {
        val response = userApiService.register(
            RegisterRequestDto(
                phone = phone,
                registerToken = registerToken,
                name = name
            )
        )
        if (response is ApiResponse.Success) {
            saveUserToDb(response.data.user)
        }
        return response
    }

    override suspend fun login(
        phone: String,
        password: String
    ): ApiResponse<RegisterResponseDto> {
        val response = userApiService.login(
            LoginRequestDto(
                phone = phone,
                password = password,
            )
        )
        if (response is ApiResponse.Success) {
            saveUserToDb(response.data.user)
        }
        return response
    }

    override suspend fun logout(): ApiResponse<Unit> {
        userDao.clearUser()
        return userApiService.logout()
    }

    override suspend fun getUserProfile(id: Int): ApiResponse<UserDto> {
        return userApiService.getUserProfile(id)
    }



    override fun getLocalUser(id: Int): Flow<UserDto?> {
        return userDao.getUserById(id).map { entity ->
            entity?.let {
                UserDto(
                    id = it.id,
                    phone = it.phone,
                    name = it.name,
                    userType = it.userType,
                    isEmployee = it.isEmployee,
                    joinedAt = it.joinedAt
                )
            }
        }
    }

    override fun getCurrentUser(): Flow<UserDto?> {
        return userDao.getCurrentUser().map { entity ->
            entity?.let {
                UserDto(
                    id = it.id,
                    phone = it.phone,
                    name = it.name,
                    userType = it.userType,
                    isEmployee = it.isEmployee,
                    joinedAt = it.joinedAt
                )
            }
        }
    }



    private suspend fun saveUserToDb(user: UserDto) {
        userDao.insertUser(
            UserEntity(
                id = user.id,
                phone = user.phone,
                name = user.name,
                userType = user.userType,
                isEmployee = user.isEmployee,
                joinedAt = user.joinedAt
            )
        )
    }

    override suspend fun sendOTP(phone: String): ApiResponse<SendOTPResponseDto> {
        return userApiService.sendOTP(SendOTPRequestDto(phone = phone))
    }

    override suspend fun verifyOTP(
        phone: String,
        code: String
    ): ApiResponse<VerifyOTPResponseDto> {
        return userApiService.verifyOTP(VerifyOTPRequestDto(phone = phone, code = code))
    }

    override suspend fun sendAuthOTP(phone: String): ApiResponse<SendOTPResponseDto> {
        return userApiService.sendAuthOTP(SendOTPRequestDto(phone = phone))
    }

    override suspend fun verifyAuthOTP(
        phone: String,
        code: String
    ): ApiResponse<xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.VerifyOTPAuthResponseDto> {
        val response = userApiService.verifyAuthOTP(VerifyOTPRequestDto(phone = phone, code = code))
        if (response is ApiResponse.Success && response.data.isRegistered && response.data.user != null) {
            saveUserToDb(response.data.user)
        }
        return response
    }

    override suspend fun resetPassword(
        phone: String,
        resetToken: String,
        newPassword: String
    ): ApiResponse<Unit> {
        return userApiService.resetPassword(
            ResetPasswordRequestDto(
                phone = phone,
                resetToken = resetToken,
                newPassword = newPassword,
            )
        )
    }
}
