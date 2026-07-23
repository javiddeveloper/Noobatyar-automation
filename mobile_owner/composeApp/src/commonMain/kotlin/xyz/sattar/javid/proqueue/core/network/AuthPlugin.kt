package xyz.sattar.javid.proqueue.core.network
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.TokensDto

object GlobalErrorManager {
    private val _errorFlow = MutableSharedFlow<GlobalError>(extraBufferCapacity = 1)
    val errorFlow = _errorFlow.asSharedFlow()

    fun emitError(error: GlobalError) {
        _errorFlow.tryEmit(error)
    }
}

sealed class GlobalError {
    data object Unauthorized : GlobalError()
    data class RateLimit(val message: String) : GlobalError()
}

val AuthInterceptor = createClientPlugin("AuthInterceptor") {
    onRequest { request, _ ->
        TokenManager.getAccessToken()?.let { token ->
            request.header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    onResponse { response ->
        if (response.status == HttpStatusCode.Unauthorized) {
            val refreshToken = TokenManager.getRefreshToken()
            
            if (refreshToken == null) {
                TokenManager.clearTokens()
                GlobalErrorManager.emitError(GlobalError.Unauthorized)
                return@onResponse
            }

            val refreshResponse = response.call.client.post("auth/token/refresh/") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("refresh" to refreshToken))
            }

            if (refreshResponse.status.isSuccess()) {
                val tokens = refreshResponse.body<TokensDto>()
                TokenManager.saveTokens(tokens.access, tokens.refresh)
            } else {
                TokenManager.clearTokens()
                GlobalErrorManager.emitError(GlobalError.Unauthorized)
            }
        } else if (response.status == HttpStatusCode.TooManyRequests) {
            // Try to parse the message from the body if possible
            val message = try {
                 val networkResponse = response.body<NetworkResponse<Unit>>()
                 networkResponse.message ?: "تعداد درخواست‌های شما بیش از حد مجاز است."
            } catch (e: Exception) {
                "تعداد درخواست‌های شما بیش از حد مجاز است."
            }
            GlobalErrorManager.emitError(GlobalError.RateLimit(message))
        }
    }
}
