package xyz.sattar.javid.proqueue.core.network

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.util.network.UnresolvedAddressException

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
                message = extractErrorMessage(rawBody, this.status.value),
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
                message = networkResponse.message ?: httpStatusMessage(0),
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
        ApiResponse.Error(message = "اتصال به سرور قطع شد. لطفاً دوباره تلاش کنید.", code = 408)
    } catch (e: UnresolvedAddressException) {
        ApiResponse.Error(message = "به اینترنت متصل نیستید.", code = 0)
    } catch (e: Exception) {
        val msg = e.message ?: ""
        val friendlyMessage = when {
            msg.contains("UnresolvedAddressException", ignoreCase = true) ||
            msg.contains("Unable to resolve host", ignoreCase = true) ->
                "به اینترنت متصل نیستید."
            msg.contains("ConnectException", ignoreCase = true) ||
            msg.contains("Connection refused", ignoreCase = true) ->
                "اتصال به سرور برقرار نشد."
            msg.contains("SocketTimeoutException", ignoreCase = true) ||
            msg.contains("timed out", ignoreCase = true) ->
                "اتصال به سرور قطع شد. لطفاً دوباره تلاش کنید."
            msg.contains("SSLException", ignoreCase = true) ->
                "خطا در اتصال امن. لطفاً دوباره تلاش کنید."
            else -> "خطایی رخ داده است."
        }
        ApiResponse.Error(message = friendlyMessage, code = 500)
    }
}

suspend inline fun <reified T> HttpResponse.toDirectApiResponse(): ApiResponse<T> {
    return try {
        if (this.status.value in 200..299) {
            val responseData = this.body<T>()
            ApiResponse.Success(responseData)
        } else {
            val rawBody = try { this.body<String>() } catch (ex: Exception) { "" }
            ApiResponse.Error(
                message = extractErrorMessage(rawBody, this.status.value),
                code = this.status.value
            )
        }
    } catch (e: HttpRequestTimeoutException) {
        ApiResponse.Error(message = "اتصال به سرور قطع شد. لطفاً دوباره تلاش کنید.", code = 408)
    } catch (e: UnresolvedAddressException) {
        ApiResponse.Error(message = "به اینترنت متصل نیستید.", code = 0)
    } catch (e: Exception) {
        ApiResponse.Error(message = "خطایی رخ داده است.", code = 500)
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
fun extractErrorMessage(rawBody: String, statusCode: Int = 0): String {
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
    return httpStatusMessage(statusCode)
}

fun httpStatusMessage(code: Int): String = when (code) {
    400 -> "اطلاعات وارد شده معتبر نیست."
    401 -> "لطفاً دوباره وارد حساب کاربری خود شوید."
    403 -> "شما دسترسی به این بخش را ندارید."
    404 -> "اطلاعات مورد نظر یافت نشد."
    408 -> "اتصال به سرور قطع شد. لطفاً دوباره تلاش کنید."
    409 -> "تعارض داده: این اطلاعات از قبل وجود دارد."
    422 -> "اطلاعات ارسالی ناقص یا نامعتبر است."
    429 -> "تعداد درخواست‌های شما بیش از حد مجاز است. لطفاً کمی صبر کنید."
    in 500..599 -> "خطای سرور. لطفاً دوباره تلاش کنید."
    0   -> "به اینترنت متصل نیستید."
    else -> "خطایی رخ داده است."
}
