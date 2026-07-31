package xyz.sattar.javid.proqueue.core.network

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.util.network.UnresolvedAddressException
import org.jetbrains.compose.resources.getString
import proqueue.composeapp.generated.resources.*

@PublishedApi
internal suspend fun getStringInternal(resource: org.jetbrains.compose.resources.StringResource): String = getString(resource)

@PublishedApi
internal val genericErrorResource = Res.string.generic_error
@PublishedApi
internal val connectionTimeoutResource = Res.string.connection_timeout
@PublishedApi
internal val noInternetResource = Res.string.no_internet
@PublishedApi
internal val serverConnectionFailedResource = Res.string.server_connection_failed
@PublishedApi
internal val sslErrorResource = Res.string.ssl_error

suspend inline fun <reified T> HttpResponse.toApiResponse(): ApiResponse<T> {
    val response = this
    return try {
        // --- Handle error status codes BEFORE deserializing body ---
        if (response.status.value >= 400) {
            val rawBody = try { response.body<String>() } catch (ex: Exception) { "" }
            return ApiResponse.Error(
                message = extractErrorMessage(rawBody, response.status.value),
                code = response.status.value
            )
        }

        val networkResponse = response.body<NetworkResponse<T>>()
        if (networkResponse.status == "success" && networkResponse.data != null) {
            ApiResponse.Success(networkResponse.data)
        } else if (networkResponse.status == "success" && T::class == Unit::class) {
            @Suppress("UNCHECKED_CAST")
            ApiResponse.Success(Unit as T)
        } else {
            ApiResponse.Error(
                message = networkResponse.message ?: getStringInternal(genericErrorResource),
                code = networkResponse.code
            )
        }
    } catch (e: ClientRequestException) {
        val rawBody = try { e.response.body<String>() } catch (ex: Exception) { "" }
        ApiResponse.Error(
            message = extractErrorMessage(rawBody, e.response.status.value),
            code = e.response.status.value
        )
    } catch (e: HttpRequestTimeoutException) {
        ApiResponse.Error(message = getStringInternal(connectionTimeoutResource), code = 408)
    } catch (e: UnresolvedAddressException) {
        ApiResponse.Error(message = getStringInternal(noInternetResource), code = 0)
    } catch (e: Exception) {
        val msg = e.message ?: ""
        val friendlyMessage = when {
            msg.contains("UnresolvedAddressException", ignoreCase = true) ||
            msg.contains("Unable to resolve host", ignoreCase = true) ->
                getStringInternal(noInternetResource)
            msg.contains("ConnectException", ignoreCase = true) ||
            msg.contains("Connection refused", ignoreCase = true) ->
                getStringInternal(serverConnectionFailedResource)
            msg.contains("SocketTimeoutException", ignoreCase = true) ||
            msg.contains("timed out", ignoreCase = true) ->
                getStringInternal(connectionTimeoutResource)
            msg.contains("SSLException", ignoreCase = true) ->
                getStringInternal(sslErrorResource)
            else -> getStringInternal(genericErrorResource)
        }
        ApiResponse.Error(message = friendlyMessage, code = 500)
    }
}

suspend inline fun <reified T> HttpResponse.toDirectApiResponse(): ApiResponse<T> {
    val response = this
    return try {
        if (response.status.value in 200..299) {
            val responseData = response.body<T>()
            ApiResponse.Success(responseData)
        } else {
            val rawBody = try { response.body<String>() } catch (ex: Exception) { "" }
            ApiResponse.Error(
                message = extractErrorMessage(rawBody, response.status.value),
                code = response.status.value
            )
        }
    } catch (e: HttpRequestTimeoutException) {
        ApiResponse.Error(message = getStringInternal(connectionTimeoutResource), code = 408)
    } catch (e: UnresolvedAddressException) {
        ApiResponse.Error(message = getStringInternal(noInternetResource), code = 0)
    } catch (e: Exception) {
        ApiResponse.Error(message = getStringInternal(genericErrorResource), code = 500)
    }
}

/**
 * Parses a raw JSON error response body and extracts a human-readable Farsi error message.
 *
 * Resolution order:
 * 1. Field-level validation errors from "data" object  (e.g. data.phone_number = ["..."])
 * 2. Top-level "message" field from the response body
 * 3. Generic Farsi message derived from the HTTP status code
 */
suspend fun extractErrorMessage(rawBody: String, statusCode: Int = 0): String {
    if (rawBody.isNotBlank()) {
        try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(rawBody)

            if (root is kotlinx.serialization.json.JsonObject) {
                val messages = mutableListOf<String>()

                // 1. Try "data" object — validation errors from DRF
                val dataEl = root["data"]
                when {
                    // data is an object with field→array/string entries
                    dataEl is kotlinx.serialization.json.JsonObject && dataEl.isNotEmpty() -> {
                        for ((_, value) in dataEl) {
                            when {
                                value is kotlinx.serialization.json.JsonArray && value.isNotEmpty() -> {
                                    val first = value[0]
                                    if (first is kotlinx.serialization.json.JsonPrimitive && first.isString) {
                                        messages.add(first.content)
                                    }
                                }
                                value is kotlinx.serialization.json.JsonPrimitive && value.isString -> {
                                    messages.add(value.content)
                                }
                            }
                        }
                    }
                    // data is a plain string
                    dataEl is kotlinx.serialization.json.JsonPrimitive && dataEl.isString -> {
                        messages.add(dataEl.content)
                    }
                    // data is an array of strings
                    dataEl is kotlinx.serialization.json.JsonArray && dataEl.isNotEmpty() -> {
                        for (el in dataEl) {
                            if (el is kotlinx.serialization.json.JsonPrimitive && el.isString) {
                                messages.add(el.content)
                            }
                        }
                    }
                }

                if (messages.isNotEmpty()) return messages.joinToString("\n")

                // 2. Try top-level "message"
                val msgEl = root["message"]
                if (msgEl is kotlinx.serialization.json.JsonPrimitive && msgEl.isString && msgEl.content.isNotBlank()) {
                    return msgEl.content
                }
            }
        } catch (ex: Exception) {
            // JSON parse failed — fall through to status-based message
        }
    }

    // 3. Fallback to HTTP status code message
    return getString(httpStatusResource(statusCode))
}

fun httpStatusResource(code: Int): org.jetbrains.compose.resources.StringResource = when (code) {
    400 -> Res.string.invalid_input
    401 -> Res.string.unauthorized
    403 -> Res.string.forbidden
    404 -> Res.string.not_found
    408 -> Res.string.connection_timeout
    409 -> Res.string.conflict_error
    422 -> Res.string.unprocessable_entity
    429 -> Res.string.too_many_requests
    in 500..599 -> Res.string.server_error
    0   -> Res.string.no_internet
    else -> Res.string.generic_error
}
