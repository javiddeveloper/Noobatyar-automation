package xyz.sattar.javid.proqueue.data.remoteDataSource.user

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.toApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.CheckVersionRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.LoginRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.RegisterRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.RegisterResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SendOTPResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.UserDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.VerifyOTPResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SubscriptionDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PlanDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PaymentResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.PaymentRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.ResetPasswordRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.SendOTPRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.VerifyOTPRequestDto
import xyz.sattar.javid.proqueue.domain.model.VersionInfo

class UserApiService(private val httpClient: HttpClient) {

    suspend fun checkVersion(body: CheckVersionRequestDto): ApiResponse<VersionInfo> {
        return httpClient.post("version/check/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }

    suspend fun register(
        body: RegisterRequestDto
    ): ApiResponse<RegisterResponseDto> {
        return httpClient.post("auth/register/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }
    suspend fun login(
        body: LoginRequestDto
    ): ApiResponse<RegisterResponseDto> {
        return httpClient.post("auth/login/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }
    suspend fun logout(): ApiResponse<Unit> {
        return httpClient.post("auth/logout/") {
            contentType(ContentType.Application.Json)
        }.toApiResponse()
    }

    suspend fun getUserProfile(id: Int): ApiResponse<UserDto> {
        return httpClient.get("users/$id/") {
            contentType(ContentType.Application.Json)
        }.toApiResponse()
    }

    suspend fun getMySubscription(): ApiResponse<SubscriptionDto> {
        return httpClient.get("accounting/my-subscription/") {
            contentType(ContentType.Application.Json)
        }.toApiResponse()
    }

    suspend fun getPlans(): ApiResponse<List<PlanDto>> {
        return httpClient.get("accounting/plans/") {
            contentType(ContentType.Application.Json)
        }.toApiResponse()
    }

    suspend fun createPayment(body: PaymentRequestDto): ApiResponse<PaymentResponseDto> {
        return httpClient.post("accounting/plans/payment/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }

    suspend fun sendOTP(
        body: SendOTPRequestDto
    ): ApiResponse<SendOTPResponseDto> {
        return httpClient.post("auth/forgot-password/send/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }
    suspend fun verifyOTP(
        body: VerifyOTPRequestDto
    ): ApiResponse<VerifyOTPResponseDto> {
        return httpClient.post("auth/forgot-password/verify/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }

    suspend fun resetPassword(
        body: ResetPasswordRequestDto
    ): ApiResponse<Unit> {
        return httpClient.post("auth/forgot-password/reset/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }
}
