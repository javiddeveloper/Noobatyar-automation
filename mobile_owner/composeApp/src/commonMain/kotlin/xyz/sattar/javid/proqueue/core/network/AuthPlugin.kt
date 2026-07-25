package xyz.sattar.javid.proqueue.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.http.*
import io.ktor.util.AttributeKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
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

/** Marks the refresh call itself so it is not intercepted (would recurse). */
private val SkipAuthKey = AttributeKey<Boolean>("SkipAuth")

/**
 * Endpoints that must be called *without* a bearer token.
 *
 * DRF runs authentication before permissions, so `JWTAuthentication` rejects a
 * stale/expired token with 401 even on `AllowAny` views. Sending the old token
 * to e.g. `auth/login/` therefore made it impossible to sign back in once the
 * token expired. (`auth/logout/` is deliberately absent — it is IsAuthenticated.)
 */
private val PUBLIC_PATHS = listOf(
    "auth/register/",
    "auth/login/",
    "auth/otp/send/",
    "auth/otp/verify/",
    "auth/token/refresh/",
    "auth/forgot-password/send/",
    "auth/forgot-password/verify/",
    "auth/forgot-password/reset/"
)

private fun HttpRequestBuilder.isPublicEndpoint(): Boolean {
    val path = url.encodedPath
    return PUBLIC_PATHS.any { path.endsWith(it) }
}

@Serializable
private data class RefreshRequestBody(val refresh: String)

/**
 * Serializes refreshes across concurrent requests. Without this, the several
 * calls a screen fires in parallel each 401 at once and each starts its own
 * refresh; because the backend runs SimpleJWT with ROTATE_REFRESH_TOKENS +
 * BLACKLIST_AFTER_ROTATION, the first refresh invalidates the token the others
 * are still holding, they all fail, and the tokens get wiped — after which
 * every request goes out with no Authorization header and 401s.
 */
private val refreshMutex = Mutex()

/**
 * Refreshes the token set at most once per "generation".
 *
 * Callers pass the access token they saw fail. Whoever wins the mutex performs
 * the network refresh; everyone queued behind it observes that the stored token
 * already changed and reuses it instead of burning the (now blacklisted)
 * refresh token. Returns a usable access token, or null if the session is
 * genuinely over.
 */
private suspend fun refreshAccessToken(client: HttpClient, staleToken: String?): String? =
    refreshMutex.withLock {
        // Someone else already refreshed while we waited for the lock.
        val current = TokenManager.getAccessToken()
        if (current != null && current != staleToken) return@withLock current

        val refreshToken = TokenManager.getRefreshToken() ?: return@withLock null

        val response = try {
            client.post("auth/token/refresh/") {
                attributes.put(SkipAuthKey, true)
                contentType(ContentType.Application.Json)
                setBody(RefreshRequestBody(refreshToken))
            }
        } catch (e: Exception) {
            return@withLock null
        }

        if (!response.status.isSuccess()) return@withLock null

        val tokens = try {
            response.body<TokensDto>()
        } catch (e: Exception) {
            return@withLock null
        }

        TokenManager.saveTokens(tokens.access, tokens.refresh)
        tokens.access
    }

/**
 * Attaches the bearer token, and on a 401 refreshes the session and **retries
 * the original request** before handing the response back to the caller.
 * Previously the retry was missing, so every call that happened to hit an
 * expired access token surfaced as a 401 to the UI even though the refresh had
 * just succeeded.
 */
val AuthInterceptor = createClientPlugin("AuthInterceptor") {
    on(Send) { request ->
        // The refresh call, and every unauthenticated endpoint, must bypass this
        // interceptor entirely — sending a stale token there causes a 401.
        if (request.attributes.contains(SkipAuthKey) || request.isPublicEndpoint()) {
            return@on proceed(request)
        }

        val tokenUsed = TokenManager.getAccessToken()
        if (tokenUsed != null) {
            request.headers[HttpHeaders.Authorization] = "Bearer $tokenUsed"
        }

        var call = proceed(request)

        if (call.response.status == HttpStatusCode.Unauthorized) {
            // A one-shot multipart body cannot be sent a second time, so refresh
            // the session for subsequent calls but don't attempt the retry.
            val retryable = request.body !is MultiPartFormDataContent
            val newToken = refreshAccessToken(call.client, tokenUsed)

            if (newToken == null) {
                // Refresh token missing, expired or rejected — the session is over.
                TokenManager.clearTokens()
                GlobalErrorManager.emitError(GlobalError.Unauthorized)
            } else if (retryable) {
                request.headers[HttpHeaders.Authorization] = "Bearer $newToken"
                call = proceed(request)

                // Still rejected even with a freshly refreshed token: the session
                // really is dead, so sign out instead of looping on 401s.
                if (call.response.status == HttpStatusCode.Unauthorized) {
                    TokenManager.clearTokens()
                    GlobalErrorManager.emitError(GlobalError.Unauthorized)
                }
            }
        }

        if (call.response.status == HttpStatusCode.TooManyRequests) {
            val message = try {
                call.response.body<NetworkResponse<Unit>>().message
                    ?: "تعداد درخواست‌های شما بیش از حد مجاز است."
            } catch (e: Exception) {
                "تعداد درخواست‌های شما بیش از حد مجاز است."
            }
            GlobalErrorManager.emitError(GlobalError.RateLimit(message))
        }

        call
    }
}
