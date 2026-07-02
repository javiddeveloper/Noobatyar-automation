package xyz.sattar.javid.proqueue.domain

import kotlinx.coroutines.flow.Flow
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.RegisterResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SendOTPResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.UserDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.VerifyOTPResponseDto

import xyz.sattar.javid.proqueue.domain.model.VersionInfo

interface UserRepository {
    suspend fun checkVersion(versionName: String): ApiResponse<VersionInfo>
    suspend fun register(phone: String, password: String, name: String): ApiResponse<RegisterResponseDto>
    suspend fun login(phone: String, password: String): ApiResponse<RegisterResponseDto>
    suspend fun logout(): ApiResponse<Unit>
    suspend fun getUserProfile(id: Int): ApiResponse<UserDto>

    // Local Data
    fun getLocalUser(id: Int): Flow<UserDto?>
    fun getCurrentUser(): Flow<UserDto?>


    suspend fun sendOTP(phone: String): ApiResponse<SendOTPResponseDto>
    suspend fun verifyOTP(phone: String, code: String): ApiResponse<VerifyOTPResponseDto>
    suspend fun resetPassword(phone: String, resetToken: String, newPassword: String): ApiResponse<Unit>
}