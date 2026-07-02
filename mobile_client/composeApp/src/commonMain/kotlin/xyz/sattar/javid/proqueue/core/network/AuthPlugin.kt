package xyz.sattar.javid.proqueue.core.network
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.call.*
import io.ktor.http.*
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.TokensDto

val AuthInterceptor = createClientPlugin("AuthInterceptor") {
    onRequest { request, _ ->
        TokenManager.getAccessToken()?.let { token ->
            request.header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    onResponse { response ->
        if (response.status == HttpStatusCode.Unauthorized) {
            val refreshToken = TokenManager.getRefreshToken() ?: return@onResponse

            val refreshResponse = response.call.client.post("auth/token/refresh/") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("refresh" to refreshToken))
            }

            if (refreshResponse.status.isSuccess()) {
                val tokens = refreshResponse.body<TokensDto>()
                TokenManager.saveTokens(tokens.access, tokens.refresh)
            } else {
                TokenManager.clearTokens()
            }
        }
    }
}
