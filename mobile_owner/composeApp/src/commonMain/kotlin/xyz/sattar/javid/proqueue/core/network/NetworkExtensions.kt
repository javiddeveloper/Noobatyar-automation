package xyz.sattar.javid.proqueue.core.network

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

suspend inline fun <reified T> HttpResponse.toApiResponse(): ApiResponse<T> {
    return try {
        // --- Handle error status codes BEFORE deserializing body ---
        // Ktor may or may not throw ClientRequestException depending on expectSuccess config.
        // We handle it here explicitly so error bodies (which may have field types that don't
        // match the success schema, e.g. data.phone_number = ["error msg"] instead of a String)
        // are never deserialized into NetworkResponse<T>.
        if (this.status.value >= 400) {
            val rawBody = try { this.body<String>() } catch (ex: Exception) { "" }
            return ApiResponse.Error(
                message = extractErrorMessage(rawBody),
                code = this.status.value
            )
        }

        val networkResponse = this.body<NetworkResponse<T>>()
        if (networkResponse.status == "success" && networkResponse.data != null) {
            ApiResponse.Success(networkResponse.data)
        } else if (networkResponse.status == "success" && T::class == Unit::class) {
            ApiResponse.Success(Unit as T)
        } else {
            ApiResponse.Error(
                    message = networkResponse.message ?: "Unknown Error",
                    code = networkResponse.code
            )
        }
    } catch (e: ClientRequestException) {
        val rawBody = try { e.response.body<String>() } catch (ex: Exception) { "" }
        ApiResponse.Error(
            message = extractErrorMessage(rawBody),
            code = e.response.status.value
        )
    } catch (e: Exception) {
        ApiResponse.Error(message = e.message ?: "Unknown Error", code = 500)
    }
}

/**
 * Parses a raw JSON error response body and extracts a human-readable error message.
 *
 * Tries in order:
 * 1. Values inside "data" object (validation errors — may be strings or string arrays)
 * 2. Top-level "message" field
 * 3. Fallback generic message
 */
fun extractErrorMessage(rawBody: String): String {
    if (rawBody.isBlank()) return "خطایی رخ داده است."
    return try {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val root = json.parseToJsonElement(rawBody)
        if (root !is kotlinx.serialization.json.JsonObject) return "خطایی رخ داده است."

        val messages = mutableListOf<String>()

        val dataEl = root["data"]
        if (dataEl is kotlinx.serialization.json.JsonObject && dataEl.isNotEmpty()) {
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

        if (messages.isNotEmpty()) return messages.joinToString("\n")

        val msgEl = root["message"]
        if (msgEl is kotlinx.serialization.json.JsonPrimitive && msgEl.isString) {
            return msgEl.content
        }

        "خطایی رخ داده است."
    } catch (ex: Exception) {
        "خطایی رخ داده است."
    }
}



suspend inline fun <reified T> HttpResponse.toDirectApiResponse(): ApiResponse<T> {
    return try {
        if (this.status.value in 200..299) {
            val responseData = this.body<T>()
            ApiResponse.Success(responseData)
        } else {
            ApiResponse.Error(
                    message = "HTTP Error: ${this.status.value}",
                    code = this.status.value
            )
        }
    } catch (e: Exception) {
        ApiResponse.Error(message = e.message ?: "Unknown Error", code = 500)
    }
}
