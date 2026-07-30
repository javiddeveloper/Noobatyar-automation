package xyz.sattar.javid.proqueue.core.network

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

suspend inline fun <reified T> HttpResponse.toApiResponse(): ApiResponse<T> {
    return try {
        if (this.status == HttpStatusCode.TooManyRequests) {
             val networkResponse = try {
                 this.body<NetworkResponse<T>>()
             } catch (e: Exception) {
                 null
             }
             return ApiResponse.Error(
                 message = networkResponse?.message ?: "تعداد درخواست‌های شما بیش از حد مجاز است.",
                 code = 429
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
        val errorResponseText = try {
            e.response.body<String>()
        } catch (ex: Exception) {
            ""
        }
        
        var extractedMessage: String? = null
        
        if (errorResponseText.isNotEmpty()) {
            try {
                val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val jsonObject = jsonParser.parseToJsonElement(errorResponseText).let {
                    if (it is kotlinx.serialization.json.JsonObject) it else null
                }
                
                if (jsonObject != null) {
                    val dataObj = jsonObject["data"]?.let { if (it is kotlinx.serialization.json.JsonObject) it else null }
                    if (dataObj != null && dataObj.isNotEmpty()) {
                        val messages = mutableListOf<String>()
                        for ((_, value) in dataObj) {
                            if (value is kotlinx.serialization.json.JsonArray && value.isNotEmpty()) {
                                val firstElement = value[0]
                                if (firstElement is kotlinx.serialization.json.JsonPrimitive && firstElement.isString) {
                                    messages.add(firstElement.content)
                                }
                            } else if (value is kotlinx.serialization.json.JsonPrimitive && value.isString) {
                                messages.add(value.content)
                            }
                        }
                        if (messages.isNotEmpty()) {
                            extractedMessage = messages.joinToString("\n")
                        }
                    }
                    
                    if (extractedMessage == null) {
                        val msgPrimitive = jsonObject["message"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it else null }
                        if (msgPrimitive != null && msgPrimitive.isString) {
                            extractedMessage = msgPrimitive.content
                        }
                    }
                }
            } catch (ex: Exception) {
                // Ignore parsing errors, fallback below
            }
        }
        
        if (e.response.status == HttpStatusCode.TooManyRequests) {
            ApiResponse.Error(
                message = extractedMessage ?: "تعداد درخواست‌های شما بیش از حد مجاز است.",
                code = 429
            )
        } else {
            ApiResponse.Error(
                message = extractedMessage ?: e.message ?: "Unknown Error",
                code = e.response.status.value
            )
        }
    } catch (e: Exception) {
        ApiResponse.Error(message = e.message ?: "Unknown Error", code = 500)
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
