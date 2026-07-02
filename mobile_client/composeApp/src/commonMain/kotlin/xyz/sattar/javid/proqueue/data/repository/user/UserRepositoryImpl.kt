package xyz.sattar.javid.proqueue.data.repository.user

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.localDataSource.user.SubscriptionEntity
import xyz.sattar.javid.proqueue.data.localDataSource.user.UserDao
import xyz.sattar.javid.proqueue.data.localDataSource.user.UserEntity
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.CheckVersionRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.RegisterRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.RegisterResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.UserApiService
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.LoginRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SendOTPResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SubscriptionDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PlanDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.UserDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.VerifyOTPResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PaymentResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.PaymentRequestDto
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
        password: String,
        name: String
    ): ApiResponse<RegisterResponseDto> {
        val response = userApiService.register(
            RegisterRequestDto(
                phone = phone,
                password = password,
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
        userDao.clearSubscription()
        return userApiService.logout()
    }

    override suspend fun getUserProfile(id: Int): ApiResponse<UserDto> {
        return userApiService.getUserProfile(id)
    }

    override suspend fun getMySubscription(): ApiResponse<SubscriptionDto> {
        return userApiService.getMySubscription()
    }

    override suspend fun getPlans(): ApiResponse<List<PlanDto>> {
        return userApiService.getPlans()
    }

    override suspend fun createPayment(planId: String): ApiResponse<PaymentResponseDto> {
        return userApiService.createPayment(PaymentRequestDto(planId))
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

    override fun getLocalSubscription(): Flow<SubscriptionDto?> {
        return userDao.getActiveSubscription().map { entity ->
            entity?.let {
                SubscriptionDto(
                    id = it.id,
                    plan = it.planName?.let { name ->
                        PlanDto(id = 0, name = name, price = 0, priceDisplay = "", durationDisplay = "", isVip = true)
                    },
                    startedAt = it.startedAt,
                    endsAt = it.endsAt,
                    isValid = it.isValid
                )
            }
        }
    }

    override suspend fun syncUserProfile(id: Int): ApiResponse<UserDto> {
        val response = userApiService.getUserProfile(id)
        if (response is ApiResponse.Success) {
            saveUserToDb(response.data)
        }
        return response
    }

    override suspend fun syncSubscription(): ApiResponse<SubscriptionDto> {
        val response = userApiService.getMySubscription()
        if (response is ApiResponse.Success) {
            userDao.insertSubscription(
                SubscriptionEntity(
                    id = response.data.id ?: 1,
                    planName = response.data.plan?.name,
                    startedAt = response.data.startedAt,
                    endsAt = response.data.endsAt,
                    isValid = response.data.isValid ?: false
                )
            )
        }
        return response
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
