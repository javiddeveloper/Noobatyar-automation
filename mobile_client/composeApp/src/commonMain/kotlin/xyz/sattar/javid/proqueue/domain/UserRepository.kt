package xyz.sattar.javid.proqueue.domain

import kotlinx.coroutines.flow.Flow
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.RegisterResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SendOTPResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.UserDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.VerifyOTPResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SubscriptionDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PlanDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PaymentResponseDto
import xyz.sattar.javid.proqueue.domain.model.VersionInfo

interface UserRepository {
    suspend fun checkVersion(versionName: String): ApiResponse<VersionInfo>
    suspend fun register(phone: String, password: String, name: String): ApiResponse<RegisterResponseDto>
    suspend fun login(phone: String, password: String): ApiResponse<RegisterResponseDto>
    suspend fun logout(): ApiResponse<Unit>
    suspend fun getUserProfile(id: Int): ApiResponse<UserDto>
    suspend fun getMySubscription(): ApiResponse<SubscriptionDto>
    suspend fun getPlans(): ApiResponse<List<PlanDto>>
    suspend fun createPayment(planId: String): ApiResponse<PaymentResponseDto>

    // Local Data
    fun getLocalUser(id: Int): Flow<UserDto?>
    fun getCurrentUser(): Flow<UserDto?>
    fun getLocalSubscription(): Flow<SubscriptionDto?>
    suspend fun syncUserProfile(id: Int): ApiResponse<UserDto>
    suspend fun syncSubscription(): ApiResponse<SubscriptionDto>

    suspend fun sendOTP(phone: String): ApiResponse<SendOTPResponseDto>
    suspend fun verifyOTP(phone: String, code: String): ApiResponse<VerifyOTPResponseDto>
    suspend fun resetPassword(phone: String, resetToken: String, newPassword: String): ApiResponse<Unit>
}